package com.example.lectorpatentes;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.example.lectorpatenteslibreria.ocr.PlateScanner;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private ImageView visor;
    private TextView txtResultado;
    private Button btnCamara;

    private String rutaImagen;
    private PlateScanner scanner;
    private static final int REQUEST_IMAGE_CAPTURE = 1;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. Vincular UI
        visor = findViewById(R.id.imageView);
        txtResultado = findViewById(R.id.txtResultado);
        btnCamara = findViewById(R.id.btnCamara);

        // 2. Inicializar nuestro motor de escaneo
        scanner = new PlateScanner(this);

        // 3. Evento del botón
        btnCamara.setOnClickListener(v -> abrirCamara());
    }

    private void abrirCamara() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        try {
            File photoFile = crearArchivoImagen();
            if (photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(this,
                        "com.example.lectorpatentes.fileprovider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            }
        } catch (IOException ex) {
            Toast.makeText(this, "Error al crear archivo", Toast.LENGTH_SHORT).show();
        }
    }

    private File crearArchivoImagen() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile("PATENTE_" + timeStamp, ".jpg", storageDir);
        rutaImagen = image.getAbsolutePath();
        return image;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            // Cargamos la foto que sacamos
            Bitmap bitmap = BitmapFactory.decodeFile(rutaImagen);

            if (bitmap != null) {
                visor.setImageBitmap(bitmap);
                txtResultado.setText("Buscando patente...");

                // ACÁ ESTÁ EL CAMBIO: Ahora recibimos (resultado, recorte)
                scanner.processImage(bitmap, (resultado, recorte) -> {
                    runOnUiThread(() -> {
                        if (resultado != null) {
                            // Extraemos los datos del objeto ResultPlate
                            String textoAviso = "PATENTE: " + resultado.patente +
                                    "\nConfianza: " + resultado.confianza +
                                    "\nCaracteres Forzados: " + resultado.cantidadCambios +
                                    "\nPosiciones: [" + resultado.posicionesCambiadas + "]";

                            txtResultado.setText(textoAviso);

                            if (recorte != null) visor.setImageBitmap(recorte);
                        } else {
                            txtResultado.setText("No se detectó ninguna patente.");
                        }
                    });
                });
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (scanner != null) {
            scanner.close();
        }
    }
}