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
        Bitmap crop = detector.detectBestPlate(original);

        if (crop == null) {
            callback.onResult(null, null);
        } else {
            recognizer.analyze(crop, callback);
        }
    }

    public void close() {
        detector.close();
        recognizer.close();
    }
}