package com.example.lectorpatentes.ocr.internal;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;

public class PlateFilter {

    //Sharpen
    public static Bitmap aplicarSharpen(Bitmap src) {
        Bitmap dest = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(dest);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(src, 0, 0, paint);

        // Aplicamos alto contraste para remarcar bordes negros
        ColorMatrix cm = new ColorMatrix();
        cm.set(new float[]{
                2.0f, 0, 0, 0, -30f,
                0, 2.0f, 0, 0, -30f,
                0, 0, 2.0f, 0, -30f,
                0, 0, 0, 1, 0
        });

        paint.setColorFilter(new ColorMatrixColorFilter(cm));
        canvas.drawBitmap(src, 0, 0, paint);

        return dest;
    }

    // Ajustar Brillo y Contraste
    public static Bitmap ajustarBrilloContraste(Bitmap src, float contrast, float brightness) {
        Bitmap dest = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(dest);
        ColorMatrix cm = new ColorMatrix(new float[]{
                contrast, 0, 0, 0, brightness,
                0, contrast, 0, 0, brightness,
                0, 0, contrast, 0, brightness,
                0, 0, 0, 1, 0
        });
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        paint.setColorFilter(new ColorMatrixColorFilter(cm));
        canvas.drawBitmap(src, 0, 0, paint);
        return dest;
    }

    //Corregir Brillo Motos
    public static Bitmap corregirBrilloMotos(Bitmap src) {
        Bitmap dest = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(dest);
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0); // Escala de grises
        cm.postConcat(new ColorMatrix(new float[] {
                3.0f, 0, 0, 0, -80f,
                0, 3.0f, 0, 0, -80f,
                0, 0, 3.0f, 0, -80f,
                0, 0, 0, 1, 0
        }));
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        paint.setColorFilter(new ColorMatrixColorFilter(cm));
        canvas.drawBitmap(src, 0, 0, paint);
        return dest;
    }

    // corregir Perspectiva
    public static Bitmap corregirPerspectiva(Bitmap src) {
        Matrix matrix = new Matrix();
        matrix.postScale(1.3f, 1.0f);
        return Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), matrix, true);
    }

    // Corregir Picado Cenital
    public static Bitmap corregirArriba(Bitmap src) {
        Matrix matrix = new Matrix();
        matrix.postScale(0.9f, 1.4f);
        return Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), matrix, true);
    }

    // Planchado derecha
    public static Bitmap plancharPatenteLadoDerecho(Bitmap src) {
        int w = src.getWidth();
        int h = src.getHeight();
        Bitmap dst = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(dst);

        float[] dstPoints = {0, 0, w, 0, w, h, 0, h};
        float[] srcPoints = {
                w * 0.25f, h * 0.30f,
                w * 0.85f, h * 0.05f,
                w * 1.20f, h * 0.85f,
                -w * 0.20f, h * 0.95f
        };

        Matrix matrix = new Matrix();
        matrix.setPolyToPoly(srcPoints, 0, dstPoints, 0, 4);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(src, matrix, paint);
        return dst;
    }

    // Planchado Lado Izquierdo
    public static Bitmap plancharPatenteLadoIzquierdo(Bitmap src) {
        int w = src.getWidth();
        int h = src.getHeight();
        Bitmap dst = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(dst);

        float[] dstPoints = {0, 0, w, 0, w, h, 0, h};
        float[] srcPoints = {
                -w * 0.30f, h * 0.05f,
                w * 0.85f, h * 0.20f,
                w * 1.10f, h * 0.95f,
                -w * 0.30f, h * 0.90f
        };

        Matrix matrix = new Matrix();
        matrix.setPolyToPoly(srcPoints, 0, dstPoints, 0, 4);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(src, matrix, paint);
        return dst;
    }
}