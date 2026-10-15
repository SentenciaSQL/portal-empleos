package com.empleord.company;

import com.empleord.common.ApiException;
import com.empleord.domain.entity.CompanyMember;
import com.empleord.domain.entity.Job;
import com.empleord.domain.enums.CompanyMemberRole;
import com.empleord.domain.repository.CompanyMemberRepository;
import com.empleord.security.SecurityUtils;
import org.springframework.stereotype.Service;

@Service
public class CompanyAccess {

    private final CompanyMemberRepository members;

    public CompanyAccess(CompanyMemberRepository members) {
        this.members = members;
    }

    public CompanyMember requireMember() {
        return members.findByUserId(SecurityUtils.requireUser().getId())
                .orElseThrow(() -> ApiException.forbidden("No perteneces a una empresa."));
    }

    public CompanyMember requireOwner() {
        CompanyMember member = requireMember();
        if (member.getMemberRole() != CompanyMemberRole.OWNER) {
            throw ApiException.forbidden("Solo quien administra la empresa puede hacer este cambio.");
        }
        return member;
    }

    public CompanyMember requireJob(Job job) {
        CompanyMember member = requireMember();
        if (job.getDeletedAt() != null || !member.getCompany().getId().equals(job.getCompany().getId())) {
            throw ApiException.forbidden("Esta oferta no pertenece a tu empresa.");
        }
        return member;
    }
}
