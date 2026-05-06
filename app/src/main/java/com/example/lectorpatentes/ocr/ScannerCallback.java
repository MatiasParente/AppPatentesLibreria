package com.example.lectorpatentes.ocr;

import android.graphics.Bitmap;

public interface ScannerCallback {
    // Ahora devuelve el objeto con métricas detalladas
    void onResult(ResultPlate resultado, Bitmap recorte);
}