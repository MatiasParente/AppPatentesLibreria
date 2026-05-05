package com.example.lectorpatentes.ocr.internal;

import com.google.mlkit.vision.text.Text;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlateValidator {

    // Formatos RegEx
    private static final String PATTERN_URU_AUTO = "^[A-Z]{3}[0-9]{4}$";
    private static final String PATTERN_URU_MOTO = "^[A-Z]{3}[0-9]{3}$";
    private static final String PATTERN_ARG_OLD = "^[A-Z]{3}[0-9]{3}$";
    private static final String PATTERN_MERCOSUR_ARG = "^[A-Z]{2}[0-9]{3}[A-Z]{2}$";
    private static final String PATTERN_MERCOSUR_BRA = "^[A-Z]{3}[0-9][A-Z][0-9]{2}$";

    public static String extraerPatente(Text visionText) {
        String[] blacklist = {
                "MERCOSUR", "BRASIL", "URUGUAY", "ARGENTINA",
                "REPUBLICA", "ORIENTAL", "CONSU DE", "TEST"
        };

        for (Text.TextBlock block : visionText.getTextBlocks()) {
            String raw = block.getText().toUpperCase();

            for (String word : blacklist) {
                raw = raw.replace(word, "");
            }

            raw = raw.replaceAll("\\s+", "") // Quita espacios y saltos
                    .replaceAll("[^A-Z0-9]", ""); // Solo deja letras y números

            if (raw.length() >= 6 && raw.length() <= 8) {
                String corregida = procesarSegunFormato(raw);
                if (!corregida.isEmpty()) return corregida;
            }
        }
        return "";
    }

    private static String procesarSegunFormato(String raw) {
        int len = raw.length();

        if (len == 6) {
            String res = forzarPatron(raw, "LLLNNN");
            if (res.matches(PATTERN_URU_MOTO)) return res;
        }

        if (len == 7) {

            // 1. Prioridad: Uruguay Auto (LLL NNNN)
            String uru = forzarPatron(raw, "LLLNNNN");
            if (uru.matches(PATTERN_URU_AUTO)) {
                return uru;
            }

            // 2. Mercosur Argentina (LL NNN LL)
            String arg = forzarPatron(raw, "LLNNNLL");
            if (arg.matches(PATTERN_MERCOSUR_ARG)) return arg;

            // 3. Mercosur Brasil (LLL N L NN)
            if (Character.isDigit(raw.charAt(3)) || raw.charAt(3) == 'I' || raw.charAt(3) == 'O') {
                String bra = forzarPatron(raw, "LLLNLNN");
                if (bra.matches(PATTERN_MERCOSUR_BRA)) return bra;
            }
        }

        return raw;
    }

    private static String forzarPatron(String raw, String mascara) {
        char[] c = raw.toCharArray();
        char[] m = mascara.toCharArray();
        StringBuilder res = new StringBuilder();

        for (int i = 0; i < c.length; i++) {
            if (m[i] == 'L') res.append(forzarLetra(c[i]));
            else res.append(forzarNumero(c[i]));
        }
        return res.toString();
    }

    private static char forzarLetra(char c) {
        switch (c) {
            case '0': return 'O';
            case '1': return 'I';
            case '2': return 'Z';
            case '4': return 'A';
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
            case 'A': return '4';
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

        if (esOficial) {
            if (votos >= 10) return "MUY ALTA"; // Casi todos los filtros coincidieron
            if (votos >= 7)  return "ALTA";     // Al menos un tercio
            if (votos >= 5)  return "MEDIA";    // Solo funcionó en algunos filtros específicos
            return "BAJA";
        } else {
            if (votos >= 7) return "MEDIA RARA";
            return "BAJA";
        }
    }
}