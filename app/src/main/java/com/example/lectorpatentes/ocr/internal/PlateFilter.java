package com.example.lectorpatentes.ocr.internal;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import androidx.renderscript.Allocation;
import androidx.renderscript.Element;
import androidx.renderscript.RenderScript;
import androidx.renderscript.ScriptIntrinsicConvolve3x3;

public class PlateFilter {

    // 1. Filtro Sharpen (Nitidez) - AHORA RECIBE RenderScript
    public static Bitmap aplicarSharpen(RenderScript rs, Bitmap src) {
        float[] sharpKernel = { 0f, -1f, 0f, -1f, 5f, -1f, 0f, -1f, 0f };
        Bitmap dest = Bitmap.createBitmap(src.getWidth(), src.getHeight(), src.getConfig());

        Allocation allocIn = Allocation.createFromBitmap(rs, src);
        Allocation allocOut = Allocation.createFromBitmap(rs, dest);
        ScriptIntrinsicConvolve3x3 convolution = ScriptIntrinsicConvolve3x3.create(rs, Element.U8_4(rs));

        convolution.setInput(allocIn);
        convolution.setCoefficients(sharpKernel);
        convolution.forEach(allocOut);
        allocOut.copyTo(dest);

        // Limpieza quirúrgica de este filtro (no tocamos 'rs')
        allocIn.destroy();
        allocOut.destroy();
        convolution.destroy();

        return dest;
    }

    // 2. Ajustar Brillo y Contraste - Canvas/ColorMatrix (SIN CAMBIOS)
    public static Bitmap ajustarBrilloContraste(Bitmap src, float contrast, float brightness) {
        Bitmap dest = Bitmap.createBitmap(src.getWidth(), src.getHeight(), src.getConfig());
        Canvas canvas = new Canvas(dest);
        ColorMatrix cm = new ColorMatrix(new float[]{
                contrast, 0, 0, 0, brightness,
                0, contrast, 0, 0, brightness,
                0, 0, contrast, 0, brightness,
                0, 0, 0, 1, 0
        });
        Paint paint = new Paint();
        paint.setColorFilter(new ColorMatrixColorFilter(cm));
        canvas.drawBitmap(src, 0, 0, paint);
        return dest;
    }

    // 3. Filtro específico para Motos - Canvas/ColorMatrix (SIN CAMBIOS)
    public static Bitmap corregirBrilloMotos(Bitmap src) {
        Bitmap bitmap = src.copy(src.getConfig(), true);
        Canvas canvas = new Canvas(bitmap);
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0); // Escala de grises
        float contrast = 3.0f;
        float brightness = -80f;
        cm.postConcat(new ColorMatrix(new float[] {
                contrast, 0, 0, 0, brightness,
                0, contrast, 0, 0, brightness,
                0, 0, contrast, 0, brightness,
                0, 0, 0, 1, 0
        }));
        Paint paint = new Paint();
        paint.setColorFilter(new ColorMatrixColorFilter(cm));
        canvas.drawBitmap(bitmap, 0, 0, paint);
        return bitmap;
    }

    // 4. Corregir Perspectiva - Matrix (SIN CAMBIOS)
    public static Bitmap corregirPerspectivaSimulada(Bitmap src) {
        Matrix matrix = new Matrix();
        matrix.postScale(1.3f, 1.0f);
        return Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), matrix, true);
    }

    // 5. Corregir Picado Cenital - Matrix (SIN CAMBIOS)
    public static Bitmap corregirPicadoCenital(Bitmap src) {
        Matrix matrix = new Matrix();
        matrix.postScale(0.9f, 1.4f);
        return Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), matrix, true);
    }

    // 6. Planchado Extremo - AHORA RECIBE RenderScript
    public static Bitmap plancharPatenteExtremadamenteDoblada(RenderScript rs, Bitmap src) {
        ScriptC_planchar_patente script = new ScriptC_planchar_patente(rs);

        Bitmap dst = Bitmap.createBitmap(src.getWidth(), src.getHeight(), src.getConfig());
        Allocation allocIn = Allocation.createFromBitmap(rs, src);
        Allocation allocOut = Allocation.createFromBitmap(rs, dst);

        float w = (float) src.getWidth();
        float h = (float) src.getHeight();
        float[] dstPoints = {0, 0, w, 0, w, h, 0, h};
        float[] srcPoints = {
                w * 0.15f, h * 0.20f,   // Superior Izquierda
                w * 0.95f, h * 0.05f,   // Superior Derecha (Subimos más el borde)
                w * 1.30f, h * 0.90f,   // Inferior Derecha (Tiramos MUCHO más hacia afuera)
                -w * 0.10f, h * 0.95f   // Inferior Izquierda
        };

        Matrix matrix = new Matrix();
        matrix.setPolyToPoly(srcPoints, 0, dstPoints, 0, 4);
        Matrix invMatrix = new Matrix();
        matrix.invert(invMatrix);

        float[] matValues = new float[9];
        invMatrix.getValues(matValues);
        androidx.renderscript.Matrix3f rsMatrix = new androidx.renderscript.Matrix3f(matValues);

        script.set_gTransform(rsMatrix);
        script.set_gIn(allocIn);
        script.forEach_process(allocIn, allocOut);

        allocOut.copyTo(dst);

        // Limpieza quirúrgica de este filtro
        allocIn.destroy();
        allocOut.destroy();
        script.destroy();

        return dst;
    }

    // 7. Planchado Lado Izquierdo - AHORA RECIBE RenderScript
    public static Bitmap plancharPatenteLadoIzquierdo(RenderScript rs, Bitmap src) {
        ScriptC_planchar_patente script = new ScriptC_planchar_patente(rs);

        Bitmap dst = Bitmap.createBitmap(src.getWidth(), src.getHeight(), src.getConfig());
        Allocation allocIn = Allocation.createFromBitmap(rs, src);
        Allocation allocOut = Allocation.createFromBitmap(rs, dst);

        float w = (float) src.getWidth();
        float h = (float) src.getHeight();
        float[] dstPoints = {0, 0, w, 0, w, h, 0, h};

        // LA MAGIA: Estiramos fuerte el lado izquierdo en vez del derecho
        float[] srcPoints = {
                -w * 0.30f, h * 0.05f,  // Superior Izquierda (Estiramos fuerte hacia afuera)
                w * 0.85f, h * 0.20f,   // Superior Derecha
                w * 1.10f, h * 0.95f,   // Inferior Derecha
                -w * 0.30f, h * 0.90f   // Inferior Izquierda (Estiramos fuerte hacia afuera)
        };

        Matrix matrix = new Matrix();
        matrix.setPolyToPoly(srcPoints, 0, dstPoints, 0, 4);
        Matrix invMatrix = new Matrix();
        matrix.invert(invMatrix);

        float[] matValues = new float[9];
        invMatrix.getValues(matValues);
        androidx.renderscript.Matrix3f rsMatrix = new androidx.renderscript.Matrix3f(matValues);

        script.set_gTransform(rsMatrix);
        script.set_gIn(allocIn);
        script.forEach_process(allocIn, allocOut);

        allocOut.copyTo(dst);

        // Limpieza quirúrgica de este filtro
        allocIn.destroy();
        allocOut.destroy();
        script.destroy();

        return dst;
    }
}