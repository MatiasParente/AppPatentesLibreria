# Lector de Patentes

**LectorPatentes** es una librería de Android potente y ligera diseñada para la detección y reconocimiento de matrículas vehiculares. Está optimizada específicamente para los formatos de **Uruguay (Auto/Moto)** y el estándar **Mercosur (Argentina/Brasil)**.

---

## ¿Cómo funciona?

A diferencia de un OCR estándar, esta librería utiliza un proceso de 5 etapas para garantizar precisión en condiciones difíciles:

1.  **Detección de Área:** Mediante PlateDetector y un modelo custom de **TensorFlow Lite** (detect.tflite), se localiza la matrícula y se elimina el "ruido" del resto del vehículo.
2.  **Generación de Versiones (18 Filtros):** El motor crea 18 variantes de la imagen en tiempo real (ajustes de perspectiva, brillo, contraste y nitidez) para combatir sombras o reflejos solares.
3.  **Lectura por Votación:** ML Kit procesa las 18 versiones. El sistema realiza un conteo de votos para determinar cuál es el texto con mayor probabilidad de éxito.
4.  **Validación Regional:** Un motor de reglas (PlateValidator) verifica que el texto encaje con los patrones oficiales (ej. LLL NNNN).
5.  **Corrección Inteligente:** Si el formato es correcto pero un carácter es dudoso, la librería aplica un "forzado" inteligente (ej. cambia un 5 por una S si la posición exige una letra).

---

## Instalación

### 1. Agregar el archivo `.aar`
Copia el archivo `LectorPatentesLibreria-release.aar` dentro de la carpeta libs/ de tu módulo app.

**Descarga**
Puedes descargar la última versión del archivo `.aar` desde la sección de [Releases]

### 2. Configurar `build.gradle` (Module: app)
Agrega la referencia local y las dependencias necesarias:

```gradle
dependencies {
    // Librería física
    implementation files('libs/LectorPatentesLibreria-release.aar')

    // Dependencias de Google ML Kit y TFLite
    implementation 'com.google.mlkit:text-recognition-latin:16.0.0'
    implementation 'com.google.android.gms:play-services-mlkit-text-recognition:19.0.0'
    implementation 'org.tensorflow:tensorflow-lite:2.14.0'
    implementation 'org.tensorflow:tensorflow-lite-support:0.4.4'
}
```

### 3. No comprimir archivo `.tflite`
Para evitar que Android comprima el modelo de IA y cause errores de lectura, agrega esto en el bloque android { ... }:

```gradle
android {
    aaptOptions {
        noCompress "tflite"
    }
}
```

## Guia de uso
**Paso 1: Inicialización**
Prepara el scanner en tu Activity. Es recomendable hacerlo en el onCreate para precargar los modelos.

```gradle
import com.example.lectorpatenteslibreria.ocr.PlateScanner;
import com.example.lectorpatenteslibreria.ocr.ResultPlate;

private PlateScanner scanner;

@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    scanner = new PlateScanner(this);
}
```

**Paso 2: Procesar Imagen**
Llama al método processImage pasando un Bitmap. El resultado se devuelve en un hilo secundario.

```gradle
scanner.processImage(miBitmap, (resultado, recorte) -> {
    runOnUiThread(() -> {
        if (resultado != null) {
            txtPatente.setText("Patente: " + resultado.patente);
            txtConfianza.setText("Seguridad: " + resultado.confianza);
            
            // Mostrar el recorte optimizado si se desea
            if (recorte != null) imgRecorte.setImageBitmap(recorte);
        } else {
            txtPatente.setText("No se detectó ninguna patente.");
        }
    });
});
```

**Paso 3: Liberar Recursos**
No olvides cerrar el scanner para liberar la memoria RAM de los modelos.

```gradle
@Override
protected void onDestroy() {
    super.onDestroy();
    if (scanner != null) scanner.close();
}
```

## Resultado esperado
Puedes esperar obtener la patente en texto (String, ej: “ISD1234”), la confianza de del resultado (String, “Muy Alta”, “Alta”, “Media” o “Baja”), la cantidad de caracteres forzados (int) y las posiciones de los mismos (String, ej: “0, 2”)


