package gg.fotia.tags.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class CustomTagTextFilter {

    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("(?i)[&§]#[0-9a-f]{6}");
    private static final Pattern BUKKIT_HEX_COLOR_PATTERN = Pattern.compile("(?i)[&§]x([&§][0-9a-f]){6}");
    private static final Pattern BASIC_COLOR_PATTERN = Pattern.compile("(?i)[&§][0-9a-fk-or]");
    private static final Pattern MINIMESSAGE_PATTERN = Pattern.compile("<[^>]+>");

    private final boolean enabled;
    private final boolean ignoreCase;
    private final boolean stripColorsBeforeCheck;
    private final boolean stripMiniMessageBeforeCheck;
    private final List<String> blockedWords;
    private final List<Pattern> blockedRegex;

    public CustomTagTextFilter(
            boolean enabled,
            boolean ignoreCase,
            boolean stripColorsBeforeCheck,
            boolean stripMiniMessageBeforeCheck,
            List<String> blockedWords,
            List<String> blockedRegex
    ) {
        this.enabled = enabled;
        this.ignoreCase = ignoreCase;
        this.stripColorsBeforeCheck = stripColorsBeforeCheck;
        this.stripMiniMessageBeforeCheck = stripMiniMessageBeforeCheck;
        this.blockedWords = normalizeWords(blockedWords);
        this.blockedRegex = compileRegex(blockedRegex);
    }

    public Optional<String> findViolation(String input) {
        if (!enabled) {
            return Optional.empty();
        }

        String normalized = normalize(input);
        for (String blockedWord : blockedWords) {
            if (!blockedWord.isEmpty() && normalized.contains(blockedWord)) {
                return Optional.of(blockedWord);
            }
        }

        for (Pattern pattern : blockedRegex) {
            if (pattern.matcher(normalized).find()) {
                return Optional.of(pattern.pattern());
            }
        }

        return Optional.empty();
    }

    public String normalize(String input) {
        String normalized = input != null ? input : "";
        if (stripColorsBeforeCheck) {
            normalized = stripLegacyColors(normalized);
        }
        if (stripMiniMessageBeforeCheck) {
            normalized = MINIMESSAGE_PATTERN.matcher(normalized).replaceAll("");
        }
        return ignoreCase ? normalized.toLowerCase(Locale.ROOT) : normalized;
    }

    private List<String> normalizeWords(List<String> words) {
        if (words == null || words.isEmpty()) {
            return List.of();
        }

        List<String> normalizedWords = new ArrayList<>();
        for (String word : words) {
            String normalized = normalize(word).trim();
            if (!normalized.isEmpty()) {
                normalizedWords.add(normalized);
            }
        }
        return List.copyOf(normalizedWords);
    }

    private List<Pattern> compileRegex(List<String> regexList) {
        if (regexList == null || regexList.isEmpty()) {
            return List.of();
        }

        int flags = ignoreCase ? Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE : 0;
        List<Pattern> patterns = new ArrayList<>();
        for (String regex : regexList) {
            if (regex == null || regex.isBlank()) {
                continue;
            }
            try {
                patterns.add(Pattern.compile(regex, flags));
            } catch (PatternSyntaxException ignored) {
            }
        }
        return List.copyOf(patterns);
    }

    private String stripLegacyColors(String input) {
        String stripped = BUKKIT_HEX_COLOR_PATTERN.matcher(input).replaceAll("");
        stripped = HEX_COLOR_PATTERN.matcher(stripped).replaceAll("");
        return BASIC_COLOR_PATTERN.matcher(stripped).replaceAll("");
    }
}
