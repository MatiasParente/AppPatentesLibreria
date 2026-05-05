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

        String raw = sb.toString()
                .replace("URUGUAY", "").replace("MERCOSUR", "")
                .replace("ARGENTINA", "").replace("BRASIL", "")
                .replaceAll("[^A-Z0-9]", "");

        // Buscamos patrones de 6 a 7 caracteres
        Matcher m = Pattern.compile("[A-Z0-9]{6,7}").matcher(raw);

        if (m.find()) {
            return identificarYCorregir(m.group());
        }
        return "";
    }

    private static String identificarYCorregir(String candidato) {
        // es uruguaya (3 Letras + 3 o 4 Números)
        if (candidato.matches("^[A-Z0-9]{3}[0-9A-Z]{3,4}$")) {
            char[] c = candidato.toCharArray();
            StringBuilder res = new StringBuilder();

            for (int i = 0; i < c.length; i++) {
                if (i < 3) {
                    res.append(forzarLetra(c[i])); // Las primeras 3 siempre letras
                } else {
                    res.append(forzarNumero(c[i])); // El resto siempre números
                }
            }
            return res.toString();
        }

        // es mercosur argentina (AA 123 BB)
        if (candidato.matches("^[A-Z0-9]{2}[0-9A-Z]{3}[A-Z0-9]{2}$")) {
            char[] c = candidato.toCharArray();
            return "" + forzarLetra(c[0]) + forzarLetra(c[1]) +
                    forzarNumero(c[2]) + forzarNumero(c[3]) + forzarNumero(c[4]) +
                    forzarLetra(c[5]) + forzarLetra(c[6]);
        }

        return candidato;
    }

    private static char forzarLetra(char c) {
        switch (c) {
            case '0': return 'O';
            case '1': return 'I';
            case '2': return 'Z';
            case '5': return 'S';
            case '8': return 'B';
            default: return c;
        }
    }

    private static char forzarNumero(char c) {
        switch (c) {
            case 'O': case 'Q': case 'G': return '0';
            case 'I': case 'L': return '1';
            case 'Z': return '2';
            case 'S': return '5';
            case 'B': return '8';
            default: return c;
        }
    }

    public static String calcularConfianza(String patente, int votos) {
        if (patente == null || patente.isEmpty()) return "NULA";

        // Formatos oficiales
        boolean uruArg = patente.matches("^[A-Z]{3}[0-9]{3,4}$");
        boolean mercosurArg = patente.matches("^[A-Z]{2}[0-9]{3}[A-Z]{2}$");
        boolean mercosurBra = patente.matches("^[A-Z]{3}[0-9][A-Z][0-9]{2}$");

        boolean esOficial = uruArg || mercosurArg || mercosurBra;

        if (votos >= 4 && esOficial) return "MUY ALTA";
        if (votos >= 2 && esOficial) return "ALTA";
        if (votos >= 3) return "MEDIA";

        return "BAJA";
    }
}