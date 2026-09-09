package fm.welle.radio;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class Featured {
    private static final String LOGO = "https://cdn-radiotime-logos.tunein.com/s55002q.png";

    public static List<Station> techno4ever() {
        ArrayList arrayList = new ArrayList();
        arrayList.add(ch("t4e-main", "TECHNO4EVER Main", "club", "Dance & Hands Up"));
        arrayList.add(ch("t4e-hard", "TECHNO4EVER Hard", "harder", "Hardstyle & Hardcore"));
        arrayList.add(ch("t4e-club", "TECHNO4EVER Club", "house", "House & Trance"));
        arrayList.add(ch("t4e-lounge", "TECHNO4EVER Lounge", "lounge", "Chill & Trance"));
        arrayList.add(ch("t4e-techno", "TECHNO4EVER Techno", "techno", "Peak Time Techno"));
        arrayList.add(ch("t4e-happy", "TECHNO4EVER Happy Hardcore", "happyhardcore", "Happy Hardcore"));
        return arrayList;
    }

    public static boolean matchesSearch(String str) {
        if (str == null) {
            return false;
        }
        String replace = str.toLowerCase(Locale.ROOT).replace(" ", "");
        return replace.contains("techno4ever") || replace.contains("t4e") || replace.contains("techno4") || replace.equals("hard") || replace.contains("hardstyle");
    }

    private static Station ch(String str, String str2, String str3, String str4) {
        return Station.local(str, str2, "https://streams.rautemusik.fm/" + str3 + "/mp3-192", str4, LOGO);
    }
}
