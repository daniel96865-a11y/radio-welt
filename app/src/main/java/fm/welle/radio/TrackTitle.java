package fm.welle.radio;

import java.util.Locale;

/** Cleans a stream's now-playing title before it is shown in the player. */
public final class TrackTitle {
    private TrackTitle() {
    }

    public static String clean(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.trim();
        if (text.length() >= 2) {
            char start = text.charAt(0);
            char end = text.charAt(text.length() - 1);
            if ((start == '\'' && end == '\'') || (start == '"' && end == '"')) {
                text = text.substring(1, text.length() - 1).trim();
            }
        }
        text = text.replace('\u0000', ' ').replaceAll("\\s+", " ").trim();
        if (text.isEmpty()) {
            return "";
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.equals("unknown") || lower.equals("null") || lower.equals("-")
                || lower.equals("n/a") || lower.equals("advert") || lower.equals("ad")) {
            return "";
        }
        if (text.startsWith("http://") || text.startsWith("https://")) {
            return "";
        }
        if (text.length() > 140) {
            text = text.substring(0, 140).trim();
        }
        return text;
    }
}
