package com.empleord.catalog;

import com.empleord.common.ApiException;
import com.empleord.domain.entity.Skill;
import com.empleord.domain.repository.SkillRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SkillCommandService {

    private final SkillRepository skills;
    private final SkillInserter inserter;

    public SkillCommandService(SkillRepository skills, SkillInserter inserter) {
        this.skills = skills;
        this.inserter = inserter;
    }

    public CatalogDtos.SkillItem create(String rawName) {
        String display = SkillNames.required(rawName);
        String key = SkillNames.key(display);
        return skills.findByNormalizedName(key)
                .map(SkillCommandService::toItem)
                .orElseGet(() -> insertOrRead(display, key));
    }

    private CatalogDtos.SkillItem insertOrRead(String display, String key) {
        try {
            return toItem(inserter.insert(display, key));
        } catch (DataIntegrityViolationException ex) {
            return skills.findByNormalizedName(key)
                    .map(SkillCommandService::toItem)
                    .orElseThrow(() -> ApiException.conflict("Esa habilidad ya existe."));
        }
    }

    private static CatalogDtos.SkillItem toItem(Skill skill) {
        return new CatalogDtos.SkillItem(skill.getId(), skill.getName());
    }

    @Service
    static class SkillInserter {

        private final SkillRepository skills;

        SkillInserter(SkillRepository skills) {
            this.skills = skills;
        }

        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public Skill insert(String name, String key) {
            Skill skill = new Skill();
            skill.setName(name);
            skill.setNormalizedName(key);
            return skills.saveAndFlush(skill);
        }
    }
}
