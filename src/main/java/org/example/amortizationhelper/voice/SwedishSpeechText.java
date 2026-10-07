package org.example.amortizationhelper.voice;

import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Keeps the displayed answer intact while making its spoken version easier to follow. */
public final class SwedishSpeechText {

    private static final Pattern RANKED_HORSE = Pattern.compile(
            "^\\d{1,2}[.)]\\s+(.+?)\\s+\\(([1-9]\\d?)\\)(\\s*(?:[–—-].*|[.!?])?)$");

    private SwedishSpeechText() {
    }

    public static String clean(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String clean = text.replace("\r\n", "\n").replace('\r', '\n')
                .replace('\u00a0', ' ')
                .replaceAll("(?s)```.*?```|~~~.*?~~~", " ")
                .replaceAll("!?\\[([^\\]\\n]+)\\]\\([^\\s)]+(?:\\s+\"[^\"]*\")?\\)", "$1")
                .replaceAll("(?i)<br\\s*/?>|</(?:p|div|li|tr|h[1-6])\\s*>", "\n")
                .replaceAll("</?[A-Za-z][^>\\n]*>", " ")
                .replaceAll("(?i)\\b(?:https?://|www\\.)[^\\s<>()]+", " ")
                .replace("&nbsp;", " ").replace("&amp;", " och ")
                .replace("&quot;", "\"").replace("&#39;", "'")
                .replace("&lt;", " mindre än ").replace("&gt;", " större än ")
                .replace("≈", " ungefär ").replace("≤", " högst ").replace("≥", " minst ")
                .replace("%", " procent").replace("&", " och ")
                .replaceAll("[\\p{So}\\p{Cn}\\x{1F3FB}-\\x{1F3FF}\\uFE0F\\u200D\\u20E3]", "")
                .replaceAll("[\\p{Cc}&&[^\\n\\t]]", " ")
                .replaceAll("(?iu)(?<!\\p{L})t\\.\\s*ex\\.(?!\\p{L})", "till exempel")
                .replaceAll("(?iu)(?<!\\p{L})bl\\.\\s*a\\.(?!\\p{L})", "bland annat")
                .replaceAll("(?iu)(?<!\\p{L})d\\.\\s*v\\.\\s*s\\.(?!\\p{L})", "det vill säga")
                .replaceAll("(?iu)(?<!\\p{L})m\\.\\s*fl\\.(?!\\p{L})", "med flera")
                .replaceAll("(?iu)(?<!\\p{L})o\\.\\s*s\\.\\s*v\\.(?!\\p{L})", "och så vidare")
                .replaceAll("(?iu)\\bca\\.?\\s+(?=\\d)", "cirka ")
                .replaceAll("(?iu)\\bnr\\.?\\s*(?=\\d)", "nummer ")
                .replaceAll("(?iu)\\bavd\\.?\\s*(?=\\d)", "avdelning ")
                .replaceAll("(?iu)(?<=\\d)\\s*km\\b", " kilometer")
                .replaceAll("(?iu)(?<=\\d)\\s*m\\b(?!\\s*/)", " meter")
                .replaceAll("(?iu)(?<=\\d)\\s*kr\\b", " kronor")
                .replaceAll("(?iu)(?<=\\d)\\s*sek\\b", " kronor")
                .replaceAll("[*_`~]+", "");

        return clean.lines()
                .map(SwedishSpeechText::cleanLine)
                .filter(line -> !line.isBlank())
                .collect(Collectors.joining("\n"));
    }

    private static String cleanLine(String line) {
        String clean = line.trim();
        if (clean.matches("[|:\\-\\s]+")) {
            return "";
        }
        clean = clean.replaceAll("^#{1,6}\\s*|^>\\s*|^[\\-•–]\\s+", "");
        Matcher horse = RANKED_HORSE.matcher(clean);
        if (horse.matches()) {
            // In "1. Horse (7)" only the parenthesized number identifies the horse.
            clean = horse.group(1) + ", nummer " + horse.group(2) + horse.group(3);
        } else {
            // Preserve numbers in other lists; they may carry information of their own.
            clean = clean.replaceAll("^(\\d{1,2})[.)]\\s+", "$1, ");
        }
        clean = clean.replaceAll("^\\|\\s*|\\s*\\|$", "")
                .replaceAll("\\s*\\|\\s*", ", ")
                .replaceAll("\\s+", " ")
                .trim();
        if (clean.isBlank() || clean.matches("[\\p{Punct}\\s]+")) {
            return "";
        }
        return clean.matches(".*[.!?:][\"”»)]?$") ? clean : clean + ".";
    }
}
