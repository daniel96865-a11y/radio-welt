package fm.welle.radio;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.File;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class ArtLoader {
    private static File diskDir;
    private static final ExecutorService IO = Executors.newFixedThreadPool(4);
    private static final Handler UI = new Handler(Looper.getMainLooper());
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(8192) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return Math.max(1, value.getByteCount() / 1024);
        }
    };
    private static final Set<String> FAIL = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Set<String> LOADING = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Map<String, Set<ImageView>> WAIT = new ConcurrentHashMap<>();

    public static void init(Context context) {
        if (diskDir != null) return;
        diskDir = new File(context.getApplicationContext().getCacheDir(), "art");
        diskDir.mkdirs();
    }

    public static void bind(ImageView imageView, String url, String name, Theme theme) {
        final String key = url == null ? "" : url.trim();
        Object prev = imageView.getTag(R.id.cover);
        if (prev instanceof String && !prev.equals(key)) {
            Set<ImageView> set = WAIT.get(prev);
            if (set != null) set.remove(imageView);
        }
        imageView.setTag(R.id.cover, key);
        Bitmap cached = key.isEmpty() ? null : CACHE.get(key);
        if (cached != null) {
            imageView.setImageBitmap(cached);
            return;
        }
        imageView.setImageBitmap(monogram(name, theme));
        if (key.isEmpty() || FAIL.contains(key)) return;
        if (!(key.startsWith("http://") || key.startsWith("https://"))) return;

        WAIT.computeIfAbsent(key, k -> Collections.newSetFromMap(new ConcurrentHashMap<>())).add(imageView);
        if (LOADING.add(key)) {
            IO.execute(() -> fetchAndDispatch(key));
        }
    }

    private static void fetchAndDispatch(String key) {
        Bitmap bmp = fromDisk(key);
        if (bmp == null) bmp = download(key);
        if (bmp != null) {
            CACHE.put(key, bmp);
            toDisk(key, bmp);
        } else {
            FAIL.add(key);
        }
        LOADING.remove(key);
        final Bitmap result = bmp;
        UI.post(() -> {
            Set<ImageView> views = WAIT.remove(key);
            if (result == null || views == null) return;
            for (ImageView iv : views) {
                if (key.equals(iv.getTag(R.id.cover))) {
                    iv.setImageBitmap(result);
                }
            }
        });
    }

    public static Bitmap peek(String url) {
        String key = url == null ? "" : url.trim();
        if (key.isEmpty()) return null;
        Bitmap cached = CACHE.get(key);
        return cached != null ? cached : fromDisk(key);
    }

    public static void load(String url, Consumer<Bitmap> consumer) {
        final String key = url == null ? "" : url.trim();
        Bitmap peek = peek(key);
        if (peek != null) {
            UI.post(() -> consumer.accept(peek));
            return;
        }
        if (key.isEmpty() || FAIL.contains(key)) {
            UI.post(() -> consumer.accept(null));
            return;
        }
        if (LOADING.add(key)) {
            IO.execute(() -> {
                Bitmap bmp = fromDisk(key);
                if (bmp == null) bmp = download(key);
                if (bmp != null) {
                    CACHE.put(key, bmp);
                    toDisk(key, bmp);
                } else {
                    FAIL.add(key);
                }
                LOADING.remove(key);
                final Bitmap result = bmp;
                UI.post(() -> consumer.accept(result));
            });
        }
    }

    public static Bitmap monogram(String name, Theme theme) {
        Bitmap bmp = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(theme.chip);
        canvas.drawRoundRect(0, 0, 128, 128, 28, 28, paint);
        paint.setColor(theme.accent);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create(Typeface.SERIF, Typeface.ITALIC));
        paint.setTextSize(64f);
        String letter = (name == null || name.isEmpty()) ? "R" : name.substring(0, 1).toUpperCase();
        Paint.FontMetrics fm = paint.getFontMetrics();
        canvas.drawText(letter, 64f, 64f - ((fm.ascent + fm.descent) / 2f), paint);
        return bmp;
    }

    private static File diskFile(String key) {
        if (diskDir == null) return null;
        return new File(diskDir, Integer.toHexString(key.hashCode()) + "_" + key.length() + ".png");
    }

    private static Bitmap fromDisk(String key) {
        File f = diskFile(key);
        if (f != null && f.isFile()) {
            try {
                return BitmapFactory.decodeFile(f.getAbsolutePath());
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static void toDisk(String key, Bitmap bitmap) {
        File f = diskFile(key);
        if (f == null) return;
        try (FileOutputStream out = new FileOutputStream(f)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, out);
        } catch (Exception ignored) {
        }
    }

    private static Bitmap download(String url) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "WelleRadio/1.2 (Android)");
            if (conn.getResponseCode() >= 400) return null;
            String type = String.valueOf(conn.getContentType());
            if (type.contains("svg") || type.contains("xml")) return null;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            return BitmapFactory.decodeStream(conn.getInputStream(), null, opts);
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
