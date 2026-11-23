package fm.welle.radio;

import android.app.Activity;
import android.view.View;
import android.view.Window;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/**
 * Edge-to-edge für Android 15/16 (targetSdk 35+): Das System zeichnet die App dort immer
 * unter Status- und Navigationsleiste. Wir schalten es deshalb auf allen Versionen gleich
 * ein und geben dem Wurzel-Layout genau so viel Innenabstand, wie die Leisten, die
 * Display-Aussparung (Kamera) und die Tastatur brauchen. Der Hintergrund des Wurzel-Layouts
 * füllt dabei weiter den ganzen Bildschirm, also auch die Fläche hinter den Leisten.
 */
final class SystemBars {
    private SystemBars() { }

    static void edgeToEdge(Activity activity, View root) {
        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);
        final int left = root.getPaddingLeft();
        final int top = root.getPaddingTop();
        final int right = root.getPaddingRight();
        final int bottom = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(left + bars.left, top + bars.top, right + bars.right,
                    bottom + Math.max(bars.bottom, ime.bottom));
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(root);
    }

    /** Helle Symbole auf dunklem Grund bzw. dunkle Symbole auf hellem Grund. */
    static void style(Window window, int background, boolean lightBackground) {
        // Ab Android 15 ignoriert das System die Leistenfarbe (transparente Leisten);
        // darunter bleibt die Farbe als Fallback gesetzt.
        window.setStatusBarColor(background);
        window.setNavigationBarColor(background);
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(window, window.getDecorView());
        controller.setAppearanceLightStatusBars(lightBackground);
        controller.setAppearanceLightNavigationBars(lightBackground);
    }
}
