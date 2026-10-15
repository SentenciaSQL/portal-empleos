package com.empleord.candidate;

import com.empleord.audit.AuditService;
import com.empleord.common.Tokens;
import com.empleord.domain.entity.CandidateProfile;
import com.empleord.domain.entity.User;
import com.empleord.domain.enums.UserStatus;
import com.empleord.domain.repository.CandidateLanguageRepository;
import com.empleord.domain.repository.CertificationRepository;
import com.empleord.domain.repository.EducationRepository;
import com.empleord.domain.repository.JobApplicationRepository;
import com.empleord.domain.repository.RefreshTokenRepository;
import com.empleord.domain.repository.ResumeRepository;
import com.empleord.domain.repository.UserRepository;
import com.empleord.domain.repository.WorkExperienceRepository;
import com.empleord.security.SecurityUtils;
import com.empleord.storage.StorageService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class AccountService {

    private final UserRepository users;
    private final CandidateProfileService profiles;
    private final WorkExperienceRepository experiences;
    private final EducationRepository educations;
    private final CertificationRepository certifications;
    private final CandidateLanguageRepository languages;
    private final ResumeRepository resumes;
    private final JobApplicationRepository applications;
    private final RefreshTokenRepository refreshTokens;
    private final StorageService storage;
    private final PasswordEncoder encoder;
    private final AuditService audit;

    public AccountService(UserRepository users, CandidateProfileService profiles, WorkExperienceRepository experiences, EducationRepository educations,
                          CertificationRepository certifications, CandidateLanguageRepository languages, ResumeRepository resumes,
                          JobApplicationRepository applications, RefreshTokenRepository refreshTokens, StorageService storage,
                          PasswordEncoder encoder, AuditService audit) {
        this.users = users;
        this.profiles = profiles;
        this.experiences = experiences;
        this.educations = educations;
        this.certifications = certifications;
        this.languages = languages;
        this.resumes = resumes;
        this.applications = applications;
        this.refreshTokens = refreshTokens;
        this.storage = storage;
        this.encoder = encoder;
        this.audit = audit;
    }

    @Transactional
    public void deleteCandidate(String ip) {
        UUID id = SecurityUtils.requireUser().getId();
        User user = users.findActiveById(id).orElseThrow();
        resumes.findByCandidateUserIdOrderByCreatedAtDesc(id).forEach(resume -> storage.delete(resume.getStorageKey()));
        resumes.deleteAll(resumes.findByCandidateUserIdOrderByCreatedAtDesc(id));
        experiences.deleteByCandidateUserId(id);
        educations.deleteByCandidateUserId(id);
        certifications.deleteByCandidateUserId(id);
        languages.deleteByCandidateUserId(id);
        CandidateProfile profile = profiles.requireProfile();
        profile.getSkills().clear();
        profile.setHeadline(null);
        profile.setSummary(null);
        profile.setProvince(null);
        profile.setMunicipality(null);
        profile.setLinkedinUrl(null);
        profile.setPortfolioUrl(null);
        profile.setSalaryMin(null);
        profile.setSalaryMax(null);
        profile.setOpenToWork(false);
        applications.findByCandidateUserIdOrderByCreatedAtDesc(id).forEach(application -> application.setCoverLetter(null));
        refreshTokens.revokeActiveForUser(id);
        user.setEmail("eliminado+" + id + "@deleted.empleord.local");
        user.setFirstName("Cuenta");
        user.setLastName("eliminada");
        user.setPhone(null);
        user.setStatus(UserStatus.DELETED);
        user.setDeletedAt(Instant.now());
        user.setPasswordHash(encoder.encode(Tokens.random()));
        user.setEmailVerificationTokenHash(null);
        user.setPasswordResetTokenHash(null);
        audit.record(id, "DELETE_ACCOUNT", "User", id.toString(), "anonymized", ip);
    }
}
