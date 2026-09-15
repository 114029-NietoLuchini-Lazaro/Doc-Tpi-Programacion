package com.example.demo.rag.service;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagramaRasterDecoderTest {

    private final DiagramaRasterDecoder decoder = new DiagramaRasterDecoder();

    @Test
    void decodificaGrafoDesdeImagenSintetica() throws Exception {
        byte[] png = diagramaSintetica();

        DiagramaRasterDecoder.ResultadoRaster resultado = decoder.analizarPng(png);

        assertNotNull(resultado, "El pipeline raster debe detectar cajas y flechas en un diagrama limpio");
        assertTrue(resultado.tieneGrafo(), "Debe detectar al menos dos componentes conectados: " + resultado);
    }

    @Test
    void imagenVaciaNoGeneraGrafo() throws Exception {
        byte[] png = imagenLimpia();

        assertNull(decoder.analizarPng(png), "Una imagen sin estructura no debe producir un grafo");
        assertNull(decoder.decodificar(png, 0, 1, "Figura vacía"), "La decodificación debe delegar en el fallback");
    }

    private byte[] diagramaSintetica() throws Exception {
        BufferedImage img = new BufferedImage(360, 260, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 360, 260);
        g.setColor(Color.LIGHT_GRAY);
        g.fillRect(30, 40, 130, 80);
        g.fillRect(200, 40, 130, 80);
        g.fillRect(120, 180, 120, 60);
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(4f));
        g.drawRect(30, 40, 130, 80);
        g.drawRect(200, 40, 130, 80);
        g.drawRect(120, 180, 120, 60);

        g.drawLine(160, 80, 200, 80);
        g.fillPolygon(new Polygon(new int[]{185, 185, 198}, new int[]{72, 88, 80}, 3));

        g.drawLine(150, 120, 150, 180);
        g.fillPolygon(new Polygon(new int[]{142, 158, 150}, new int[]{168, 168, 180}, 3));

        g.dispose();
        return aPng(img);
    }

    private byte[] imagenLimpia() throws Exception {
        BufferedImage img = new BufferedImage(240, 160, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 240, 160);
        g.dispose();
        return aPng(img);
    }

    private byte[] aPng(BufferedImage img) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }
}