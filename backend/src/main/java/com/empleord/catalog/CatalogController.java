package com.empleord.catalog;

import com.empleord.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/catalog")
public class CatalogController {

    private final CatalogService catalog;

    public CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/countries")
    public ApiResponse<List<CatalogDtos.CountryItem>> countries() {
        return ApiResponse.ok(catalog.countries());
    }

    @GetMapping("/provinces")
    public ApiResponse<List<CatalogDtos.ProvinceItem>> provinces() {
        return ApiResponse.ok(catalog.provinces());
    }

    @GetMapping("/provinces/{id}/municipalities")
    public ApiResponse<List<CatalogDtos.MunicipalityItem>> municipalities(@PathVariable Integer id) {
        return ApiResponse.ok(catalog.municipalities(id));
    }

    @GetMapping("/categories")
    public ApiResponse<List<CatalogDtos.CategoryItem>> categories() {
        return ApiResponse.ok(catalog.categories());
    }

    @GetMapping("/skills")
    public ApiResponse<List<CatalogDtos.SkillItem>> skills(@RequestParam(required = false) String q) {
        return ApiResponse.ok(catalog.skills(q));
    }

    @GetMapping("/plans")
    public ApiResponse<List<CatalogDtos.PlanItem>> plans() {
        return ApiResponse.ok(catalog.plans());
    }
}
