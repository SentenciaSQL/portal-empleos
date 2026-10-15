package com.empleord.ads;

import com.empleord.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdController {

    private final AdService ads;

    public AdController(AdService ads) {
        this.ads = ads;
    }

    @GetMapping("/api/ads/config")
    public ApiResponse<AdService.PublicConfig> config() {
        return ApiResponse.ok(ads.publicConfig());
    }

    @PostMapping("/api/ads/impressions")
    public ApiResponse<Void> impression(@RequestBody AdService.ImpressionRequest request) {
        ads.recordImpression(request == null ? null : request.key());
        return ApiResponse.ok(null);
    }

    @GetMapping(value = "/ads.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public String adsTxt() {
        return ads.adsTxt();
    }

    @GetMapping("/api/admin/ads")
    public ApiResponse<AdService.AdminView> adminView() {
        return ApiResponse.ok(ads.adminView());
    }

    @PutMapping("/api/admin/ads")
    public ApiResponse<AdService.AdminView> update(@RequestBody AdService.UpdateRequest request, HttpServletRequest http) {
        return ApiResponse.ok(ads.update(request, http.getRemoteAddr()));
    }
}
