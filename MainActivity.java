package com.candlebot;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "CandleBot";
    private static final int CAMERA_PERMISSION_REQUEST = 100;

    private PreviewView previewView;
    private TextView tvStatus, tvRedCount, tvBuyCount, tvLog;
    private Button btnStart, btnStop, btnAccessibility;
    private SeekBar seekThreshold, seekSensitivity;
    private TextView tvThresholdVal, tvSensitivityVal;

    private ExecutorService cameraExecutor;
    private boolean isAnalysing = false;
    private int redCandleCount = 0;
    private int buyTriggerCount = 0;
    private int threshold = 10;
    private int sensitivity = 3;
    private long lastTriggerTime = 0;
    private static final long COOLDOWN_MS = 3000;

    // Communication avec l'AccessibilityService
    public static MainActivity instance;
    public static boolean pendingClick = false;
    public static float clickX = 0, clickY = 0;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance = this;
        setContentView(R.layout.activity_main);

        initViews();
        setupSeekBars();
        setupButtons();

        if (checkCameraPermission()) {
            startCamera();
        } else {
            requestCameraPermission();
        }
    }

    private void initViews() {
        previewView    = findViewById(R.id.previewView);
        tvStatus       = findViewById(R.id.tvStatus);
        tvRedCount     = findViewById(R.id.tvRedCount);
        tvBuyCount     = findViewById(R.id.tvBuyCount);
        tvLog          = findViewById(R.id.tvLog);
        btnStart       = findViewById(R.id.btnStart);
        btnStop        = findViewById(R.id.btnStop);
        btnAccessibility = findViewById(R.id.btnAccessibility);
        seekThreshold  = findViewById(R.id.seekThreshold);
        seekSensitivity = findViewById(R.id.seekSensitivity);
        tvThresholdVal = findViewById(R.id.tvThresholdVal);
        tvSensitivityVal = findViewById(R.id.tvSensitivityVal);
    }

    private void setupSeekBars() {
        seekThreshold.setMax(19);
        seekThreshold.setProgress(9); // default 10
        tvThresholdVal.setText("10");
        seekThreshold.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean u) {
                threshold = p + 1;
                tvThresholdVal.setText(String.valueOf(threshold));
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });

        seekSensitivity.setMax(4);
        seekSensitivity.setProgress(2); // default 3
        tvSensitivityVal.setText("3");
        seekSensitivity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean u) {
                sensitivity = p + 1;
                tvSensitivityVal.setText(String.valueOf(sensitivity));
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
    }

    private void setupButtons() {
        btnStart.setOnClickListener(v -> {
            isAnalysing = true;
            redCandleCount = 0;
            tvStatus.setText("Analyse en cours...");
            tvStatus.setTextColor(Color.parseColor("#639922"));
            addLog("Analyse démarrée — recherche bougies rouges");
        });

        btnStop.setOnClickListener(v -> {
            isAnalysing = false;
            tvStatus.setText("Arrêté");
            tvStatus.setTextColor(Color.parseColor("#888888"));
            addLog("Analyse arrêtée");
        });

        btnAccessibility.setOnClickListener(v -> {
            if (isAccessibilityServiceEnabled()) {
                addLog("Service accessibilité déjà actif ✓");
                Toast.makeText(this, "Service déjà activé !", Toast.LENGTH_SHORT).show();
            } else {
                Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                startActivity(intent);
                Toast.makeText(this,
                    "Active 'CandleBot - Clic automatique' dans la liste",
                    Toast.LENGTH_LONG).show();
            }
        });
    }

    private void startCamera() {
        cameraExecutor = Executors.newSingleThreadExecutor();
        ListenableFuture<ProcessCameraProvider> future =
            ProcessCameraProvider.getInstance(this);

        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build();

                imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeImage);

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
                provider.unbindAll();
                provider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);

                mainHandler.post(() -> {
                    tvStatus.setText("Caméra prête — appuie sur Démarrer");
                    addLog("Caméra démarrée");
                });

            } catch (Exception e) {
                Log.e(TAG, "Erreur caméra: " + e.getMessage());
                mainHandler.post(() -> addLog("Erreur caméra: " + e.getMessage()));
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void analyzeImage(@NonNull ImageProxy image) {
        if (!isAnalysing) {
            image.close();
            return;
        }

        try {
            Bitmap bitmap = imageProxyToBitmap(image);
            if (bitmap == null) { image.close(); return; }

            int redCandlesInFrame = countRedCandles(bitmap);

            if (redCandlesInFrame > 0) {
                redCandleCount += redCandlesInFrame;
                final int current = redCandleCount;
                final int inFrame = redCandlesInFrame;

                mainHandler.post(() -> {
                    tvRedCount.setText(String.valueOf(current));
                    addLog("Détecté: " + inFrame + " bougie(s) rouge(s)");
                });

                if (redCandleCount >= threshold) {
                    long now = System.currentTimeMillis();
                    if (now - lastTriggerTime > COOLDOWN_MS) {
                        lastTriggerTime = now;
                        final int bCount = ++buyTriggerCount;
                        redCandleCount = 0;
                        mainHandler.post(() -> triggerBuyAction(bCount));
                    }
                }
            }

            bitmap.recycle();
        } catch (Exception e) {
            Log.e(TAG, "Erreur analyse: " + e.getMessage());
        } finally {
            image.close();
        }
    }

    /**
     * Détecte les bougies rouges dans le bitmap.
     * Divise l'image en colonnes verticales et cherche des zones
     * dominées par la couleur rouge (bougies baissières).
     */
    private int countRedCandles(Bitmap bmp) {
        int width = bmp.getWidth();
        int height = bmp.getHeight();
        int cols = 20;
        int colWidth = width / cols;
        int redCandleCount = 0;

        // Paramètres selon la sensibilité
        float redDominance = 1.5f - (sensitivity - 1) * 0.1f; // 1.5 → 1.1
        float minRed = 130f - (sensitivity - 1) * 10f;         // 130 → 90
        float maxGreen = 140f + (sensitivity - 1) * 15f;       // 140 → 200
        float minRatio = 0.05f - (sensitivity - 1) * 0.008f;   // 0.05 → 0.018

        for (int col = 0; col < cols; col++) {
            int x0 = col * colWidth;
            int x1 = Math.min(x0 + colWidth, width);
            int redPixels = 0;
            int greenPixels = 0;
            int total = 0;

            // Échantillonnage (1 pixel sur 4 pour la perf)
            for (int x = x0; x < x1; x += 2) {
                for (int y = 0; y < height; y += 2) {
                    int pixel = bmp.getPixel(x, y);
                    float r = Color.red(pixel);
                    float g = Color.green(pixel);
                    float b = Color.blue(pixel);
                    total++;

                    // Bougie rouge : rouge dominant, pas trop de vert/bleu
                    if (r >= minRed && r > g * redDominance && r > b * redDominance
                            && g < maxGreen && b < maxGreen) {
                        redPixels++;
                    }
                    // Bougie verte : vert dominant
                    if (g > 100 && g > r * 1.4f && g > b * 1.2f) {
                        greenPixels++;
                    }
                }
            }

            if (total == 0) continue;
            float redRatio = (float) redPixels / total;
            float greenRatio = (float) greenPixels / total;

            // C'est une bougie rouge si rouge domine et dépasse le ratio minimum
            if (redRatio > minRatio && redRatio > greenRatio) {
                redCandleCount++;
            }
        }

        return redCandleCount;
    }

    private void triggerBuyAction(int count) {
        tvBuyCount.setText(String.valueOf(count));
        addLog(">>> BUY TRIGGERED ! (" + count + "ème déclenchement)");
        tvStatus.setText("BUY !");
        tvStatus.setTextColor(Color.parseColor("#e24b4a"));

        // Vibration feedback
        try {
            android.os.Vibrator v = (android.os.Vibrator)
                getSystemService(VIBRATOR_SERVICE);
            if (v != null) v.vibrate(300);
        } catch (Exception ignored) {}

        // Déclenche le clic via l'AccessibilityService
        if (isAccessibilityServiceEnabled()) {
            CandleBotAccessibilityService service =
                CandleBotAccessibilityService.getInstance();
            if (service != null) {
                service.performBuyClick();
                addLog("Clic système effectué via AccessibilityService");
            }
        } else {
            addLog("⚠ Active l'AccessibilityService pour les vrais clics !");
        }

        // Remet le statut après 2s
        mainHandler.postDelayed(() -> {
            if (isAnalysing) {
                tvStatus.setText("Analyse en cours...");
                tvStatus.setTextColor(Color.parseColor("#639922"));
            }
        }, 2000);
    }

    private Bitmap imageProxyToBitmap(ImageProxy image) {
        try {
            ImageProxy.PlaneProxy[] planes = image.getPlanes();
            ByteBuffer yBuffer = planes[0].getBuffer();
            ByteBuffer uBuffer = planes[1].getBuffer();
            ByteBuffer vBuffer = planes[2].getBuffer();

            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();

            byte[] nv21 = new byte[ySize + uSize + vSize];
            yBuffer.get(nv21, 0, ySize);
            vBuffer.get(nv21, ySize, vSize);
            uBuffer.get(nv21, ySize + vSize, uSize);

            android.renderscript.RenderScript rs =
                android.renderscript.RenderScript.create(this);
            android.renderscript.ScriptIntrinsicYuvToRGB script =
                android.renderscript.ScriptIntrinsicYuvToRGB.create(
                    rs, android.renderscript.Element.U8_4(rs));

            android.renderscript.Type.Builder yuvType =
                new android.renderscript.Type.Builder(rs,
                    android.renderscript.Element.U8(rs))
                    .setX(nv21.length);
            android.renderscript.Allocation ain =
                android.renderscript.Allocation.createTyped(rs, yuvType.create(),
                    android.renderscript.Allocation.USAGE_SCRIPT);

            android.renderscript.Type.Builder rgbaType =
                new android.renderscript.Type.Builder(rs,
                    android.renderscript.Element.RGBA_8888(rs))
                    .setX(image.getWidth()).setY(image.getHeight());
            android.renderscript.Allocation aout =
                android.renderscript.Allocation.createTyped(rs, rgbaType.create(),
                    android.renderscript.Allocation.USAGE_SCRIPT);

            ain.copyFrom(nv21);
            script.setInput(ain);
            script.forEach(aout);

            Bitmap bmp = Bitmap.createBitmap(
                image.getWidth(), image.getHeight(), Bitmap.Config.ARGB_8888);
            aout.copyTo(bmp);
            rs.destroy();
            return bmp;
        } catch (Exception e) {
            Log.e(TAG, "YUV conversion error: " + e.getMessage());
            return null;
        }
    }

    private boolean isAccessibilityServiceEnabled() {
        String service = getPackageName() + "/" +
            CandleBotAccessibilityService.class.getCanonicalName();
        String enabled = Settings.Secure.getString(
            getContentResolver(),
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) return false;
        TextUtils.SimpleStringSplitter splitter =
            new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabled);
        while (splitter.hasNext()) {
            if (splitter.next().equalsIgnoreCase(service)) return true;
        }
        return false;
    }

    private void addLog(String msg) {
        String current = tvLog.getText().toString();
        String[] lines = current.split("\n");
        StringBuilder sb = new StringBuilder();
        int start = Math.max(0, lines.length - 7);
        for (int i = start; i < lines.length; i++) {
            if (!lines[i].isEmpty()) sb.append(lines[i]).append("\n");
        }
        sb.append("› ").append(msg);
        tvLog.setText(sb.toString());
    }

    private boolean checkCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this,
            new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
            @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST &&
                grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            Toast.makeText(this, "Permission caméra refusée", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        instance = null;
        if (cameraExecutor != null) cameraExecutor.shutdown();
    }
}
