#pragma version(1)
#pragma rs java_package_name(com.example.lectorpatentes.ocr.internal)

rs_allocation gIn;
rs_matrix3x3 gTransform;

// Ahora el kernel recibe "in" (el pixel actual) y devuelve el procesado
uchar4 RS_KERNEL process(uchar4 in, uint32_t x, uint32_t y) {
    // 1. Coordenadas de salida
    float3 outCoords = {(float)x, (float)y, 1.0f};

    // 2. Aplicar transformación
    float3 inCoords = rsMatrixMultiply(&gTransform, outCoords);

    // 3. Normalizar
    float2 finalIn = {inCoords.x / inCoords.z, inCoords.y / inCoords.z};

    // 4. Obtener el pixel de la posición transformada desde la entrada
    // Usamos rsGetElementAt para el muestreo
    return *(uchar4*)rsGetElementAt(gIn, (uint32_t)finalIn.x, (uint32_t)finalIn.y);
}