package com.candlebot;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.PointF;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

/**
 * CandleBotAccessibilityService
 *
 * Ce service Android utilise l'API AccessibilityService pour effectuer
 * de vrais clics système sur l'écran — même dans d'autres applications.
 *
 * Stratégie de clic :
 * 1. Cherche un nœud d'interface avec le texte "Buy", "Acheter", "Long", etc.
 * 2. Si trouvé → performAction(ACTION_CLICK) directement sur ce nœud
 * 3. Sinon → cherche par description de contenu
 * 4. Sinon → clic de secours au centre-bas de l'écran (position typique du bouton Buy)
 */
public class CandleBotAccessibilityService extends AccessibilityService {

    private static final String TAG = "CandleBotA11y";
    private static CandleBotAccessibilityService instance;

    // Mots-clés pour trouver le bouton Buy (insensible à la casse)
    private static final String[] BUY_KEYWORDS = {
        "buy", "acheter", "achat", "long", "entrer", "enter",
        "open", "ouvrir", "place order", "passer ordre", "confirm"
    };

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.d(TAG, "AccessibilityService connecté");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // On n'a pas besoin d'écouter les events pour cette app
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "AccessibilityService interrompu");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
    }

    public static CandleBotAccessibilityService getInstance() {
        return instance;
    }

    /**
     * Méthode principale — appelée par MainActivity quand le seuil est atteint.
     * Tente d'abord de cliquer sur un bouton Buy identifié dans l'UI,
     * puis retombe sur un clic gestuel à une position prédéfinie.
     */
    public void performBuyClick() {
        Log.d(TAG, "performBuyClick() appelé");

        // Essai 1 : trouver le bouton par son texte dans l'arbre d'accessibilité
        if (tryClickByText()) return;

        // Essai 2 : trouver par description de contenu
        if (tryClickByContentDescription()) return;

        // Essai 3 : clic gestuel au centre-bas (position courante du bouton Buy)
        performFallbackGestureClick();
    }

    private boolean tryClickByText() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;

        for (String keyword : BUY_KEYWORDS) {
            // CherchE par texte exact
            List<AccessibilityNodeInfo> nodes =
                root.findAccessibilityNodeInfosByText(keyword);
            if (nodes != null && !nodes.isEmpty()) {
                for (AccessibilityNodeInfo node : nodes) {
                    if (node.isEnabled() && node.isClickable()) {
                        boolean clicked = node.performAction(
                            AccessibilityNodeInfo.ACTION_CLICK);
                        Log.d(TAG, "Clic sur nœud texte '" + keyword + "': " + clicked);
                        if (clicked) { root.recycle(); return true; }
                    }
                    // Essaie le parent si le nœud lui-même n'est pas cliquable
                    AccessibilityNodeInfo parent = node.getParent();
                    if (parent != null && parent.isClickable()) {
                        boolean clicked = parent.performAction(
                            AccessibilityNodeInfo.ACTION_CLICK);
                        Log.d(TAG, "Clic sur parent du nœud '" + keyword + "': " + clicked);
                        if (clicked) { root.recycle(); return true; }
                    }
                }
            }
        }
        root.recycle();
        return false;
    }

    private boolean tryClickByContentDescription() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;

        boolean found = searchAndClickNode(root);
        root.recycle();
        return found;
    }

    private boolean searchAndClickNode(AccessibilityNodeInfo node) {
        if (node == null) return false;

        CharSequence desc = node.getContentDescription();
        CharSequence text = node.getText();

        String combined = ((desc != null ? desc.toString() : "") + " " +
                           (text != null ? text.toString() : "")).toLowerCase();

        for (String kw : BUY_KEYWORDS) {
            if (combined.contains(kw) && node.isEnabled()) {
                if (node.isClickable()) {
                    boolean clicked = node.performAction(
                        AccessibilityNodeInfo.ACTION_CLICK);
                    if (clicked) return true;
                }
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (searchAndClickNode(child)) return true;
        }
        return false;
    }

    /**
     * Clic gestuel de secours — Android 7.0+
     * Clique au centre-bas de l'écran (où se trouve souvent le bouton Buy).
     * L'utilisateur peut ajuster les coordonnées X/Y ici.
     */
    private void performFallbackGestureClick() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.w(TAG, "GestureDescription requiert Android 7+");
            return;
        }

        PointF clickPoint = getBuyButtonPosition();
        Log.d(TAG, "Clic gestuel de secours à: " + clickPoint.x + ", " + clickPoint.y);

        Path clickPath = new Path();
        clickPath.moveTo(clickPoint.x, clickPoint.y);

        GestureDescription.StrokeDescription stroke =
            new GestureDescription.StrokeDescription(clickPath, 0, 50);

        GestureDescription gesture = new GestureDescription.Builder()
            .addStroke(stroke)
            .build();

        dispatchGesture(gesture, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                Log.d(TAG, "Clic gestuel effectué avec succès");
            }
            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                Log.w(TAG, "Clic gestuel annulé");
            }
        }, null);
    }

    /**
     * Calcule la position du bouton Buy.
     * Par défaut : centre horizontal, 85% de la hauteur (bas de l'écran).
     * Modifiable par l'utilisateur dans les paramètres.
     */
    private PointF getBuyButtonPosition() {
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager wm = (WindowManager)
            getSystemService(WINDOW_SERVICE);
        if (wm != null) {
            wm.getDefaultDisplay().getRealMetrics(metrics);
        }

        // Position par défaut : centre-bas
        // L'utilisateur peut changer ces valeurs selon son app de trading
        float x = metrics.widthPixels * 0.75f;  // côté droit (Buy est souvent à droite)
        float y = metrics.heightPixels * 0.85f; // bas de l'écran

        return new PointF(x, y);
    }
}
