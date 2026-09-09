package fm.welle.radio;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class Featured {
    private static final String LOGO = "https://cdn-radiotime-logos.tunein.com/s55002q.png";

    public static List<Station> techno4ever() {
        ArrayList<Station> list = new ArrayList<>();
        list.add(ch("t4e-main", "TECHNO4EVER Main", "club", "Dance & Hands Up"));
        list.add(ch("t4e-hard", "TECHNO4EVER Hard", "harder", "Hardstyle & Hardcore"));
        list.add(ch("t4e-club", "TECHNO4EVER Club", "house", "House & Trance"));
        list.add(ch("t4e-lounge", "TECHNO4EVER Lounge", "lounge", "Chill & Trance"));
        list.add(ch("t4e-techno", "TECHNO4EVER Techno", "techno", "Peak Time Techno"));
        list.add(ch("t4e-happy", "TECHNO4EVER Happy Hardcore", "happyhardcore", "Happy Hardcore"));
        return list;
    }

    public static boolean matchesSearch(String q) {
        if (q == null) return false;
        String s = q.toLowerCase(Locale.ROOT).replace(" ", "");
        return s.contains("techno4ever") || s.contains("t4e") || s.contains("techno4")
                || s.equals("hard") || s.contains("hardstyle");
    }

    private static Station ch(String id, String name, String path, String genre) {
        return Station.local(id, name, "https://streams.rautemusik.fm/" + path + "/mp3-192", genre, LOGO);
    }
}
