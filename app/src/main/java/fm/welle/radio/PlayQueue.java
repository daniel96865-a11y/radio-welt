package fm.welle.radio;

import java.util.ArrayList;
import java.util.List;

/** In-memory playlist context for Previous / Next on the player screen. */
public final class PlayQueue {
    private static final List<Station> stations = new ArrayList<>();
    private static int index = -1;

    private PlayQueue() {}

    public static synchronized void set(List<Station> list, Station current) {
        stations.clear();
        index = -1;
        if (list == null || list.isEmpty()) {
            if (current != null) {
                stations.add(current);
                index = 0;
            }
            return;
        }
        for (Station station : list) {
            if (station != null && station.id != null && !station.id.isEmpty()) {
                stations.add(station);
            }
        }
        if (current != null) {
            for (int i = 0; i < stations.size(); i++) {
                if (current.id.equals(stations.get(i).id)) {
                    index = i;
                    return;
                }
            }
            stations.add(0, current);
            index = 0;
        } else if (!stations.isEmpty()) {
            index = 0;
        }
    }

    public static synchronized void setFromRows(List<?> rows, Station current) {
        ArrayList<Station> list = new ArrayList<>();
        if (rows != null) {
            for (Object row : rows) {
                if (row instanceof Station) list.add((Station) row);
            }
        }
        set(list, current);
    }

    public static synchronized void syncTo(Station current) {
        if (current == null || current.id == null) return;
        for (int i = 0; i < stations.size(); i++) {
            if (current.id.equals(stations.get(i).id)) {
                index = i;
                return;
            }
        }
    }

    public static synchronized boolean hasPrevious() {
        return index > 0;
    }

    public static synchronized boolean hasNext() {
        return index >= 0 && index < stations.size() - 1;
    }

    public static synchronized Station previous() {
        if (!hasPrevious()) return null;
        index--;
        return stations.get(index);
    }

    public static synchronized Station next() {
        if (!hasNext()) return null;
        index++;
        return stations.get(index);
    }

    public static synchronized Station peek() {
        if (index < 0 || index >= stations.size()) return null;
        return stations.get(index);
    }

    public static synchronized int size() {
        return stations.size();
    }
}
