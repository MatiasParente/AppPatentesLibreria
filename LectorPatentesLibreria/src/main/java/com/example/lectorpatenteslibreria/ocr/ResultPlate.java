package com.example.lectorpatenteslibreria.ocr;

public class ResultPlate {
    public String patente;
    public String confianza;
    public int cantidadCambios;
    public String posicionesCambiadas; // Ejemplo: "0, 3, 5"

    public ResultPlate(String patente, String confianza, int cantidadCambios, String posicionesCambiadas) {
        this.patente = patente;
        this.confianza = confianza;
        this.cantidadCambios = cantidadCambios;
        this.posicionesCambiadas = posicionesCambiadas;
    }
}
