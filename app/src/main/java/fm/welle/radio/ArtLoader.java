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
import java.util.function.Function;

public class ArtLoader {
    private static File diskDir;
    private static final ExecutorService IO = Executors.newFixedThreadPool(4);
    private static final Handler UI = new Handler(Looper.getMainLooper());
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(8192) {
                @Override
        public int sizeOf(String str, Bitmap bitmap) {
            return Math.max(1, bitmap.getByteCount() / 1024);
        }
    };
    private static final Set<String> FAIL = Collections.newSetFromMap(new ConcurrentHashMap());
    private static final Set<String> LOADING = Collections.newSetFromMap(new ConcurrentHashMap());
    private static final Map<String, Set<ImageView>> WAIT = new ConcurrentHashMap();

    public static void init(Context context) {
        if (diskDir != null) {
            return;
        }
        File file = new File(context.getApplicationContext().getCacheDir(), "art");
        diskDir = file;
        file.mkdirs();
    }

    public static void bind(ImageView imageView, String str, String str2, Theme theme) {
        Set<ImageView> set;
        final String trim = str == null ? "" : str.trim();
        Object tag = imageView.getTag(R.id.cover);
        if ((tag instanceof String) && !((String) tag).equals(trim) && (set = WAIT.get(tag)) != null) {
            set.remove(imageView);
        }
        imageView.setTag(R.id.cover, trim);
        Bitmap bitmap = trim.isEmpty() ? null : CACHE.get(trim);
        if (bitmap != null) {
            imageView.setImageBitmap(bitmap);
            return;
        }
        imageView.setImageBitmap(monogram(str2, theme));
        if (trim.isEmpty() || FAIL.contains(trim)) {
            return;
        }
        if (trim.startsWith("http://") || trim.startsWith("https://")) {
            WAIT.computeIfAbsent(trim, new Function() {
                @Override
                public final Object apply(Object obj) {
                    Set newSetFromMap;
                    newSetFromMap = Collections.newSetFromMap(new ConcurrentHashMap());
                    return newSetFromMap;
                }
            }).add(imageView);
            if (LOADING.add(trim)) {
                IO.execute(new Runnable() {
                    @Override
                    public final void run() {
                        ArtLoader.lambda$bind$2(trim);
                    }
                });
            }
        }
    }

    static /* synthetic */ void lambda$bind$2(final String str) {
        Bitmap bmp = fromDisk(str);
        if (bmp == null) {
            bmp = download(str);
        }
        if (bmp != null) {
            CACHE.put(str, bmp);
            toDisk(str, bmp);
        } else {
            FAIL.add(str);
        }
        LOADING.remove(str);
        final Bitmap result = bmp;
        UI.post(new Runnable() {
            @Override
            public final void run() {
                ArtLoader.lambda$bind$1(str, result);
            }
        });
    }

    static /* synthetic */ void lambda$bind$1(String str, Bitmap bitmap) {
        Set<ImageView> remove = WAIT.remove(str);
        if (bitmap == null || remove == null) {
            return;
        }
        for (ImageView imageView : remove) {
            if (str.equals(imageView.getTag(R.id.cover))) {
                imageView.setImageBitmap(bitmap);
            }
        }
    }

    public static Bitmap peek(String str) {
        String trim = str == null ? "" : str.trim();
        if (trim.isEmpty()) {
            return null;
        }
        Bitmap bitmap = CACHE.get(trim);
        return bitmap != null ? bitmap : fromDisk(trim);
    }

    public static void load(String str, final Consumer<Bitmap> consumer) {
        final String trim = str == null ? "" : str.trim();
        final Bitmap peek = peek(trim);
        if (peek != null) {
            UI.post(new Runnable() {
                @Override
                public final void run() {
                    consumer.accept(peek);
                }
            });
            return;
        }
        if (trim.isEmpty() || FAIL.contains(trim)) {
            UI.post(new Runnable() {
                @Override
                public final void run() {
                    consumer.accept(null);
                }
            });
        } else if (LOADING.add(trim)) {
            IO.execute(new Runnable() {
                @Override
                public final void run() {
                    ArtLoader.lambda$load$6(trim, consumer);
                }
            });
        }
    }

    static /* synthetic */ void lambda$load$6(String str, final Consumer consumer) {
        Bitmap bmp = fromDisk(str);
        if (bmp == null) {
            bmp = download(str);
        }
        if (bmp != null) {
            CACHE.put(str, bmp);
            toDisk(str, bmp);
        } else {
            FAIL.add(str);
        }
        LOADING.remove(str);
        final Bitmap result = bmp;
        UI.post(new Runnable() {
            @Override
            public final void run() {
                consumer.accept(result);
            }
        });
    }

    public static Bitmap monogram(String str, Theme theme) {
        Bitmap createBitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(createBitmap);
        Paint paint = new Paint(1);
        paint.setColor(theme.chip);
        float f = 128;
        canvas.drawRoundRect(0.0f, 0.0f, f, f, 28.0f, 28.0f, paint);
        paint.setColor(theme.accent);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create(Typeface.SERIF, 2));
        paint.setTextSize(64.0f);
        String upperCase = (str == null || str.isEmpty()) ? "R" : str.substring(0, 1).toUpperCase();
        Paint.FontMetrics fontMetrics = paint.getFontMetrics();
        float f2 = f / 2.0f;
        canvas.drawText(upperCase, f2, f2 - ((fontMetrics.ascent + fontMetrics.descent) / 2.0f), paint);
        return createBitmap;
    }

    private static File diskFile(String str) {
        if (diskDir == null) {
            return null;
        }
        return new File(diskDir, Integer.toHexString(str.hashCode()) + "_" + str.length() + ".png");
    }

    private static Bitmap fromDisk(String str) {
        File diskFile = diskFile(str);
        if (diskFile != null && diskFile.isFile()) {
            try {
                return BitmapFactory.decodeFile(diskFile.getAbsolutePath());
            } catch (Exception unused) {
            }
        }
        return null;
    }

    private static void toDisk(String str, Bitmap bitmap) {
        File diskFile = diskFile(str);
        if (diskFile == null) {
            return;
        }
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(diskFile);
            try {
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, fileOutputStream);
                fileOutputStream.close();
            } finally {
            }
        } catch (Exception unused) {
        }
    }
    private static Bitmap download(String str) {
        HttpURLConnection httpURLConnection = null;
        try {
            httpURLConnection = (HttpURLConnection) new URL(str).openConnection();
            httpURLConnection.setConnectTimeout(4000);
            httpURLConnection.setReadTimeout(4000);
            httpURLConnection.setInstanceFollowRedirects(true);
            httpURLConnection.setRequestProperty("User-Agent", "WelleRadio/1.2 (Android)");
            if (httpURLConnection.getResponseCode() >= 400) {
                return null;
            }
            String valueOf = String.valueOf(httpURLConnection.getContentType());
            if (valueOf.contains("svg") || valueOf.contains("xml")) {
                return null;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            return BitmapFactory.decodeStream(httpURLConnection.getInputStream(), null, options);
        } catch (Exception unused) {
            return null;
        } finally {
            if (httpURLConnection != null) {
                httpURLConnection.disconnect();
            }
        }
    }
}
