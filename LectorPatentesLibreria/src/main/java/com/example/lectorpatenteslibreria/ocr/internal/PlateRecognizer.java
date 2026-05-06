package com.example.lectorpatenteslibreria.ocr.internal;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import com.example.lectorpatenteslibreria.ocr.ResultPlate;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.example.lectorpatenteslibreria.ocr.ScannerCallback;

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
            int targetW = Math.min(plateCrop.getWidth() * 2, 900);
            float ratio = (float) targetW / plateCrop.getWidth();
            int targetH = (int) (plateCrop.getHeight() * ratio);

            Bitmap base = Bitmap.createScaledBitmap(plateCrop, targetW, targetH, true)
                    .copy(Bitmap.Config.ARGB_8888, false);

            // 18 versiones con los filtros
            Bitmap[] versions = {
                    base,
                    PlateFilter.aplicarSharpen(base),
                    PlateFilter.corregirPerspectiva(base),
                    PlateFilter.corregirArriba(base),
                    PlateFilter.plancharPatenteLadoDerecho(base),
                    PlateFilter.aplicarSharpen(PlateFilter.plancharPatenteLadoDerecho(base)),
                    PlateFilter.ajustarBrilloContraste(base, 2.0f, -50f),
                    PlateFilter.plancharPatenteLadoIzquierdo(base),
                    PlateFilter.aplicarSharpen(PlateFilter.plancharPatenteLadoIzquierdo(base)),
                    PlateFilter.aplicarSharpen(PlateFilter.corregirArriba(base)),
                    PlateFilter.plancharEsquinaSuperiorDerecha(base),
                    PlateFilter.plancharEsquinaSuperiorIzquierda(base),
                    PlateFilter.aplicarSharpen(PlateFilter.plancharEsquinaSuperiorDerecha(base)),
                    PlateFilter.aplicarSharpen(PlateFilter.plancharEsquinaSuperiorIzquierda(base)),
                    PlateFilter.corregirBrilloMotos(base),
                    PlateFilter.aclararImagenOscura(base),
                    PlateFilter.reducirReflejos(base),
                    PlateFilter.filtroSuciedad(base)
            };

            Map<String, Integer> votes = new HashMap<>();
            Map<String, PlateValidator.ValidationResult> validationDetails = new HashMap<>();
            AtomicInteger completedFrames = new AtomicInteger(0);

            for (int i = 0; i < versions.length; i++) {
                Bitmap bmp = versions[i];

                boolean esVersionTorcida = i >= 2;

                Bitmap bmpFinal;
                if (esVersionTorcida) {
                    bmpFinal = bmp; // Mantenemos la imagen estirada completa
                } else {
                    int marginW = (int) (bmp.getWidth() * 0.05);
                    int marginTop = (int) (bmp.getHeight() * 0.12);
                    int marginBot = (int) (bmp.getHeight() * 0.05);

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
                            PlateValidator.ValidationResult vResult = PlateValidator.extraerPatente(visionText);

                            if (vResult != null && !vResult.texto.isEmpty()) {
                                synchronized (votes) {
                                    String patenteDetectada = vResult.texto;
                                    votes.put(patenteDetectada, votes.getOrDefault(patenteDetectada, 0) + 1);

                                    if (!validationDetails.containsKey(patenteDetectada) ||
                                            vResult.cambios < validationDetails.get(patenteDetectada).cambios) {
                                        validationDetails.put(patenteDetectada, vResult);
                                    }
                                }
                            }
                            checkFinal(completedFrames, versions.length, votes, validationDetails, plateCrop, callback, versions);
                        })
                        .addOnFailureListener(e -> {
                            //Si falla, igual cuenta el frame para no congelar la app
                            checkFinal(completedFrames, versions.length, votes, validationDetails, plateCrop, callback, versions);
                        });
            }
        });
    }

    private void checkFinal(AtomicInteger completed, int total, Map<String, Integer> votes,
                            Map<String, PlateValidator.ValidationResult> validationDetails,
                            Bitmap crop, ScannerCallback callback, Bitmap[] versionsToRecycle) {

        if (completed.incrementAndGet() == total) {
            String bestPlate = "";
            int maxVotes = 0;

            // Buscamos la patente ganadora por votos
            for (Map.Entry<String, Integer> entry : votes.entrySet()) {
                if (entry.getValue() > maxVotes) {
                    maxVotes = entry.getValue();
                    bestPlate = entry.getKey();
                }
            }

            ResultPlate finalResult = null;

            if (!bestPlate.isEmpty()) {
                PlateValidator.ValidationResult details = validationDetails.get(bestPlate);
                int cambios = (details != null) ? details.cambios : 0;
                String posiciones = (details != null) ? details.posiciones : "";

                // Calculamos confianza pasando también la cantidad de cambios
                String confianza = PlateValidator.calcularConfianza(bestPlate, maxVotes, cambios);

                finalResult = new ResultPlate(bestPlate, confianza, cambios, posiciones);
            }

            for (Bitmap b : versionsToRecycle) {
                if (b != null && !b.isRecycled() && b != crop) {
                    try {
                        b.recycle();
                    } catch (Exception e) {
                        Log.e("OCR", "No se pudo reciclar un bitmap: " + e.getMessage());
                    }
                }
            }

            // Devolvemos el objeto (será null si no se reconoció nada)
            callback.onResult(finalResult, crop);
        }
    }

    public void close() {
        mlkitRecognizer.close();
        executor.shutdown();
    }
}