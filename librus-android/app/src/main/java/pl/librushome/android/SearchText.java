package pl.librushome.android;

import java.text.Normalizer;
import java.util.Locale;

/** Literal, case/diacritic-insensitive words; never interprets the query as a regex. */
public final class SearchText {
    private SearchText() { }
    public static String normalize(String text) {
        return Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT).replace('ł', 'l').trim();
    }
    public static String[] words(String query) {
        String value = normalize(query);
        return value.isEmpty() ? new String[0] : value.split("\\s+");
    }
    public static boolean matches(String[] words, String text) {
        String value = normalize(text);
        for (String word : words) if (!value.contains(word)) return false;
        return true;
    }
}
