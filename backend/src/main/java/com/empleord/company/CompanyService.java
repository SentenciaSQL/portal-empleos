package com.empleord.company;

import com.empleord.auth.AuthService;
import com.empleord.billing.PlanService;
import com.empleord.catalog.CatalogService;
import com.empleord.common.ApiException;
import com.empleord.common.Text;
import com.empleord.domain.entity.Company;
import com.empleord.domain.entity.CompanyMember;
import com.empleord.domain.entity.EmployerProfile;
import com.empleord.domain.entity.Job;
import com.empleord.domain.entity.SubscriptionPlan;
import com.empleord.domain.entity.User;
import com.empleord.domain.enums.CompanyMemberRole;
import com.empleord.domain.enums.CompanyStatus;
import com.empleord.domain.enums.JobStatus;
import com.empleord.domain.enums.VerificationStatus;
import com.empleord.domain.repository.CompanyMemberRepository;
import com.empleord.domain.repository.CompanyRepository;
import com.empleord.domain.repository.EmployerProfileRepository;
import com.empleord.domain.repository.JobApplicationRepository;
import com.empleord.domain.repository.JobRepository;
import com.empleord.security.SecurityUtils;
import com.empleord.storage.FileRules;
import com.empleord.storage.StorageService;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CompanyService {

    private final CompanyRepository companies;
    private final CompanyMemberRepository members;
    private final EmployerProfileRepository employers;
    private final JobRepository jobs;
    private final JobApplicationRepository applications;
    private final CompanyAccess access;
    private final CatalogService catalog;
    private final PlanService plans;
    private final AuthService auth;
    private final StorageService storage;

    public CompanyService(CompanyRepository companies, CompanyMemberRepository members, EmployerProfileRepository employers, JobRepository jobs,
                          JobApplicationRepository applications, CompanyAccess access, CatalogService catalog, PlanService plans, AuthService auth,
                          StorageService storage) {
        this.companies = companies;
        this.members = members;
        this.employers = employers;
        this.jobs = jobs;
        this.applications = applications;
        this.access = access;
        this.catalog = catalog;
        this.plans = plans;
        this.auth = auth;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    public CompanyDtos.PublicCompany publicProfile(String slug) {
        Company company = companies.findBySlugAndDeletedAtIsNull(slug).orElseThrow(() -> ApiException.notFound("La empresa no existe."));
        if (company.getStatus() != CompanyStatus.ACTIVE) {
            throw ApiException.notFound("La empresa no existe.");
        }
        return publicView(company);
    }

    @Transactional(readOnly = true)
    public CompanyDtos.ManagedCompany mine() {
        CompanyMember member = access.requireMember();
        SubscriptionPlan plan = plans.current(member.getCompany());
        EmployerProfile employer = employers.findById(member.getUser().getId()).orElse(null);
        return new CompanyDtos.ManagedCompany(publicView(member.getCompany()), member.getMemberRole().name(),
                employer == null ? null : employer.getJobTitle(), plan.getCode(), plan.getName(), plan.getMaxActiveJobs(),
                plan.isCandidateFilters(), plan.isAdvancedStats());
    }

    @Transactional
    public CompanyDtos.ManagedCompany update(CompanyDtos.ProfileRequest request) {
        CompanyMember member = access.requireOwner();
        Company company = member.getCompany();
        company.setName(Text.required(request.name(), "El nombre"));
        company.setDescription(Text.clean(request.description()));
        company.setWebsite(Text.url(request.website()));
        company.setIndustry(Text.clean(request.industry()));
        var province = catalog.province(request.provinceId());
        company.setProvince(province);
        company.setMunicipality(catalog.municipality(request.municipalityId(), province));
        company.setCountry(catalog.country(request.countryCode()));
        EmployerProfile employer = employers.findById(member.getUser().getId()).orElseThrow();
        employer.setJobTitle(Text.clean(request.recruiterTitle()));
        employer.setPhone(Text.clean(request.phone()));
        return mine();
    }

    @Transactional
    public void uploadLogo(MultipartFile file) {
        Company company = access.requireOwner().getCompany();
        byte[] bytes = FileRules.image(file);
        storage.delete(company.getLogoStorageKey());
        StorageService.StoredFile stored = storage.store("logos", file.getOriginalFilename(), bytes);
        company.setLogoStorageKey(stored.key());
    }

    @Transactional(readOnly = true)
    public ResponseEntity<Resource> logo(UUID companyId) {
        Company company = companies.findByIdAndDeletedAtIsNull(companyId).orElseThrow(() -> ApiException.notFound("La empresa no existe."));
        if (company.getLogoStorageKey() == null) {
            throw ApiException.notFound("La empresa no tiene logotipo.");
        }
        String type = company.getLogoStorageKey().endsWith(".png") || company.getLogoStorageKey().contains(".png")
                ? MediaType.IMAGE_PNG_VALUE
                : company.getLogoStorageKey().contains("webp") ? "image/webp" : MediaType.IMAGE_JPEG_VALUE;
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .header(HttpHeaders.CONTENT_TYPE, type)
                .body(storage.load(company.getLogoStorageKey()));
    }

    @Transactional(readOnly = true)
    public List<CompanyDtos.MemberItem> members() {
        Company company = access.requireMember().getCompany();
        return members.findByCompanyId(company.getId()).stream()
                .map(member -> new CompanyDtos.MemberItem(member.getUser().getId(), member.getUser().fullName(), member.getUser().getEmail(), member.getMemberRole().name()))
                .toList();
    }

    @Transactional
    public CompanyDtos.MemberItem addMember(CompanyDtos.MemberRequest request) {
        Company company = access.requireOwner().getCompany();
        User user = auth.createRecruiter(request.email(), request.firstName(), request.lastName());
        if (members.existsByUserId(user.getId())) {
            throw ApiException.conflict("Esa persona ya pertenece a una empresa.");
        }
        CompanyMember member = new CompanyMember();
        member.setCompany(company);
        member.setUser(user);
        member.setMemberRole(CompanyMemberRole.RECRUITER);
        members.save(member);
        return new CompanyDtos.MemberItem(user.getId(), user.fullName(), user.getEmail(), member.getMemberRole().name());
    }

    @Transactional(readOnly = true)
    public CompanyDtos.CompanyStats stats() {
        Company company = access.requireMember().getCompany();
        boolean advanced = plans.current(company).isAdvancedStats();
        List<Job> companyJobs = jobs.findByCompanyIdAndDeletedAtIsNullOrderByCreatedAtDesc(company.getId());
        long views = companyJobs.stream().mapToLong(Job::getViewCount).sum();
        Map<String, Long> byStatus = new LinkedHashMap<>();
        applications.countByStatusForCompany(company.getId()).forEach(row -> byStatus.put(String.valueOf(row[0]), (Long) row[1]));
        List<CompanyDtos.JobPerformance> performance = advanced
                ? companyJobs.stream().map(job -> new CompanyDtos.JobPerformance(job.getId(), job.getTitle(), job.getStatus().name(), job.getViewCount(),
                applications.findByJobIdOrderByCreatedAtDesc(job.getId()).size())).toList()
                : List.of();
        return new CompanyDtos.CompanyStats(
                jobs.countByCompanyIdAndStatusAndDeletedAtIsNull(company.getId(), JobStatus.PUBLISHED),
                applications.countByJobCompanyId(company.getId()),
                byStatus,
                views,
                performance,
                advanced);
    }

    private CompanyDtos.PublicCompany publicView(Company company) {
        return new CompanyDtos.PublicCompany(
                company.getId(),
                company.getName(),
                company.getSlug(),
                company.getDescription(),
                company.getWebsite(),
                company.getIndustry(),
                company.getProvince() == null ? null : company.getProvince().getName(),
                company.getMunicipality() == null ? null : company.getMunicipality().getName(),
                company.getCountry().getCode(),
                company.getCountry().getName(),
                company.getVerificationStatus() == VerificationStatus.VERIFIED,
                company.getLogoStorageKey() == null ? null : "/api/media/logos/" + company.getId());
    }
}
