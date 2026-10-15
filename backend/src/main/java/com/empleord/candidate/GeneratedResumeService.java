package com.empleord.candidate;

import com.empleord.common.Text;
import com.empleord.common.Texts;
import com.empleord.domain.entity.CandidateLanguage;
import com.empleord.domain.entity.CandidateProfile;
import com.empleord.domain.entity.Certification;
import com.empleord.domain.entity.Education;
import com.empleord.domain.entity.Skill;
import com.empleord.domain.entity.User;
import com.empleord.domain.entity.WorkExperience;
import com.empleord.domain.repository.CandidateLanguageRepository;
import com.empleord.domain.repository.CandidateProfileRepository;
import com.empleord.domain.repository.CertificationRepository;
import com.empleord.domain.repository.EducationRepository;
import com.empleord.domain.repository.WorkExperienceRepository;
import com.empleord.security.SecurityUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class GeneratedResumeService {

    private static final float LEFT = 48;
    private static final float WIDTH = 500;
    private static final float TOP = 790;
    private static final float BOTTOM = 46;
    private static final Color NAVY = new Color(14, 39, 68);
    private static final Color ACCENT = new Color(141, 61, 22);
    private static final Color INK = new Color(20, 32, 51);
    private static final Color MUTED = new Color(94, 108, 123);

    private final CandidateProfileRepository profiles;
    private final WorkExperienceRepository experiences;
    private final EducationRepository educations;
    private final CertificationRepository certifications;
    private final CandidateLanguageRepository languages;

    public GeneratedResumeService(CandidateProfileRepository profiles, WorkExperienceRepository experiences, EducationRepository educations,
                                  CertificationRepository certifications, CandidateLanguageRepository languages) {
        this.profiles = profiles;
        this.experiences = experiences;
        this.educations = educations;
        this.certifications = certifications;
        this.languages = languages;
    }

    @Transactional(readOnly = true)
    public Document forCurrentUser() {
        CandidateProfile profile = profiles.findById(SecurityUtils.requireUser().getId()).orElseThrow();
        User user = profile.getUser();
        boolean english = "en".equalsIgnoreCase(user.getLocale());
        Locale locale = english ? Locale.ENGLISH : Locale.forLanguageTag("es-DO");
        DateTimeFormatter dates = DateTimeFormatter.ofPattern("MMM yyyy", locale);
        try (PDDocument document = new PDDocument()) {
            Sheet sheet = new Sheet(document, new PDType1Font(Standard14Fonts.FontName.HELVETICA), new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD));
            sheet.header(safe(user.fullName()), safe(profile.getHeadline()));
            contact(sheet, user, profile);
            section(sheet, english ? "Summary" : "Resumen", profile.getSummary());
            List<WorkExperience> jobs = experiences.findByCandidateUserIdOrderByStartDateDesc(user.getId());
            if (!jobs.isEmpty()) {
                sheet.heading(english ? "Experience" : "Experiencia");
                for (WorkExperience job : jobs) {
                    String when = formatRange(job.getStartDate(), job.getEndDate(), job.isCurrentJob(), dates, english);
                    sheet.entry(safe(job.getJobTitle()), join(safe(job.getCompanyName()), safe(job.getLocation()), when));
                    sheet.paragraph(job.getDescription(), false);
                }
            }
            List<Education> studies = educations.findByCandidateUserIdOrderByEndYearDesc(user.getId());
            if (!studies.isEmpty()) {
                sheet.heading(english ? "Education" : "Formación");
                for (Education study : studies) {
                    String years = join(study.getStartYear() == null ? null : String.valueOf(study.getStartYear()), study.getEndYear() == null ? null : String.valueOf(study.getEndYear()), null);
                    sheet.entry(safe(study.getDegree()), join(safe(study.getInstitution()), safe(study.getFieldOfStudy()), years));
                    sheet.paragraph(study.getDescription(), false);
                }
            }
            List<String> skillNames = profile.getSkills().stream().map(Skill::getName).sorted(Comparator.naturalOrder()).toList();
            if (!skillNames.isEmpty()) {
                sheet.heading(english ? "Skills" : "Habilidades");
                sheet.paragraph(String.join(" · ", skillNames), false);
            }
            List<CandidateLanguage> spoken = languages.findByCandidateUserIdOrderByLanguageAsc(user.getId());
            if (!spoken.isEmpty()) {
                sheet.heading(english ? "Languages" : "Idiomas");
                sheet.paragraph(spoken.stream().map(item -> safe(item.getLanguage()) + " — " + proficiency(item.getProficiency().name(), english)).reduce((a, b) -> a + "\n" + b).orElse(""), false);
            }
            List<Certification> courses = certifications.findByCandidateUserIdOrderByIssueYearDesc(user.getId());
            if (!courses.isEmpty()) {
                sheet.heading(english ? "Courses and certifications" : "Cursos y certificaciones");
                for (Certification course : courses) {
                    String meta = join(safe(course.getIssuer()), course.getIssueYear() == null ? null : String.valueOf(course.getIssueYear()), null);
                    sheet.entry(safe(course.getName()), meta);
                    if (course.getCredentialUrl() != null && course.getCredentialUrl().startsWith("http")) {
                        sheet.link(course.getCredentialUrl());
                    }
                }
            }
            List<String> links = new ArrayList<>();
            if (profile.getLinkedinUrl() != null && profile.getLinkedinUrl().startsWith("http")) {
                links.add(profile.getLinkedinUrl());
            }
            if (profile.getPortfolioUrl() != null && profile.getPortfolioUrl().startsWith("http")) {
                links.add(profile.getPortfolioUrl());
            }
            if (!links.isEmpty()) {
                sheet.heading(english ? "Links" : "Enlaces");
                for (String url : links) {
                    sheet.link(url);
                }
            }
            sheet.close();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return new Document(output.toByteArray(), filename(user, english));
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo generar el currículum", ex);
        }
    }

    private void contact(Sheet sheet, User user, CandidateProfile profile) throws IOException {
        List<String> parts = new ArrayList<>();
        if (user.getEmail() != null) parts.add(user.getEmail());
        if (user.getPhone() != null) parts.add(user.getPhone());
        if (profile.getMunicipality() != null && profile.getProvince() != null) {
            parts.add(profile.getMunicipality().getName() + ", " + profile.getProvince().getName());
        } else if (profile.getProvince() != null) {
            parts.add(profile.getProvince().getName());
        }
        if (!parts.isEmpty()) {
            sheet.muted(String.join("  ·  ", parts));
        }
    }

    private void section(Sheet sheet, String title, String body) throws IOException {
        if (body == null || body.isBlank()) {
            return;
        }
        sheet.heading(title);
        sheet.paragraph(body, false);
    }

    private String formatRange(LocalDate start, LocalDate end, boolean current, DateTimeFormatter dates, boolean english) {
        String from = start == null ? "" : dates.format(start);
        String to = current ? (english ? "Present" : "Actual") : end == null ? "" : dates.format(end);
        if (from.isBlank()) return to;
        if (to.isBlank()) return from;
        return from + " – " + to;
    }

    private String proficiency(String value, boolean english) {
        return switch (value) {
            case "BASIC" -> english ? "Basic" : "Básico";
            case "INTERMEDIATE" -> english ? "Intermediate" : "Intermedio";
            case "ADVANCED" -> english ? "Advanced" : "Avanzado";
            case "NATIVE" -> english ? "Native" : "Nativo";
            default -> value;
        };
    }

    private String join(String first, String second, String third) {
        List<String> parts = new ArrayList<>();
        if (first != null && !first.isBlank()) parts.add(first);
        if (second != null && !second.isBlank()) parts.add(second);
        if (third != null && !third.isBlank()) parts.add(third);
        return String.join(" · ", parts);
    }

    private String safe(String value) {
        return value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private String filename(User user, boolean english) {
        String base = Text.slug(user.getFirstName() + "-" + user.getLastName());
        if (base.equals("item")) {
            base = "candidato";
        }
        return (english ? "resume-" : "curriculum-") + base + ".pdf";
    }

    public record Document(byte[] bytes, String filename) {
    }

    private static final class Sheet {
        private final PDDocument document;
        private final PDFont regular;
        private final PDFont bold;
        private PDPage page;
        private PDPageContentStream stream;
        private float y;
        private Sheet(PDDocument document, PDFont regular, PDFont bold) throws IOException {
            this.document = document;
            this.regular = regular;
            this.bold = bold;
            openPage(false);
        }

        private void header(String name, String headline) throws IOException {
            stream.setNonStrokingColor(NAVY);
            stream.addRect(0, 760, PDRectangle.A4.getWidth(), 82);
            stream.fill();
            text(bold, 20, Color.WHITE, LEFT, 798, name.isBlank() ? "EmpleoRD" : name);
            if (!headline.isBlank()) {
                text(regular, 11, new Color(232, 224, 210), LEFT, 776, headline);
            }
            y = 740;
        }

        private void heading(String title) throws IOException {
            ensure(36);
            y -= 16;
            text(bold, 12, ACCENT, LEFT, y, title.toUpperCase(Locale.ROOT));
            y -= 6;
            stream.setStrokingColor(ACCENT);
            stream.setLineWidth(0.6f);
            stream.moveTo(LEFT, y);
            stream.lineTo(LEFT + WIDTH, y);
            stream.stroke();
            y -= 14;
        }

        private void entry(String title, String meta) throws IOException {
            if (title.isBlank() && meta.isBlank()) {
                return;
            }
            ensure(32);
            if (!title.isBlank()) {
                text(bold, 11, INK, LEFT, y, title);
                y -= 14;
            }
            if (!meta.isBlank()) {
                muted(meta);
            }
        }

        private void muted(String value) throws IOException {
            paragraph(value, true);
        }

        private void paragraph(String value, boolean muted) throws IOException {
            if (value == null || value.isBlank()) {
                return;
            }
            PDFont font = regular;
            Color color = muted ? MUTED : INK;
            for (String block : value.split("\\R")) {
                for (String line : wrap(font, 10, block.isBlank() ? " " : block, WIDTH)) {
                    ensure(14);
                    text(font, 10, color, LEFT, y, line);
                    y -= 13;
                }
            }
            y -= 4;
        }

        private void link(String url) throws IOException {
            ensure(16);
            String label = url.length() > 80 ? url.substring(0, 77) + "..." : url;
            text(regular, 10, ACCENT, LEFT, y, label);
            float width = Math.min(WIDTH, stringWidth(regular, 10, label));
            PDAnnotationLink annotation = new PDAnnotationLink();
            annotation.setRectangle(new PDRectangle(LEFT, y - 2, width, 12));
            PDActionURI action = new PDActionURI();
            action.setURI(url);
            annotation.setAction(action);
            page.getAnnotations().add(annotation);
            y -= 16;
        }

        private void text(PDFont font, float size, Color color, float x, float baseline, String value) throws IOException {
            stream.setNonStrokingColor(color);
            stream.beginText();
            stream.setFont(font, size);
            stream.newLineAtOffset(x, baseline);
            stream.showText(encode(font, value));
            stream.endText();
        }

        private void ensure(float needed) throws IOException {
            if (y - needed >= BOTTOM) {
                return;
            }
            openPage(true);
        }

        private void openPage(boolean continued) throws IOException {
            if (stream != null) {
                stream.close();
            }
            page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            y = TOP;
            if (continued) {
                text(bold, 9, MUTED, LEFT, 820, "EmpleoRD");
            }
        }

        private void close() throws IOException {
            stream.close();
            stream = null;
        }

        private List<String> wrap(PDFont font, float size, String value, float width) throws IOException {
            List<String> lines = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            for (String word : value.split(" ")) {
                String candidate = current.isEmpty() ? word : current + " " + word;
                if (stringWidth(font, size, candidate) <= width) {
                    current.setLength(0);
                    current.append(candidate);
                    continue;
                }
                if (!current.isEmpty()) {
                    lines.add(current.toString());
                    current.setLength(0);
                }
                if (stringWidth(font, size, word) <= width) {
                    current.append(word);
                } else {
                    StringBuilder piece = new StringBuilder();
                    for (char character : word.toCharArray()) {
                        if (stringWidth(font, size, piece.toString() + character) > width && !piece.isEmpty()) {
                            lines.add(piece.toString());
                            piece.setLength(0);
                        }
                        piece.append(character);
                    }
                    current.append(piece);
                }
            }
            if (!current.isEmpty()) {
                lines.add(current.toString());
            }
            if (lines.isEmpty()) {
                lines.add("");
            }
            return lines;
        }

        private float stringWidth(PDFont font, float size, String value) throws IOException {
            return font.getStringWidth(encode(font, value)) / 1000f * size;
        }

        private String encode(PDFont font, String value) throws IOException {
            StringBuilder encoded = new StringBuilder();
            for (int i = 0; i < value.length(); ) {
                int point = value.codePointAt(i);
                String character = new String(Character.toChars(point));
                try {
                    font.encode(character);
                    encoded.append(character);
                } catch (IllegalArgumentException | IOException ex) {
                    encoded.append('?');
                }
                i += Character.charCount(point);
            }
            return encoded.toString();
        }
    }
}
