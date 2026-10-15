package com.empleord.candidate;

import com.empleord.catalog.CatalogService;
import com.empleord.common.ApiException;
import com.empleord.common.Text;
import com.empleord.domain.entity.CandidateLanguage;
import com.empleord.domain.entity.CandidateProfile;
import com.empleord.domain.entity.Certification;
import com.empleord.domain.entity.Education;
import com.empleord.domain.entity.Province;
import com.empleord.domain.entity.Skill;
import com.empleord.domain.entity.WorkExperience;
import com.empleord.domain.enums.ApplicationStatus;
import com.empleord.domain.enums.SalaryPeriod;
import com.empleord.domain.repository.CandidateLanguageRepository;
import com.empleord.domain.repository.CandidateProfileRepository;
import com.empleord.domain.repository.CertificationRepository;
import com.empleord.domain.repository.EducationRepository;
import com.empleord.domain.repository.JobAlertRepository;
import com.empleord.domain.repository.JobApplicationRepository;
import com.empleord.domain.repository.ResumeRepository;
import com.empleord.domain.repository.SavedJobRepository;
import com.empleord.domain.repository.WorkExperienceRepository;
import com.empleord.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class CandidateProfileService {

    private final CandidateProfileRepository profiles;
    private final WorkExperienceRepository experiences;
    private final EducationRepository educations;
    private final CertificationRepository certifications;
    private final CandidateLanguageRepository languages;
    private final ResumeRepository resumes;
    private final JobApplicationRepository applications;
    private final SavedJobRepository savedJobs;
    private final JobAlertRepository alerts;
    private final CatalogService catalog;

    public CandidateProfileService(CandidateProfileRepository profiles, WorkExperienceRepository experiences, EducationRepository educations,
                                   CertificationRepository certifications, CandidateLanguageRepository languages, ResumeRepository resumes,
                                   JobApplicationRepository applications, SavedJobRepository savedJobs, JobAlertRepository alerts, CatalogService catalog) {
        this.profiles = profiles;
        this.experiences = experiences;
        this.educations = educations;
        this.certifications = certifications;
        this.languages = languages;
        this.resumes = resumes;
        this.applications = applications;
        this.savedJobs = savedJobs;
        this.alerts = alerts;
        this.catalog = catalog;
    }

    @Transactional(readOnly = true)
    public CandidateDtos.ProfileResponse get() {
        return toResponse(requireProfile());
    }

    @Transactional
    public CandidateDtos.ProfileResponse update(CandidateDtos.ProfileRequest request) {
        CandidateProfile profile = requireProfile();
        Province province = catalog.province(request.provinceId());
        profile.setHeadline(Text.clean(request.headline()));
        profile.setSummary(Text.clean(request.summary()));
        profile.setProvince(province);
        profile.setMunicipality(catalog.municipality(request.municipalityId(), province));
        profile.setLinkedinUrl(Text.url(request.linkedinUrl()));
        profile.setPortfolioUrl(Text.url(request.portfolioUrl()));
        applySalary(profile, request.salaryMin(), request.salaryMax(), request.salaryCurrency(), request.salaryPeriod());
        profile.setOpenToWork(request.openToWork());
        return toResponse(profile);
    }

    @Transactional
    public CandidateDtos.ExperienceItem addExperience(CandidateDtos.ExperienceRequest request) {
        WorkExperience experience = new WorkExperience();
        experience.setCandidate(requireProfile());
        fill(experience, request);
        experiences.save(experience);
        return experienceItem(experience);
    }

    @Transactional
    public CandidateDtos.ExperienceItem updateExperience(UUID id, CandidateDtos.ExperienceRequest request) {
        WorkExperience experience = ownExperience(id);
        fill(experience, request);
        return experienceItem(experience);
    }

    @Transactional
    public void deleteExperience(UUID id) {
        experiences.delete(ownExperience(id));
    }

    @Transactional
    public CandidateDtos.EducationItem addEducation(CandidateDtos.EducationRequest request) {
        Education education = new Education();
        education.setCandidate(requireProfile());
        fill(education, request);
        educations.save(education);
        return educationItem(education);
    }

    @Transactional
    public CandidateDtos.EducationItem updateEducation(UUID id, CandidateDtos.EducationRequest request) {
        Education education = ownEducation(id);
        fill(education, request);
        return educationItem(education);
    }

    @Transactional
    public void deleteEducation(UUID id) {
        educations.delete(ownEducation(id));
    }

    @Transactional
    public CandidateDtos.CertificationItem addCertification(CandidateDtos.CertificationRequest request) {
        Certification certification = new Certification();
        certification.setCandidate(requireProfile());
        fill(certification, request);
        certifications.save(certification);
        return certificationItem(certification);
    }

    @Transactional
    public CandidateDtos.CertificationItem updateCertification(UUID id, CandidateDtos.CertificationRequest request) {
        Certification certification = ownCertification(id);
        fill(certification, request);
        return certificationItem(certification);
    }

    @Transactional
    public void deleteCertification(UUID id) {
        certifications.delete(ownCertification(id));
    }

    @Transactional
    public CandidateDtos.LanguageItem addLanguage(CandidateDtos.LanguageRequest request) {
        CandidateLanguage language = new CandidateLanguage();
        language.setCandidate(requireProfile());
        language.setLanguage(Text.required(request.language(), "El idioma"));
        language.setProficiency(request.proficiency());
        languages.save(language);
        return new CandidateDtos.LanguageItem(language.getId(), language.getLanguage(), language.getProficiency().name());
    }

    @Transactional
    public void deleteLanguage(UUID id) {
        CandidateLanguage language = languages.findById(id).orElseThrow(() -> ApiException.notFound("El idioma no existe."));
        if (!language.getCandidate().getUserId().equals(currentId())) {
            throw ApiException.forbidden("No puedes modificar este idioma.");
        }
        languages.delete(language);
    }

    @Transactional
    public List<CandidateDtos.CatalogSkill> replaceSkills(List<Integer> skillIds) {
        CandidateProfile profile = requireProfile();
        List<Skill> skills = catalog.skills(skillIds);
        profile.getSkills().clear();
        profile.getSkills().addAll(skills);
        return skills.stream().map(skill -> new CandidateDtos.CatalogSkill(skill.getId(), skill.getName())).toList();
    }

    @Transactional(readOnly = true)
    public CandidateDtos.CandidateStats stats() {
        UUID id = currentId();
        return new CandidateDtos.CandidateStats(
                applications.countByCandidateUserId(id),
                applications.countInProgress(id, List.of(ApplicationStatus.REJECTED, ApplicationStatus.WITHDRAWN, ApplicationStatus.HIRED)),
                savedJobs.countByCandidateUserId(id),
                alerts.countByCandidateUserIdAndActiveTrue(id));
    }

    public CandidateProfile requireProfile() {
        return profiles.findById(currentId()).orElseThrow(() -> ApiException.notFound("El perfil de candidato no existe."));
    }

    private CandidateDtos.ProfileResponse toResponse(CandidateProfile profile) {
        UUID id = profile.getUserId();
        return new CandidateDtos.ProfileResponse(
                profile.getHeadline(),
                profile.getSummary(),
                profile.getProvince() == null ? null : profile.getProvince().getId(),
                profile.getProvince() == null ? null : profile.getProvince().getName(),
                profile.getMunicipality() == null ? null : profile.getMunicipality().getId(),
                profile.getMunicipality() == null ? null : profile.getMunicipality().getName(),
                profile.getLinkedinUrl(),
                profile.getPortfolioUrl(),
                profile.getSalaryMin(),
                profile.getSalaryMax(),
                profile.getSalaryCurrency(),
                profile.getSalaryPeriod() == null ? null : profile.getSalaryPeriod().name(),
                profile.isOpenToWork(),
                experiences.findByCandidateUserIdOrderByStartDateDesc(id).stream().map(this::experienceItem).toList(),
                educations.findByCandidateUserIdOrderByEndYearDesc(id).stream().map(this::educationItem).toList(),
                certifications.findByCandidateUserIdOrderByIssueYearDesc(id).stream().map(this::certificationItem).toList(),
                languages.findByCandidateUserIdOrderByLanguageAsc(id).stream()
                        .map(language -> new CandidateDtos.LanguageItem(language.getId(), language.getLanguage(), language.getProficiency().name()))
                        .toList(),
                profile.getSkills().stream().map(skill -> new CandidateDtos.CatalogSkill(skill.getId(), skill.getName())).sorted(java.util.Comparator.comparing(CandidateDtos.CatalogSkill::name)).toList(),
                resumes.findByCandidateUserIdOrderByCreatedAtDesc(id).stream()
                        .map(resume -> new CandidateDtos.ResumeItem(resume.getId(), resume.getOriginalFilename(), resume.getSizeBytes(), resume.isPrimaryResume(), resume.getCreatedAt()))
                        .toList());
    }

    private void fill(WorkExperience experience, CandidateDtos.ExperienceRequest request) {
        if (request.endDate() != null && request.endDate().isBefore(request.startDate())) {
            throw ApiException.badRequest("La fecha de salida no puede ser anterior al inicio.");
        }
        experience.setJobTitle(Text.required(request.jobTitle(), "El cargo"));
        experience.setCompanyName(Text.required(request.companyName(), "La empresa"));
        experience.setLocation(Text.clean(request.location()));
        experience.setStartDate(request.startDate());
        experience.setCurrentJob(request.currentJob());
        experience.setEndDate(request.currentJob() ? null : request.endDate());
        experience.setDescription(Text.clean(request.description()));
    }

    private void fill(Education education, CandidateDtos.EducationRequest request) {
        education.setInstitution(Text.required(request.institution(), "La institución"));
        education.setDegree(Text.required(request.degree(), "El título"));
        education.setFieldOfStudy(Text.clean(request.fieldOfStudy()));
        education.setStartYear(request.startYear());
        education.setEndYear(request.endYear());
        education.setDescription(Text.clean(request.description()));
    }

    private void fill(Certification certification, CandidateDtos.CertificationRequest request) {
        certification.setName(Text.required(request.name(), "El nombre"));
        certification.setIssuer(Text.clean(request.issuer()));
        certification.setIssueYear(request.issueYear());
        certification.setCredentialUrl(Text.url(request.credentialUrl()));
    }

    private void applySalary(CandidateProfile profile, BigDecimal min, BigDecimal max, String currency, SalaryPeriod period) {
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw ApiException.badRequest("El salario mínimo no puede ser mayor que el máximo.");
        }
        if ((min != null && min.signum() < 0) || (max != null && max.signum() < 0)) {
            throw ApiException.badRequest("El salario no puede ser negativo.");
        }
        profile.setSalaryMin(min);
        profile.setSalaryMax(max);
        profile.setSalaryCurrency(currency == null || currency.isBlank() ? "DOP" : currency.trim().toUpperCase());
        profile.setSalaryPeriod(period);
    }

    private WorkExperience ownExperience(UUID id) {
        WorkExperience experience = experiences.findById(id).orElseThrow(() -> ApiException.notFound("La experiencia no existe."));
        if (!experience.getCandidate().getUserId().equals(currentId())) {
            throw ApiException.forbidden("No puedes modificar esta experiencia.");
        }
        return experience;
    }

    private Education ownEducation(UUID id) {
        Education education = educations.findById(id).orElseThrow(() -> ApiException.notFound("La formación no existe."));
        if (!education.getCandidate().getUserId().equals(currentId())) {
            throw ApiException.forbidden("No puedes modificar esta formación.");
        }
        return education;
    }

    private Certification ownCertification(UUID id) {
        Certification certification = certifications.findById(id).orElseThrow(() -> ApiException.notFound("La certificación no existe."));
        if (!certification.getCandidate().getUserId().equals(currentId())) {
            throw ApiException.forbidden("No puedes modificar esta certificación.");
        }
        return certification;
    }

    private CandidateDtos.ExperienceItem experienceItem(WorkExperience experience) {
        return new CandidateDtos.ExperienceItem(experience.getId(), experience.getJobTitle(), experience.getCompanyName(), experience.getLocation(),
                experience.getStartDate(), experience.getEndDate(), experience.isCurrentJob(), experience.getDescription());
    }

    private CandidateDtos.EducationItem educationItem(Education education) {
        return new CandidateDtos.EducationItem(education.getId(), education.getInstitution(), education.getDegree(), education.getFieldOfStudy(),
                education.getStartYear(), education.getEndYear(), education.getDescription());
    }

    private CandidateDtos.CertificationItem certificationItem(Certification certification) {
        return new CandidateDtos.CertificationItem(certification.getId(), certification.getName(), certification.getIssuer(), certification.getIssueYear(), certification.getCredentialUrl());
    }

    private UUID currentId() {
        return SecurityUtils.requireUser().getId();
    }
}
