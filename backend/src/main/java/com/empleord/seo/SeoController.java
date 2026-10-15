package com.empleord.seo;

import com.empleord.common.ApiException;
import com.empleord.common.HtmlSanitizer;
import com.empleord.config.AppProperties;
import com.empleord.domain.entity.Company;
import com.empleord.domain.entity.Job;
import com.empleord.domain.enums.CompanyStatus;
import com.empleord.domain.enums.JobStatus;
import com.empleord.domain.repository.CompanyRepository;
import com.empleord.domain.repository.JobCategoryRepository;
import com.empleord.domain.repository.JobRepository;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
public class SeoController {

    private final JobRepository jobs;
    private final CompanyRepository companies;
    private final JobCategoryRepository categories;
    private final AppProperties properties;

    public SeoController(JobRepository jobs, CompanyRepository companies, JobCategoryRepository categories, AppProperties properties) {
        this.jobs = jobs;
        this.companies = companies;
        this.categories = categories;
        this.properties = properties;
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    @Transactional(readOnly = true)
    public String sitemap() {
        String base = properties.getPublicUrl();
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">");
        url(xml, base + "/");
        url(xml, base + "/empleos");
        categories.findAll().forEach(category -> url(xml, base + "/empleos?categoryId=" + category.getId()));
        jobs.findOpenSlugs(Instant.now()).forEach(slug -> url(xml, base + "/empleos/" + slug));
        companies.findAll().stream().filter(company -> company.getDeletedAt() == null && company.getStatus() == CompanyStatus.ACTIVE)
                .forEach(company -> url(xml, base + "/empresas/" + company.getSlug()));
        xml.append("</urlset>");
        return xml.toString();
    }

    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public String robots() {
        return """
                User-agent: *
                Allow: /
                Disallow: /candidato
                Disallow: /empresa
                Disallow: /admin
                Disallow: /entrar
                Disallow: /registro

                Sitemap: %s/sitemap.xml
                """.formatted(properties.getPublicUrl());
    }

    @GetMapping(value = "/public/share/jobs/{slug}", produces = MediaType.TEXT_HTML_VALUE)
    @Transactional(readOnly = true)
    public String shareJob(@PathVariable String slug) {
        Job job = jobs.findBySlugAndDeletedAtIsNull(slug).orElseThrow(() -> ApiException.notFound("La oferta no existe."));
        if (job.getStatus() == JobStatus.DRAFT || job.getStatus() == JobStatus.REJECTED) {
            throw ApiException.notFound("La oferta no existe.");
        }
        String url = properties.getPublicUrl() + "/empleos/" + job.getSlug();
        String description = shorten(job.getDescription());
        boolean open = job.isOpen(Instant.now());
        String jsonLd = open ? jobPosting(job, description) : "";
        return page(job.getTitle() + " en " + job.getCompany().getName(), description, url, open, jsonLd, """
                <p class="meta">%s · %s</p>
                <h1>%s</h1>
                <p>%s</p>
                <p>%s</p>
                <p><a href="%s">Ver oferta en EmpleoRD</a></p>
                """.formatted(
                escape(location(job)),
                escape(job.getWorkMode().name()),
                escape(job.getTitle()),
                escape(job.getCompany().getName()),
                escape(description),
                escape(url)));
    }

    @GetMapping(value = "/public/share/companies/{slug}", produces = MediaType.TEXT_HTML_VALUE)
    @Transactional(readOnly = true)
    public String shareCompany(@PathVariable String slug) {
        Company company = companies.findBySlugAndDeletedAtIsNull(slug).orElseThrow(() -> ApiException.notFound("La empresa no existe."));
        String url = properties.getPublicUrl() + "/empresas/" + company.getSlug();
        String description = shorten(company.getDescription() == null ? company.getName() : company.getDescription());
        return page(company.getName(), description, url, true, "", """
                <h1>%s</h1>
                <p>%s</p>
                <p><a href="%s">Ver empresa en EmpleoRD</a></p>
                """.formatted(escape(company.getName()), escape(description), escape(url)));
    }

