package org.example.amortizationhelper.Tools;

import lombok.AllArgsConstructor;
import org.example.amortizationhelper.Entity.KopAndel;
import org.example.amortizationhelper.repo.KopAndelRepo;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Component
@AllArgsConstructor
public class KopAndelTools {

    private static final DateTimeFormatter INPUT_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private static final DateTimeFormatter OUTPUT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final KopAndelRepo kopAndelRepo;

    @Tool(
            name = "kop_andel_links",
            description = "Hämtar alla aktuella KöpAndel-länkar. id 1 är V85 och id 2 är V86."
    )
    public List<KopAndelView> kopAndelLinks() {
        return kopAndelRepo.findAllByOrderByIdAsc().stream()
                .map(this::toView)
                .toList();
    }

    @Tool(
            name = "kop_andel_by_spelform",
            description = "Hämtar aktuell KöpAndel-länk för V85 eller V86."
    )
    public KopAndelView kopAndelBySpelform(
            @ToolParam(description = "Spelform eller fras, till exempel V85, V86, köpandel V85 eller köpandel V86") String spelformOrPhrase
    ) {
        Long id = resolveId(spelformOrPhrase);
        if (id == null) {
            return null;
        }

        return kopAndelRepo.findById(id)
                .map(this::toView)
                .orElse(null);
    }

    @Tool(
            name = "kop_andel_by_id",
            description = "Hämtar aktuell KöpAndel-länk via id. id 1 är V85 och id 2 är V86."
    )
    public KopAndelView kopAndelById(
            @ToolParam(description = "1 för V85 eller 2 för V86") Long id
    ) {
        if (id == null) {
            return null;
        }

        return kopAndelRepo.findById(id)
                .map(this::toView)
                .orElse(null);
    }

    private KopAndelView toView(KopAndel row) {
        if (row == null) {
            return null;
        }

        return new KopAndelView(
                row.getId(),
                mapSpelform(row.getId()),
                row.getUrl(),
                row.getDate(),
                formatDate(row.getDate())
        );
    }

    private Long resolveId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.toLowerCase(Locale.ROOT).trim();

        if (normalized.contains("v85") || normalized.contains("85")) {
            return 1L;
        }

        if (normalized.contains("v86") || normalized.contains("86")) {
            return 2L;
        }

        return null;
    }

    private String mapSpelform(Long id) {
        if (id == null) {
            return "Okänd";
        }
        if (id == 1L) {
            return "V85";
        }
        if (id == 2L) {
            return "V86";
        }
        return "Okänd";
    }

    private String formatDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }

        try {
            LocalDateTime parsed = LocalDateTime.parse(raw.trim(), INPUT_FORMAT);
            return parsed.format(OUTPUT_FORMAT);
        } catch (Exception e) {
            return raw;
        }
    }

    public static class KopAndelView {
        public Long id;
        public String spelform;
        public String url;
        public String date;
        public String formattedDate;

        public KopAndelView(Long id, String spelform, String url, String date, String formattedDate) {
            this.id = id;
            this.spelform = spelform;
            this.url = url;
            this.date = date;
            this.formattedDate = formattedDate;
        }
    }
}