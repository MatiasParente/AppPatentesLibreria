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
            // Pre-procesamiento: Reescalado inteligente
            int targetW = Math.min(plateCrop.getWidth() * 2, 900);
            float ratio = (float) targetW / plateCrop.getWidth();
            int targetH = (int) (plateCrop.getHeight() * ratio);

            Bitmap base = Bitmap.createScaledBitmap(plateCrop, targetW, targetH, true)
                    .copy(Bitmap.Config.ARGB_8888, false);

            // 11 versiones con los filtros de PlateFilter
            Bitmap[] versions = {
                    base,                                                                 // 1. Original
                    PlateFilter.aplicarSharpen(base),                            // 2. Sharpen
                    PlateFilter.corregirPerspectiva(base),                       // 3. Estiramiento Horizontal
                    PlateFilter.corregirArriba(base),                             // 4. Estiramiento Vertical
                    PlateFilter.plancharPatenteLadoDerecho(base),      // 5. plancha derecha
                    PlateFilter.aplicarSharpen(PlateFilter.plancharPatenteLadoDerecho(base)), // 6. sharpen + derecha
                    PlateFilter.ajustarBrilloContraste(base, 2.0f, -50f),
                    PlateFilter.plancharPatenteLadoIzquierdo(base),// 7. Binarización suave
                    PlateFilter.aplicarSharpen(PlateFilter.plancharPatenteLadoIzquierdo(base)), // 8. sharpen + izquierda
                    PlateFilter.aplicarSharpen(PlateFilter.corregirArriba(base)), // 9. sharpen+ arriba
                    PlateFilter.plancharEsquinaSuperiorDerecha(base),
                    PlateFilter.plancharEsquinaSuperiorIzquierda(base),
                    PlateFilter.aplicarSharpen(PlateFilter.plancharEsquinaSuperiorDerecha(base)),
                    PlateFilter.aplicarSharpen(PlateFilter.plancharEsquinaSuperiorIzquierda(base)),
                    PlateFilter.corregirBrilloMotos(base)                                 // 10. Filtro Motos
            };

            Map<String, Integer> votes = new HashMap<>();
            AtomicInteger completedFrames = new AtomicInteger(0);

            for (int i = 0; i < versions.length; i++) {
                Bitmap bmp = versions[i];

                boolean esVersionTorcida = i >= 2;

                Bitmap bmpFinal;
                if (esVersionTorcida) {
                    bmpFinal = bmp; // Mantenemos la imagen estirada completa para no perder bordes
                } else {
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