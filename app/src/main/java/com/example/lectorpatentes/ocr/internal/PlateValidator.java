package com.example.lectorpatentes.ocr.internal;

import com.google.mlkit.vision.text.Text;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlateValidator {

    public static String extraerPatente(Text visionText) {
        StringBuilder sb = new StringBuilder();
        for (Text.TextBlock block : visionText.getTextBlocks()) {
            sb.append(block.getText().toUpperCase().replace("\n", "").replace(" ", ""));
        }

        // Limpieza inicial
        String raw = sb.toString()
                .replace("URUGUAY", "").replace("MERCOSUR", "")
                .replace("BRASILIA", "").replace("BRASIL", "")
                .replace("ARGENTINA", "")
                .replaceAll("[^A-Z0-9]", "");

        // Buscamos patrones de 6 o 7 caracteres
        Matcher m = Pattern.compile("[A-Z0-9]{6,7}").matcher(raw);

        if (m.find()) {
            return corregirHomoglifos(m.group());
        }
        return "";
    }

    private static String corregirHomoglifos(String candidato) {
        char[] c = candidato.toCharArray();

        // 1. Corrección básica para posiciones que suelen ser números (3 en adelante)
        for (int i = 0; i < c.length; i++) {
            if (i >= 3) {
                if (c[i] == 'G' || c[i] == 'O' || c[i] == 'Q') c[i] = '0';
                if (c[i] == 'I' || c[i] == 'L') c[i] = '1';
            }
        }

        String corregida = new String(c);

        // 2. Verificamos si es formato Uruguayo (3 letras + 3/4 números)
        if (corregida.matches("^[A-Z]{3}[0-9]{3,4}$")) {
            char[] cu = corregida.toCharArray();
            for (int i = 0; i < cu.length; i++) {
                if (i < 3) { // Las primeras 3 letras
                    if (cu[i] == '0') cu[i] = 'O';
                    if (cu[i] == '5') cu[i] = 'S';
                } else { // El resto numeros
                    if (cu[i] == 'Z') cu[i] = '2';
                    if (cu[i] == 'S') cu[i] = '5';
                }
            }
            return new String(cu);
        }

        return corregida; // Si no es uruguaya devolvemos normal
    }

    public static String calcularConfianza(String patente, int votos) {
        if (patente == null || patente.isEmpty()) return "NULA";

        boolean formatoUruArg = patente.matches("^[A-Z]{3}[0-9]{4}$");
        boolean formatoBrasil = patente.matches("^[A-Z]{3}[0-9][A-Z][0-9]{2}$");
        boolean formatoArgPar = patente.matches("^[A-Z]{2}[0-9]{3}[A-Z]{2}$");
        boolean formatoMoto = patente.matches("^[A-Z]{3}[0-9]{2,3}$");

        boolean tieneFormatoOficial = formatoUruArg || formatoBrasil || formatoArgPar || formatoMoto;

        if (votos >= 4 && tieneFormatoOficial) return "MUY ALTA";
        if (votos >= 3 && tieneFormatoOficial) return "ALTA";
        if (votos >= 3) return "MEDIA"; // 3 votos aunque no sea formato estándar
        if (votos == 2 && tieneFormatoOficial) return "MEDIA";

        return "BAJA";
    }
}