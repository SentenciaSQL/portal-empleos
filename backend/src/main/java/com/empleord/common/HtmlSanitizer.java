package com.empleord.common;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;

import java.net.URI;
import java.util.Locale;

/**
 * Allowlist for the job description, responsibilities and requirements.
 * Other fields keep using {@link Text#clean(String)}, which removes every tag.
 */
public final class HtmlSanitizer {

    private static final Safelist SAFELIST = Safelist.none()
            .addTags("p", "br", "strong", "b", "em", "i", "u", "ul", "ol", "li", "a")
            .addAttributes("a", "href")
            .addProtocols("a", "href", "http", "https", "mailto");

    private HtmlSanitizer() {
    }

    public static String clean(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String source = value.strip().startsWith("<") ? value : plainToHtml(value);
        Document.OutputSettings settings = new Document.OutputSettings().prettyPrint(false);
        String cleaned = Jsoup.clean(source, "", SAFELIST, settings);
        Document document = Jsoup.parseBodyFragment(cleaned);
        document.outputSettings(settings);
        for (Element anchor : document.select("a")) {
            String href = anchor.attr("href").trim();
            if (!safeHref(href)) {
                anchor.unwrap();
                continue;
            }
            anchor.attr("href", href);
            anchor.attr("rel", "noopener noreferrer");
            anchor.attr("target", "_blank");
        }
        String html = document.body().html().trim();
        return plain(html).isBlank() ? null : html;
    }

    public static String required(String value, String label) {
        String cleaned = clean(value);
        if (cleaned == null) {
            throw ApiException.badRequest(label + " es obligatorio.");
        }
        return cleaned;
    }

    public static String plain(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String text = value.strip().startsWith("<") ? Jsoup.parse(value).text() : value;
        return text.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    private static String plainToHtml(String value) {
        String normalized = value.replace("\r\n", "\n").replace('\r', '\n').strip();
        String[] blocks = normalized.split("\n{2,}");
        StringBuilder html = new StringBuilder();
        for (String block : blocks) {
            if (block.isBlank()) {
                continue;
            }
            html.append("<p>");
            String[] lines = block.split("\n", -1);
            for (int index = 0; index < lines.length; index++) {
                if (index > 0) {
                    html.append("<br>");
                }
                html.append(escape(lines[index]));
            }
            html.append("</p>");
        }
        return html.toString();
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static boolean safeHref(String href) {
        if (href.isEmpty()) {
            return false;
        }
        try {
            URI uri = URI.create(href);
            String scheme = uri.getScheme();
            if (scheme == null) {
                return false;
            }
            String normalized = scheme.toLowerCase(Locale.ROOT);
            if (normalized.equals("http") || normalized.equals("https")) {
                return uri.getHost() != null && !uri.getHost().isBlank();
            }
            if (normalized.equals("mailto")) {
                String specific = uri.getSchemeSpecificPart();
                return specific != null && !specific.isBlank();
            }
            return false;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