    private String jobPosting(Job job, String description) {
        String date = job.getPublishedAt() == null ? "" : DateTimeFormatter.ISO_LOCAL_DATE.format(job.getPublishedAt().atOffset(ZoneOffset.UTC));
        if (date.isBlank() || description.isBlank()) {
            return "";
        }
        StringBuilder json = new StringBuilder();
        json.append("<script type=\"application/ld+json\">");
        json.append("{\"@context\":\"https://schema.org\",\"@type\":\"JobPosting\",");
        json.append("\"title\":").append(quote(job.getTitle())).append(',');
        json.append("\"description\":").append(quote(description)).append(',');
        json.append("\"datePosted\":").append(quote(date)).append(',');
        json.append("\"hiringOrganization\":{\"@type\":\"Organization\",\"name\":").append(quote(job.getCompany().getName())).append("},");
        if (job.getExpiresAt() != null) {
            json.append("\"validThrough\":").append(quote(DateTimeFormatter.ISO_INSTANT.format(job.getExpiresAt()))).append(',');
        }
        if (job.getWorkMode().name().equals("REMOTE")) {
            json.append("\"jobLocationType\":\"TELECOMMUTE\",");
        }
        if (job.getProvince() != null) {
            json.append("\"jobLocation\":{\"@type\":\"Place\",\"address\":{\"@type\":\"PostalAddress\",\"addressLocality\":")
                    .append(quote(job.getMunicipality() == null ? job.getProvince().getName() : job.getMunicipality().getName()))
                    .append(",\"addressRegion\":").append(quote(job.getProvince().getName()))
                    .append(",\"addressCountry\":").append(quote(job.getCountry().getCode())).append("}},");
        }
        if (job.getSalaryMin() != null && job.getSalaryPeriod() != null) {
            json.append("\"baseSalary\":{\"@type\":\"MonetaryAmount\",\"currency\":").append(quote(job.getSalaryCurrency()))
                    .append(",\"value\":{\"@type\":\"QuantitativeValue\",\"minValue\":").append(job.getSalaryMin());
            if (job.getSalaryMax() != null) {
                json.append(",\"maxValue\":").append(job.getSalaryMax());
            }
            json.append(",\"unitText\":").append(quote(salaryUnit(job.getSalaryPeriod().name()))).append("}},");
        }
        if (json.charAt(json.length() - 1) == ',') {
            json.deleteCharAt(json.length() - 1);
        }
        json.append("}</script>");
        return json.toString();
    }

    private String salaryUnit(String period) {
        return switch (period) {
            case "HOUR" -> "HOUR";
            case "DAY" -> "DAY";
            case "WEEK", "BIWEEKLY" -> "WEEK";
            case "YEAR" -> "YEAR";
            default -> "MONTH";
        };
    }

    private String location(Job job) {
        if (job.getLocationLabel() != null) {
            return job.getLocationLabel();
        }
        if (job.getMunicipality() != null) {
            return job.getMunicipality().getName() + ", " + job.getProvince().getName();
        }
        if (job.getProvince() != null) {
            return job.getProvince().getName();
        }
        return job.getCountry().getName();
    }

    private void url(StringBuilder xml, String loc) {
        xml.append("<url><loc>").append(escape(loc)).append("</loc></url>");
    }

    private String page(String title, String description, String url, boolean index, String jsonLd, String body) {
        return """
                <!doctype html><html lang="es"><head><meta charset="utf-8">
                <title>%s</title><meta name="description" content="%s">
                <link rel="canonical" href="%s">
                <meta name="robots" content="%s">
                <meta property="og:title" content="%s"><meta property="og:description" content="%s">
                <meta property="og:url" content="%s"><meta property="og:type" content="website">
                %s</head><body style="font-family:Georgia,serif;max-width:680px;margin:40px auto;color:#142033">%s</body></html>
                """.formatted(escape(title), escape(description), escape(url), index ? "index,follow" : "noindex,follow",
                escape(title), escape(description), escape(url), jsonLd, body);
    }

    private String shorten(String value) {
        String clean = HtmlSanitizer.plain(value);
        return clean.length() > 220 ? clean.substring(0, 217) + "..." : clean;
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private String quote(String value) {
        String safe = value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", " ").replace("\n", " ").replace("<", "\\u003c");
        return "\"" + safe + "\"";
    }
}
