package com.example.lectorpatenteslibreria.ocr.internal;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.util.Log;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;

import java.io.FileInputStream;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Map;

public class PlateDetector {

    private Interpreter tflite;
    private int imageSizeX, imageSizeY;
    private boolean isModelLoaded = false;

    public PlateDetector(Context context) {
        try {
            MappedByteBuffer buffer = loadModelFile(context, "detect.tflite");
            tflite = new Interpreter(buffer);
            int[] shape = tflite.getInputTensor(0).shape(); // [1, height, width, 3]
            imageSizeY = shape[1];
            imageSizeX = shape[2];
            isModelLoaded = true;
        } catch (Exception e) {
            Log.e("OCR_DETECTOR", "Error cargando modelo TFLite: " + e.getMessage());
        }
    }

    private MappedByteBuffer loadModelFile(Context context, String modelPath) throws Exception {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd(modelPath);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.getStartOffset(), fileDescriptor.getDeclaredLength());
    }

    public Bitmap detectBestPlate(Bitmap bitmap) {
        if (!isModelLoaded) return null;

        try {
            // Pre-procesar la imagen para la IA
            ImageProcessor ip = new ImageProcessor.Builder()
                    .add(new ResizeOp(imageSizeY, imageSizeX, ResizeOp.ResizeMethod.BILINEAR))
                    .add(new NormalizeOp(127.5f, 127.5f))
                    .build();

            TensorImage ti = new TensorImage(DataType.FLOAT32);
            ti.load(bitmap);
            ti = ip.process(ti);

            float[][] confs = new float[1][10]; // Confianza de las 10 mejores detecciones
            float[][][] boxes = new float[1][10][4]; // Coordenadas de las cajas
            Map<Integer, Object> outputs = new HashMap<>();
            outputs.put(0, confs);
            outputs.put(1, boxes);

            tflite.runForMultipleInputsOutputs(new Object[]{ti.getBuffer()}, outputs);

            // detección con mayor puntaje
            int bestIdx = -1;
            float maxConf = 0f;
            for (int i = 0; i < 10; i++) {
                if (confs[0][i] > maxConf) {
                    maxConf = confs[0][i];
                    bestIdx = i;
                }
            }

            // Si la confianza es decente 25%, recortamos
            if (bestIdx != -1 && maxConf >= 0.25f) {
                float ymin = boxes[0][bestIdx][0], xmin = boxes[0][bestIdx][1];
                float ymax = boxes[0][bestIdx][2], xmax = boxes[0][bestIdx][3];

                // Convertir coordenadas relativas (0-1) a píxeles reales
                int left = (int) (Math.max(0, xmin - 0.02f) * bitmap.getWidth());
                int top = (int) (Math.max(0, ymin - 0.02f) * bitmap.getHeight());
                int right = (int) (Math.min(1.0f, xmax + 0.02f) * bitmap.getWidth());
                int bottom = (int) (Math.min(1.0f, ymax + 0.02f) * bitmap.getHeight());

                return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top);
            }
        } catch (Exception e) {
            Log.e("OCR_DETECTOR", "Error en detección: " + e.getMessage());
        }
        return null;
    }

    public void close() {
        if (tflite != null) tflite.close();
    }
}