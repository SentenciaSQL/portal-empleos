package com.empleord.ads;

import com.empleord.audit.AuditService;
import com.empleord.common.ApiException;
import com.empleord.config.AppProperties;
import com.empleord.domain.entity.AdSettings;
import com.empleord.domain.entity.AdSlot;
import com.empleord.domain.repository.AdSettingsRepository;
import com.empleord.domain.repository.AdSlotRepository;
import com.empleord.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class AdService {

    private static final short SETTINGS_ID = 1;

    private final AdSettingsRepository settings;
    private final AdSlotRepository slots;
    private final AppProperties properties;
    private final AuditService audit;

    public AdService(AdSettingsRepository settings, AdSlotRepository slots, AppProperties properties, AuditService audit) {
        this.settings = settings;
        this.slots = slots;
        this.properties = properties;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PublicConfig publicConfig() {
        AdSettings stored = loadSettings();
        String publisherId = effectivePublisher(stored);
        boolean serving = serving(stored, publisherId);
        List<PublicSlot> visible = serving
                ? slots.findAllByOrderBySlotKeyAsc().stream()
                .map(slot -> new PublicSlot(slot.getSlotKey(), slot.getPageKey(), slot.isEnabled(), effectiveUnit(slot)))
                .filter(slot -> slot.enabled() && AdIds.unit(slot.adUnitId()))
                .toList()
                : List.of();
        return new PublicConfig(serving, serving ? publisherId : null, true, visible);
    }

    @Transactional
    public void recordImpression(String key) {
        if (key == null || !key.matches("[a-z0-9-]{1,40}")) {
            return;
        }
        AdSlot slot = slots.findById(key).orElse(null);
        if (slot == null || !slot.isEnabled() || !AdIds.unit(effectiveUnit(slot))) {
            return;
        }
        AdSettings stored = loadSettings();
        if (!serving(stored, effectivePublisher(stored))) {
            return;
        }
        slots.incrementImpressions(key, Instant.now());
    }

    @Transactional(readOnly = true)
    public AdminView adminView() {
        AdSettings stored = loadSettings();
        String publisherId = effectivePublisher(stored);
        boolean envPublisher = AdIds.publisher(properties.getAdsense().getPublisherId());
        List<AdminSlot> items = slots.findAllByOrderBySlotKeyAsc().stream().map(slot -> {
            String envUnit = envUnit(slot.getSlotKey());
            String effective = effectiveUnit(slot);
            return new AdminSlot(slot.getSlotKey(), slot.getPageKey(), slot.isEnabled(), slot.getAdUnitId(),
                    AdIds.unit(envUnit), effective, slot.getImpressions(), slot.isEnabled() && AdIds.unit(effective));
        }).toList();
        return new AdminView(properties.getAdsense().isEnabled(), envPublisher, stored.isEnabled(), stored.getPublisherId(),
                publisherId, serving(stored, publisherId), items);
    }

    @Transactional
    public AdminView update(UpdateRequest request, String ip) {
        if (request == null || request.slots() == null) {
            throw ApiException.badRequest("Revisa los datos del formulario.");
        }
        String publisherId = AdIds.normalizePublisher(request.publisherId());
        if (!publisherId.isEmpty() && !AdIds.publisher(publisherId)) {
            throw ApiException.badRequest("El identificador de editor no es válido.");
        }
        AdSettings stored = loadSettings();
        stored.setEnabled(request.enabled());
        if (!AdIds.publisher(properties.getAdsense().getPublisherId())) {
            stored.setPublisherId(publisherId);
        }
        stored.setUpdatedAt(Instant.now());
        Map<String, AdSlot> current = slots.findAllByOrderBySlotKeyAsc().stream()
                .collect(java.util.stream.Collectors.toMap(AdSlot::getSlotKey, slot -> slot));
        for (SlotUpdate update : request.slots()) {
            AdSlot slot = current.get(update.key());
            if (slot == null) {
                continue;
            }
            String unit = AdIds.normalizeUnit(update.adUnitId());
            if (!unit.isEmpty() && !AdIds.unit(unit)) {
                throw ApiException.badRequest("El identificador del bloque no es válido.");
            }
            slot.setEnabled(update.enabled());
            if (!AdIds.unit(envUnit(slot.getSlotKey()))) {
                slot.setAdUnitId(unit);
            }
            slot.setUpdatedAt(Instant.now());
        }
        audit.record(SecurityUtils.requireUser().getId(), "UPDATE_ADS", "AdSettings", "1",
                request.enabled() ? "enabled" : "disabled", ip);
        return adminView();
    }

    @Transactional(readOnly = true)
    public String adsTxt() {
        AdSettings stored = loadSettings();
        String publisherId = effectivePublisher(stored);
        if (!serving(stored, publisherId)) {
            return "# EmpleoRD no tiene un Publisher ID de AdSense configurado.\n";
        }
        return "google.com, " + AdIds.adsTxtPublisher(publisherId) + ", DIRECT, f08c47fec0942fa0\n";
    }

    private boolean serving(AdSettings stored, String publisherId) {
        return properties.getAdsense().isEnabled() && stored.isEnabled() && AdIds.publisher(publisherId);
    }

    private String effectivePublisher(AdSettings stored) {
        String fromEnv = AdIds.normalizePublisher(properties.getAdsense().getPublisherId());
        if (AdIds.publisher(fromEnv)) {
            return fromEnv;
        }
        String storedId = AdIds.normalizePublisher(stored.getPublisherId());
        return AdIds.publisher(storedId) ? storedId : "";
    }

    private String effectiveUnit(AdSlot slot) {
        String fromEnv = envUnit(slot.getSlotKey());
        if (AdIds.unit(fromEnv)) {
            return fromEnv;
        }
        String stored = AdIds.normalizeUnit(slot.getAdUnitId());
        return AdIds.unit(stored) ? stored : "";
    }

    private String envUnit(String key) {
        AppProperties.Adsense.Slots configured = properties.getAdsense().getSlots();
        String value = switch (key) {
            case "home-top" -> configured.getHomeTop();
            case "home-feed" -> configured.getHomeFeed();
            case "job-search-inline" -> configured.getJobSearchInline();
            case "job-detail-bottom" -> configured.getJobDetailBottom();
            case "category-content" -> configured.getCategoryContent();
            case "info-content" -> configured.getInfoContent();
            case "resource-content" -> configured.getResourceContent();
            default -> "";
        };
        return AdIds.normalizeUnit(value);
    }

    private AdSettings loadSettings() {
        return settings.findById(SETTINGS_ID).orElseThrow(() -> ApiException.notFound("La configuración de publicidad no existe."));
    }

    public record PublicConfig(boolean enabled, String publisherId, boolean consentRequired, List<PublicSlot> slots) {
    }

    public record PublicSlot(String key, String page, boolean enabled, String adUnitId) {
    }

    public record AdminView(boolean environmentEnabled, boolean publisherLocked, boolean enabled, String publisherId,
                            String effectivePublisherId, boolean serving, List<AdminSlot> slots) {
    }

    public record AdminSlot(String key, String page, boolean enabled, String adUnitId, boolean unitLocked,
                            String effectiveUnitId, long portalViews, boolean served) {
    }

    public record UpdateRequest(boolean enabled, String publisherId, List<SlotUpdate> slots) {
    }

    public record SlotUpdate(String key, boolean enabled, String adUnitId) {
    }

    public record ImpressionRequest(String key) {
    }
}
