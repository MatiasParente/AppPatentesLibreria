package com.example.lectorpatentes.ocr.internal;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.example.lectorpatentes.ocr.ScannerCallback;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class PlateRecognizer {

    private final Context context;
    private final TextRecognizer mlkitRecognizer;
    private final ExecutorService executor;

    public PlateRecognizer(Context context) {
        this.context = context;
        this.mlkitRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        this.executor = Executors.newSingleThreadExecutor();
    }

    public void analyze(Bitmap plateCrop, ScannerCallback callback) {
        executor.execute(() -> {
            androidx.renderscript.RenderScript rs = androidx.renderscript.RenderScript.create(context);
            // 1. Pre-procesamiento: Reescalado inteligente (Máximo 900px de ancho)
            int targetW = Math.min(plateCrop.getWidth() * 2, 600);
            float ratio = (float) targetW / plateCrop.getWidth();
            int targetH = (int) (plateCrop.getHeight() * ratio);

            Bitmap base = Bitmap.createScaledBitmap(plateCrop, targetW, targetH, true)
                    .copy(Bitmap.Config.ARGB_8888, false);


            try {

                Bitmap[] versions = {
                        base,
                        PlateFilter.aplicarSharpen(rs, base),                                                                // 1. Original
                    PlateFilter.aplicarSharpen(rs, base),                            // 2. Sharpen
                    PlateFilter.corregirPerspectivaSimulada(base),                       // 3. Estiramiento Horizontal
                    PlateFilter.corregirPicadoCenital(base),                             // 4. Estiramiento Vertical
                    PlateFilter.plancharPatenteExtremadamenteDoblada(rs, base),      // 5. MODO EXTREMO
                    PlateFilter.aplicarSharpen(rs, PlateFilter.plancharPatenteExtremadamenteDoblada(rs, base)), // 6. Combo Extremo
                    PlateFilter.ajustarBrilloContraste(base, 2.0f, -50f),
                    PlateFilter.plancharPatenteLadoIzquierdo(rs, base),// 7. Binarización suave
                    PlateFilter.aplicarSharpen(rs, PlateFilter.corregirPicadoCenital(base)), // 8. Combo Cenital
                    PlateFilter.corregirBrilloMotos(base)                                 // 9. Filtro Motos/Contraste fuerte
            };

            Map<String, Integer> votes = new HashMap<>();
            AtomicInteger completedFrames = new AtomicInteger(0);

            for (int i = 0; i < versions.length; i++) {
                Bitmap bmp = versions[i];

                // Si es la versión 3, 4, 5, 6 u 8 (las de perspectiva/planchado),
                // NO aplicamos recorte para no comer letras en los bordes.
                boolean esVersionTorcida = (i == 2 || i == 3 || i == 4 || i == 5 || i == 7);

                Bitmap bmpFinal;
                if (esVersionTorcida) {
                    bmpFinal = bmp; // Usamos la imagen completa
                } else {
                    // Recorte solo para fotos frontales
                    int marginW = (int)(bmp.getWidth() * 0.05);
                    int marginTop = (int)(bmp.getHeight() * 0.12);
                    int marginBot = (int)(bmp.getHeight() * 0.05);
                    int finalW = bmp.getWidth() - (marginW * 2);
                    int finalH = bmp.getHeight() - marginTop - marginBot;

                    if (finalW > 0 && finalH > 0) {
                        bmpFinal = Bitmap.createBitmap(bmp, marginW, marginTop, finalW, finalH);
                    } else {
                        bmpFinal = bmp;
                    }
                }

                InputImage image = InputImage.fromBitmap(bmpFinal, 0);
                mlkitRecognizer.process(image)
                        .addOnSuccessListener(visionText -> {
                            String result = PlateValidator.extraerPatente(visionText);
                            if (!result.isEmpty()) {
                                synchronized (votes) {
                                    votes.put(result, votes.getOrDefault(result, 0) + 1);
                                }
                            }
                            checkFinal(completedFrames, versions.length, votes, plateCrop, callback, versions);
                        })
                        .addOnFailureListener(e -> {
                            checkFinal(completedFrames, versions.length, votes, plateCrop, callback, versions);
                        });
            }
            } finally {
                // Esto se ejecuta SIEMPRE, incluso si hay error.
                // Es lo que va a evitar que el TCL explote al sexto tiro.
                rs.destroy();
            }
        });
    }

    private void checkFinal(AtomicInteger completed, int total, Map<String, Integer> votes, Bitmap crop, ScannerCallback callback, Bitmap[] versionsToRecycle) {

        if (completed.incrementAndGet() == total) {
            String bestPlate = "";
            int maxVotes = 0;

            if (!votes.isEmpty()) {
                for (Map.Entry<String, Integer> entry : votes.entrySet()) {
                    if (entry.getValue() > maxVotes) {
                        maxVotes = entry.getValue();
                        bestPlate = entry.getKey();
                    }
                }
            }

            String confidence = PlateValidator.calcularConfianza(bestPlate, maxVotes);

            // --- LA LIMPIEZA MÁGICA PARA EL TCL ---
            for (Bitmap b : versionsToRecycle) {
                if (b != null && !b.isRecycled() && b != crop) {
                    try {
                        b.recycle();
                    } catch (Exception e) {
                        Log.e("OCR", "No se pudo reciclar un bitmap: " + e.getMessage());
                    }
                }
            }

            callback.onResult(bestPlate.isEmpty() ? null : bestPlate, confidence, crop);
        }
    }

    public void close() {
        mlkitRecognizer.close();
        executor.shutdown();
    }
}