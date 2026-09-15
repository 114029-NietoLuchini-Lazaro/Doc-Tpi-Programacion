package com.example.demo.rag.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PdfTextExtractorServiceTest {

    private final PdfTextExtractorService extractor = new PdfTextExtractorService();

    @Test
    void dehyphenacionUnePalabraPartidaEnMinuscula() {
        String normalized = extractor.normalizePageText("En este tema se explica la recursi-\nvidad como técnica.");
        assertTrue(normalized.contains("recursividad"), "El guion de fin de línea debe unir la palabra: " + normalized);
        assertFalse(normalized.contains("recursi-\nvidad"));
    }

    @Test
    void dehyphenacionNoUneRangosNumericosNiSeparadoresDeCifras() {
        String normalized = extractor.normalizePageText("El período cubre 2020-\n2025 y el 10-2\nresultado");
        assertTrue(normalized.contains("2020-"), "Un guion entre dígitos no debe unirse: " + normalized);
        assertTrue(normalized.contains("2025"), "Debe conservar el rango: " + normalized);
    }

    @Test
    void lineasDeCabeceraRepetidasSeExcluyenDelContenido() {
        List<String> pages = List.of(
                "PROYECTO FINAL - INTRO A PROGRAMACION\ncontenido de la página uno",
                "PROYECTO FINAL - INTRO A PROGRAMACION\ncontenido de la página dos",
                "PROYECTO FINAL - INTRO A PROGRAMACION\ncontenido de la página tres"
        );

        Set<String> repeated = extractor.detectRepeatedLayoutLines(pages);

        assertTrue(repeated.contains("PROYECTO FINAL - INTRO A PROGRAMACION"));
        assertFalse(repeated.contains("contenido de la página uno"));
    }

    @Test
    void lineasDeNumeracionDePaginaSeExcluyenCuandoSeRepiten() {
        List<String> pages = List.of(
                "3\nheader\ncuerpo a",
                "12\nheader\ncuerpo b",
                "3\nheader\ncuerpo c"
        );

        Set<String> repeated = extractor.detectRepeatedLayoutLines(pages);

        assertTrue(repeated.contains("3"), "Linea numérica repetida debe ser layout: " + repeated);
        assertTrue(repeated.contains("header"));
    }

    @Test
    void lineaRepetidaEnUnaSolaPaginaNoSeExcluye() {
        List<String> pages = List.of(
                "solo aquí\ncontenido a",
                "contenido b",
                "contenido c"
        );

        Set<String> repeated = extractor.detectRepeatedLayoutLines(pages);

        assertFalse(repeated.contains("solo aquí"), "Una línea que no se repite no puede ser header/footer");
    }
}