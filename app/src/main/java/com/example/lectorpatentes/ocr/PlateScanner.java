package com.example.lectorpatentes.ocr;

import android.content.Context;
import android.graphics.Bitmap;
import com.example.lectorpatentes.ocr.internal.PlateDetector;
import com.example.lectorpatentes.ocr.internal.PlateRecognizer;

public class PlateScanner {

    private final PlateDetector detector;
    private final PlateRecognizer recognizer;

    public PlateScanner(Context context) {
        this.detector = new PlateDetector(context);
        this.recognizer = new PlateRecognizer(context);
    }

    public void processImage(Bitmap original, ScannerCallback callback) {
        // Buscamos la patente en la foto grande
        Bitmap crop = detector.detectBestPlate(original);

        if (crop == null) {
            // Si no detectó la placa, devolvemos null inmediatamente
            callback.onResult(null, "NULA", null);
        } else {
            // 2. Si la encontró, se la pasamos al reconocedor de texto
            recognizer.analyze(crop, callback);
        }
    }

    public void close() {
        detector.close();
        recognizer.close();
    }
}