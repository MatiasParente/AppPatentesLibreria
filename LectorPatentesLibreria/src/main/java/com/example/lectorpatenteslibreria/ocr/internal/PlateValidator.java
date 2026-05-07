package com.example.lectorpatenteslibreria.ocr.internal;

import com.google.mlkit.vision.text.Text;

public class PlateValidator {

    // Clase interna para transportar datos de validación
    public static class ValidationResult {
        public String texto;
        public int cambios;
        public String posiciones;
        public ValidationResult(String t, int c, String p) { this.texto = t; this.cambios = c; this.posiciones = p; }
    }

    private static final String PATTERN_URU_AUTO = "^[A-Z]{3}[0-9]{4}$";
    private static final String PATTERN_URU_MOTO = "^[A-Z]{3}[0-9]{3}$";
    private static final String PATTERN_MERCOSUR_ARG = "^[A-Z]{2}[0-9]{3}[A-Z]{2}$";
    private static final String PATTERN_MERCOSUR_BRA = "^[A-Z]{3}[0-9][A-Z][0-9]{2}$";

    public static ValidationResult extraerPatente(Text visionText) {
        String[] blacklist = {"MERCOSUR", "BRASIL", "URUGUAY", "ARGENTINA", "REPUBLICA", "ORIENTAL"};

        for (Text.TextBlock block : visionText.getTextBlocks()) {
            String raw = block.getText().toUpperCase();
            for (String word : blacklist) raw = raw.replace(word, "");
            raw = raw.replaceAll("\\s+", "").replaceAll("[^A-Z0-9]", "");

            if (raw.length() >= 6 && raw.length() <= 8) {
                return procesarSegunFormato(raw);
            }
        }
        return null; // Cambiado a null si no hay candidato
    }

    private static ValidationResult procesarSegunFormato(String raw) {
        int len = raw.length();

        if (len == 6) {
            ValidationResult res = forzarPatron(raw, "LLLNNN");
            if (res.texto.matches(PATTERN_URU_MOTO)) return res;
        }

        if (len == 7) {
            // Generamos los 3 candidatos posibles
            ValidationResult uru = forzarPatron(raw, "LLLNNNN");
            ValidationResult arg = forzarPatron(raw, "LLNNNLL");
            ValidationResult bra = forzarPatron(raw, "LLLNLNN");

            // Buscamos cuál tuvo menos cambios
            ValidationResult mejorCandidato = uru;

            if (arg.cambios < mejorCandidato.cambios) {
                mejorCandidato = arg;
            }

            if (bra.cambios < mejorCandidato.cambios) {
                mejorCandidato = bra;
            }

            // Si hay empate en cambios,
            // le damos prioridad a Uruguay por ser el mercado principal
            if (uru.cambios <= mejorCandidato.cambios) {
                mejorCandidato = uru;
            }

            return mejorCandidato;
        }

        return new ValidationResult(raw, 0, "");
    }

    private static ValidationResult forzarPatron(String raw, String mascara) {
        char[] c = raw.toCharArray();
        char[] m = mascara.toCharArray();
        StringBuilder res = new StringBuilder();
        int cambios = 0;
        StringBuilder pos = new StringBuilder();

        for (int i = 0; i < c.length; i++) {
            char original = c[i];
            char nuevo = (m[i] == 'L') ? forzarLetra(original) : forzarNumero(original);

            if (original != nuevo) {
                cambios++;
                if (pos.length() > 0) pos.append(",");
                pos.append(i);
            }
            res.append(nuevo);
        }
        return new ValidationResult(res.toString(), cambios, pos.toString());
    }

    private static char forzarLetra(char c) {
        switch (c) {
            case '0': return 'O'; case '1': return 'I'; case '2': return 'Z';
            case '4': return 'A'; case '5': return 'S'; case '8': return 'B';
            default: return c;
        }
    }

    private static char forzarNumero(char c) {
        switch (c) {
            case 'O': case 'Q': case 'G': return '0';
            case 'I': case 'L': return '1'; case 'Z': return '2';
            case 'A': return '4'; case 'S': return '5'; case 'B': return '8';
            default: return c;
        }
    }

    public static String calcularConfianza(String patente, int votos, int cambios) {
        if (patente == null || patente.isEmpty()) return "NULA";
        boolean oficial = patente.matches(PATTERN_URU_AUTO) || patente.matches(PATTERN_MERCOSUR_ARG) || patente.matches(PATTERN_MERCOSUR_BRA);

        int score = votos;
        if (cambios > 1) score -= 2; // Penalización por forzar caracteres
        if (cambios > 3) score -= 3;

        if (oficial) {
            if (score >= 12) return "MUY ALTA";
            if (score >= 8) return "ALTA";
            if (score >= 5) return "MEDIA";
        }
        return "BAJA";
    }
}