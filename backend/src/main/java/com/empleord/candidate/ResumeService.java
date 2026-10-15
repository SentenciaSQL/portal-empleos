package com.empleord.candidate;

import com.empleord.common.ApiException;
import com.empleord.config.AppProperties;
import com.empleord.domain.entity.CompanyMember;
import com.empleord.domain.entity.Resume;
import com.empleord.domain.entity.User;
import com.empleord.domain.enums.UserRole;
import com.empleord.domain.repository.CompanyMemberRepository;
import com.empleord.domain.repository.JobApplicationRepository;
import com.empleord.domain.repository.ResumeRepository;
import com.empleord.domain.repository.UserRepository;
import com.empleord.security.SecurityUtils;
import com.empleord.security.UserPrincipal;
import com.empleord.storage.FileRules;
import com.empleord.storage.FileSignatureService;
import com.empleord.storage.StorageService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

@Service
public class ResumeService {

    private final ResumeRepository resumes;
    private final UserRepository users;
    private final CompanyMemberRepository members;
    private final JobApplicationRepository applications;
    private final StorageService storage;
    private final FileSignatureService signatures;
    private final AppProperties properties;
    private final CandidateProfileService profiles;

    public ResumeService(ResumeRepository resumes, UserRepository users, CompanyMemberRepository members, JobApplicationRepository applications,
                         StorageService storage, FileSignatureService signatures, AppProperties properties, CandidateProfileService profiles) {
        this.resumes = resumes;
        this.users = users;
        this.members = members;
        this.applications = applications;
        this.storage = storage;
        this.signatures = signatures;
        this.properties = properties;
        this.profiles = profiles;
    }

    @Transactional
    public CandidateDtos.ResumeItem upload(MultipartFile file) {
        if (resumes.countByCandidateUserId(SecurityUtils.requireUser().getId()) >= 3) {
            throw ApiException.conflict("Puedes guardar hasta 3 currículums. Elimina uno antes de subir otro.");
        }
        byte[] bytes = FileRules.pdf(file);
        StorageService.StoredFile stored = storage.store("resumes", file.getOriginalFilename(), bytes);
        Resume resume = new Resume();
        resume.setCandidate(profiles.requireProfile());
        resume.setOriginalFilename(file.getOriginalFilename() == null ? "curriculum.pdf" : file.getOriginalFilename());
        resume.setStorageKey(stored.key());
        resume.setContentType("application/pdf");
        resume.setSizeBytes(stored.size());
        resume.setPrimaryResume(resumes.countByCandidateUserId(SecurityUtils.requireUser().getId()) == 0);
        resumes.save(resume);
        return new CandidateDtos.ResumeItem(resume.getId(), resume.getOriginalFilename(), resume.getSizeBytes(), resume.isPrimaryResume(), resume.getCreatedAt());
    }

    @Transactional
    public void delete(UUID id) {
        Resume resume = own(id);
        if (applications.existsByResumeId(id)) {
            throw ApiException.conflict("Este currículum ya se usó en una postulación y no puede eliminarse.");
        }
        storage.delete(resume.getStorageKey());
        boolean primary = resume.isPrimaryResume();
        resumes.delete(resume);
        if (primary) {
            resumes.findByCandidateUserIdOrderByCreatedAtDesc(SecurityUtils.requireUser().getId()).stream().findFirst()
                    .ifPresent(next -> next.setPrimaryResume(true));
        }
    }

    @Transactional
    public void makePrimary(UUID id) {
        UUID candidateId = SecurityUtils.requireUser().getId();
        resumes.findByCandidateUserIdOrderByCreatedAtDesc(candidateId).forEach(resume -> resume.setPrimaryResume(resume.getId().equals(id)));
        if (resumes.findByCandidateUserIdOrderByCreatedAtDesc(candidateId).stream().noneMatch(resume -> resume.getId().equals(id))) {
            throw ApiException.notFound("El currículum no existe.");
        }
    }

    @Transactional(readOnly = true)
    public String signedLinkForCurrentUser(UUID resumeId) {
        own(resumeId);
        return link(resumeId, SecurityUtils.requireUser().getId());
    }

    @Transactional(readOnly = true)
    public String signedLinkForCompany(UUID applicationResumeId, UUID companyUserId) {
        Resume resume = resumes.findById(applicationResumeId).orElseThrow(() -> ApiException.notFound("El currículum no existe."));
        assertCanRead(resume, companyUserId);
        return link(resume.getId(), companyUserId);
    }

    @Transactional(readOnly = true)
    public ResponseEntity<Resource> stream(String token) {
        FileSignatureService.SignedAccess access = signatures.parse(token);
        Resume resume = resumes.findById(access.resumeId()).orElseThrow(() -> ApiException.notFound("El currículum no existe."));
        assertCanRead(resume, access.userId());
        String filename = URLEncoder.encode(resume.getOriginalFilename(), StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.APPLICATION_PDF)
                .body(storage.load(resume.getStorageKey()));
    }

    public Resume own(UUID id) {
        Resume resume = resumes.findById(id).orElseThrow(() -> ApiException.notFound("El currículum no existe."));
        if (!resume.getCandidate().getUserId().equals(SecurityUtils.requireUser().getId())) {
            throw ApiException.forbidden("No puedes usar este currículum.");
        }
        return resume;
    }

    public Resume primaryOrOwned(UUID requestedId) {
        if (requestedId != null) {
            return own(requestedId);
        }
        return resumes.findFirstByCandidateUserIdAndPrimaryResumeTrue(SecurityUtils.requireUser().getId())
                .orElseThrow(() -> ApiException.badRequest("Sube un currículum en PDF antes de postularte."));
    }

    private String link(UUID resumeId, UUID userId) {
        String token = signatures.sign(resumeId, userId, Duration.ofMinutes(10));
        return properties.getApiPublicUrl() + "/api/files/resumes?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    private void assertCanRead(Resume resume, UUID userId) {
        User user = users.findActiveById(userId).orElseThrow(() -> ApiException.unauthorized("El enlace del archivo no es válido."));
        UserRole role = UserRole.valueOf(user.getRole().getName());
        if (role == UserRole.ADMIN || resume.getCandidate().getUserId().equals(userId)) {
            return;
        }
        if (role == UserRole.EMPLOYER) {
            CompanyMember member = members.findByUserId(userId).orElse(null);
            if (member != null && applications.existsByResumeIdAndJobCompanyId(resume.getId(), member.getCompany().getId())) {
                return;
            }
        }
        throw ApiException.forbidden("No tienes autorización para ver este currículum.");
    }

    public UserPrincipal current() {
        return SecurityUtils.requireUser();
    }
}
