package com.example.lectorpatentes.ocr;

import android.graphics.Bitmap;

public interface ScannerCallback {
    // Si encuentra la patente, devuelve el texto y la confianza. Si no, patente es null.
    void onResult(String patente, String confianza, Bitmap recorte);
}