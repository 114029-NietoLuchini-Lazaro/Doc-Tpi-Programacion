package com.example.demo.rag.service;

import com.example.demo.rag.dto.DiagramDecodedResultDto;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.MatVector;
import org.bytedeco.opencv.opencv_core.Point;
import org.bytedeco.opencv.opencv_core.Rect;
import org.bytedeco.opencv.opencv_core.Size;
import org.bytedeco.opencv.opencv_imgproc.Vec4iVector;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleDirectedGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.bytedeco.opencv.global.opencv_imgcodecs.imdecode;
import static org.bytedeco.opencv.global.opencv_imgproc.ADAPTIVE_THRESH_GAUSSIAN_C;
import static org.bytedeco.opencv.global.opencv_imgproc.CHAIN_APPROX_SIMPLE;
import static org.bytedeco.opencv.global.opencv_imgproc.Canny;
import static org.bytedeco.opencv.global.opencv_imgproc.COLOR_BGR2GRAY;
import static org.bytedeco.opencv.global.opencv_imgproc.GaussianBlur;
import static org.bytedeco.opencv.global.opencv_imgproc.HoughLinesP;
import static org.bytedeco.opencv.global.opencv_imgproc.RETR_TREE;
import static org.bytedeco.opencv.global.opencv_imgproc.THRESH_BINARY_INV;
import static org.bytedeco.opencv.global.opencv_imgproc.adaptiveThreshold;
import static org.bytedeco.opencv.global.opencv_imgproc.approxPolyDP;
import static org.bytedeco.opencv.global.opencv_imgproc.boundingRect;
import static org.bytedeco.opencv.global.opencv_imgproc.contourArea;
import static org.bytedeco.opencv.global.opencv_imgproc.cvtColor;
import static org.bytedeco.opencv.global.opencv_imgproc.findContours;

/**
 * Decodificador 100% determinista (sin IA generativa) del contenido raster de una
 * imagen de diagrama incrustada en un PDF:
 * 1. OpenCV: detección de contenedores (cajas) y flechas (segmentos con cabeza de flecha).
 * 2. Tess4J: OCR de las etiquetas dentro de cada contenedor (solo imágenes, no páginas escaneadas).
 * 3. JGraphT: construcción del grafo dirigido de componentes y conexiones.
 * Devuelve {@code null} cuando no hay estructura de grafo detectable para que el llamador
 * utilice el análisis heurístico de texto de página como fallback.
 */
@Service
public class DiagramaRasterDecoder {

    private static final Logger log = LoggerFactory.getLogger(DiagramaRasterDecoder.class);

    /** Tamaño mínimo de un contenedor para ser considerado nodo del diagrama. */
    private static final int MIN_BOX_SIZE = 22;
    /** Fracción máxima del área de la imagen que puede ocupar un contenedor (el marco exterior se descarta). */
    private static final double MAX_BOX_AREA_RATIO = 0.22;
    /** Radio de vecindad para asociar un extremo de flecha con su caja más cercana. */
    private static final double CONNECT_RADIUS = 96.0;
    /** Máximo de nodos y aristas que se indexan (protección de tiempo/presupuesto). */
    private static final int MAX_NODES = 30;
    private static final int MAX_EDGES = 60;

    private ITesseract tesseractInstance;

    public record NodoRaster(int id, String etiqueta, long cx, long cy) {}

    public record AristaRaster(int origen, int destino) {}

    public record ResultadoRaster(List<NodoRaster> nodos, List<AristaRaster> aristas) {

        public boolean tieneGrafo() {
            return nodos.size() >= 2 && !aristas.isEmpty();
        }
    }

    private record CandidateBox(Rect rect) {}

    /**
     * Decodifica la estructura de la imagen. Devuelve {@code null} si no se detecta
     * un grafo válido (cajas conectadas por flechas), dejando el fallback al llamador.
     */
    public DiagramDecodedResultDto decodificar(byte[] pngBytes, int imageIndex, int pageNumber, String titleHint) {
        if (pngBytes == null || pngBytes.length == 0) {
            return null;
        }

        ResultadoRaster resultado = analizarPng(pngBytes);
        if (resultado == null || !resultado.tieneGrafo()) {
            return null;
        }

        String tipoDiagrama = clasificarTipoDiagrama(resultado);
        String interpretacion = construirInterpretacion(titleHint, pageNumber, tipoDiagrama, resultado);
        String mermaidCode = generarMermaid(titleHint, resultado);
        List<String> elementos = resultado.nodos().stream()
                .map(NodoRaster::etiqueta)
                .limit(10)
                .toList();

        return DiagramDecodedResultDto.builder()
                .imageIndex(imageIndex)
                .pageNumber(pageNumber)
                .tituloDetectado(titleHint)
                .tipoDiagrama(tipoDiagrama)
                .interpretacion(interpretacion)
                .mermaidCode(mermaidCode)
                .elementosEncontrados(elementos)
                .build();
    }

    public ResultadoRaster analizarPng(byte[] pngBytes) {
        Mat color = null;
        Mat gray = null;
        Mat blurred = null;
        Mat binary = null;
        Mat edges = null;
        Vec4iVector lines = null;
        try {
            color = imdecode(new Mat(pngBytes), 1);
            if (color.empty()) {
                log.debug("Raster no decodificable desde los bytes de la imagen");
                return null;
            }

            gray = new Mat();
            cvtColor(color, gray, COLOR_BGR2GRAY);
            blurred = new Mat();
            GaussianBlur(gray, blurred, new Size(5, 5), 0);

            binary = new Mat();
            adaptiveThreshold(blurred, binary, 255,
                    ADAPTIVE_THRESH_GAUSSIAN_C, THRESH_BINARY_INV, 15, 10);

            MatVector contours = new MatVector();
            Mat hierarchy = new Mat();
            findContours(binary, contours, hierarchy, RETR_TREE, CHAIN_APPROX_SIMPLE);

            List<CandidateBox> candidatos = new ArrayList<>();
            for (int i = 0; i < contours.size(); i++) {
                Mat contour = contours.get(i);
                Rect r = boundingRect(contour);
                if (r.width() < MIN_BOX_SIZE || r.height() < MIN_BOX_SIZE) {
                    continue;
                }
                double area = contourArea(contour);
                if (area < MIN_BOX_SIZE * (long) MIN_BOX_SIZE) {
                    continue;
                }
                long imgArea = (long) gray.cols() * gray.rows();
                if (area > imgArea * MAX_BOX_AREA_RATIO) {
                    continue;
                }
                double maxSide = Math.max(r.width(), r.height());
                double minSide = Math.min(r.width(), r.height());
                if (minSide > 0 && maxSide / minSide > 5.0) {
                    continue;
                }
                Mat approx = new Mat();
                approxPolyDP(contour, approx, 0.02 * contourArea(contour), true);
                if (approx.total() > 0 && approx.total() <= 10) {
                    candidatos.add(new CandidateBox(r));
                }
                approx.close();
            }

            // Descartar marcos anidados: es grafo de cajas directas, no jerarquías de contención
            List<CandidateBox> boxes = new ArrayList<>();
            for (CandidateBox box : candidatos) {
                boolean contenida = false;
                for (CandidateBox other : candidatos) {
                    if (box == other) {
                        continue;
                    }
                    if (rectContiene(other.rect(), box.rect())) {
                        contenida = true;
                        break;
                    }
                }
                if (!contenida) {
                    boxes.add(box);
                }
            }

            boxes.sort(Comparator
                    .comparingLong((CandidateBox b) -> b.rect().y())
                    .thenComparingLong(b -> b.rect().x()));

            if (boxes.size() < 2) {
                return null;
            }
            boxes = boxes.size() > MAX_NODES ? boxes.subList(0, MAX_NODES) : boxes;

            List<NodoRaster> nodos = new ArrayList<>();
            for (int i = 0; i < boxes.size(); i++) {
                Rect r = boxes.get(i).rect();
                nodos.add(new NodoRaster(i, ocrEtiqueta(gray, r, i), r.x() + r.width() / 2L, r.y() + r.height() / 2L));
            }

            edges = new Mat();
            Canny(blurred, edges, 60, 160);
            lines = new Vec4iVector();
            HoughLinesP(edges, lines, 1, Math.PI / 180, 40, 18, 12);

            Set<String> conexiones = new LinkedHashSet<>();
            long cantidad = lines.size();
            for (long i = 0; i < cantidad && conexiones.size() < MAX_EDGES; i++) {
                int x1 = lines.get(i).get(0);
                int y1 = lines.get(i).get(1);
                int x2 = lines.get(i).get(2);
                int y2 = lines.get(i).get(3);

                double length = Math.hypot(x2 - x1, y2 - y1);
                if (length < 18 || length > 320) {
                    continue;
                }

                int boxA = nearestBox(nodos, x1, y1);
                int boxB = nearestBox(nodos, x2, y2);
                if (boxA < 0 || boxB < 0 || boxA == boxB) {
                    continue;
                }

                boolean arrowAtStart = hasArrowheadNear(contours, x1, y1);
                boolean arrowAtEnd = hasArrowheadNear(contours, x2, y2);
                int from;
                int to;
                if (arrowAtStart) {
                    from = boxB;
                    to = boxA;
                } else if (arrowAtEnd) {
                    from = boxA;
                    to = boxB;
                } else {
                    from = boxA;
                    to = boxB;
                }

                if (from != to) {
                    conexiones.add(from + ">" + to);
                }
            }

            List<AristaRaster> aristas = new ArrayList<>();
            for (String c : conexiones) {
                String[] parts = c.split(">");
                aristas.add(new AristaRaster(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])));
            }

            ResultadoRaster resultado = new ResultadoRaster(nodos, aristas);
            return resultado.tieneGrafo() ? resultado : null;
        } catch (Exception e) {
            log.debug("Análisis raster no disponible para esta imagen: {}", e.getMessage());
            return null;
        } finally {
            if (lines != null) lines.close();
            if (edges != null) edges.close();
            if (binary != null) binary.close();
            if (blurred != null) blurred.close();
            if (gray != null) gray.close();
            if (color != null) color.close();
        }
    }

    private static boolean rectContiene(Rect outer, Rect inner) {
        return outer.x() <= inner.x()
                && outer.y() <= inner.y()
                && outer.x() + outer.width() >= inner.x() + inner.width()
                && outer.y() + outer.height() >= inner.y() + inner.height();
    }

    private int nearestBox(List<NodoRaster> nodos, double x, double y) {
        double best = Double.MAX_VALUE;
        int bestIndex = -1;
        for (NodoRaster nodo : nodos) {
            double d = Math.hypot(nodo.cx() - x, nodo.cy() - y);
            if (d < best) {
                best = d;
                bestIndex = nodo.id();
            }
        }
        return best != Double.MAX_VALUE && best <= CONNECT_RADIUS ? bestIndex : -1;
    }

    private boolean hasArrowheadNear(MatVector contours, double x, double y) {
        for (int i = 0; i < contours.size(); i++) {
            Mat contour = contours.get(i);
            double area = contourArea(contour);
            if (area < 5 || area > 400) {
                continue;
            }
            Rect r = boundingRect(contour);
            double cx = r.x() + r.width() / 2.0;
            double cy = r.y() + r.height() / 2.0;
            if (Math.hypot(cx - x, cy - y) <= 14.0) {
                return true;
            }
        }
        return false;
    }

    private String ocrEtiqueta(Mat gray, Rect r, int fallbackId) {
        String env = System.getenv("TESSDATA_DIR");
        if (env == null || env.isBlank()) {
            return etiquetaGenerica(fallbackId);
        }
        ITesseract tesseract = obtenerTesseract();
        // El ROI se recorta con un margen; si el margen deja el recuadro irreconocible, se usa etiqueta genérica.
        int margin = 6;
        int x = Math.max(0, (int) r.x() - margin);
        int y = Math.max(0, (int) r.y() - margin);
        int w = Math.min(gray.cols(), (int) r.x() + (int) r.width() + 2 * margin) - x;
        int h = Math.min(gray.rows(), (int) r.y() + (int) r.height() + 2 * margin) - y;
        if (w <= 4 || h <= 4) {
            return etiquetaGenerica(fallbackId);
        }
        Mat roi = new Mat(gray, new Rect(x, y, w, h));
        try (Java2DFrameConverter converter = new Java2DFrameConverter();
             OpenCVFrameConverter.ToMat toMat = new OpenCVFrameConverter.ToMat()) {
            Frame frame = toMat.convert(roi);
            BufferedImage img = converter.convert(frame);
            String text = tesseract.doOCR(img);
            String cleaned = limpiarEtiqueta(text);
            return cleaned.isEmpty() ? etiquetaGenerica(fallbackId) : cleaned;
        } catch (TesseractException | RuntimeException e) {
            log.debug("OCR local no disponible para el recuadro {}: {}", fallbackId, e.getMessage());
            return etiquetaGenerica(fallbackId);
        } finally {
            roi.close();
        }
    }

    private ITesseract obtenerTesseract() {
        if (tesseractInstance == null) {
            Tesseract tesseract = new Tesseract();
            tesseract.setDatapath(System.getenv("TESSDATA_DIR"));
            tesseract.setLanguage("eng");
            tesseract.setPageSegMode(6);
            tesseractInstance = tesseract;
        }
        return tesseractInstance;
    }

    private String limpiarEtiqueta(String text) {
        if (text == null) {
            return "";
        }
        String cleaned = text.replaceAll("[\\n\\r\\t]+", " ").replaceAll("\\s+", " ").trim();
        if (cleaned.length() > 40) {
            cleaned = cleaned.substring(0, 40).trim();
        }
        return cleaned;
    }

    private String etiquetaGenerica(int id) {
        return "Componente " + (id + 1);
    }

    /**
     * Clasificación determinista del tipo de diagrama a partir de la topología del grafo:
     * mapa conceptual (un nodo con varias salidas) > flujo de proceso (cadenas) > arquitectura.
     */
    private String clasificarTipoDiagrama(ResultadoRaster resultado) {
        Graph<NodoRaster, DefaultEdge> graph = construirGrafo(resultado);
        long fuentesConRamificacion = 0;
        boolean todasCadenas = true;
        for (NodoRaster nodo : graph.vertexSet()) {
            int out = graph.outDegreeOf(nodo);
            int in = graph.inDegreeOf(nodo);
            if (in == 0 && out >= 2) {
                fuentesConRamificacion++;
            }
            if (out > 1 || in > 1) {
                todasCadenas = false;
            }
        }
        if (fuentesConRamificacion >= 1) {
            return "MAPA_CONCEPTUAL";
        }
        if (todasCadenas && graph.vertexSet().size() >= 3) {
            return "FLUJO_DE_PROCESO";
        }
        return "ARQUITECTURA_DE_SISTEMAS";
    }

    private Graph<NodoRaster, DefaultEdge> construirGrafo(ResultadoRaster resultado) {
        Graph<NodoRaster, DefaultEdge> graph =
                new SimpleDirectedGraph<>(DefaultEdge.class);
        for (NodoRaster nodo : resultado.nodos()) {
            graph.addVertex(nodo);
        }
        for (AristaRaster arista : resultado.aristas()) {
            NodoRaster origen = resultado.nodos().get(arista.origen());
            NodoRaster destino = resultado.nodos().get(arista.destino());
            if (origen != null && destino != null) {
                graph.addEdge(origen, destino);
            }
        }
        return graph;
    }

    private String construirInterpretacion(String titleHint, int pageNumber, String tipoDiagrama, ResultadoRaster resultado) {
        StringBuilder sb = new StringBuilder();
        sb.append("### ").append(titleHint).append("\n\n");
        sb.append("**Ubicación en el documento:** Página ").append(pageNumber)
                .append(" (análisis raster determinista de la figura)\n\n");
        sb.append("**Tipo de diagrama:** ").append(descripcionTipo(tipoDiagrama)).append("\n\n");

        sb.append("#### Estructura detectada:\n");
        sb.append("- **Componentes:** ").append(resultado.nodos().size()).append(" contenedores identificados.\n");
        sb.append("- **Conexiones:** ").append(resultado.aristas().size()).append(" vínculos dirigidos entre componentes.\n\n");

        if (!resultado.nodos().isEmpty()) {
            sb.append("#### Componentes:\n");
            for (NodoRaster nodo : resultado.nodos()) {
                sb.append("- **").append(nodo.etiqueta()).append("**\n");
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    private String descripcionTipo(String tipoDiagrama) {
        return switch (tipoDiagrama) {
            case "MAPA_CONCEPTUAL" -> "Mapa conceptual o mental (un concepto raíz que se ramifica)";
            case "FLUJO_DE_PROCESO" -> "Flujo o secuencia de proceso (componentes enlazados en cadena)";
            case "ARQUITECTURA_DE_SISTEMAS" -> "Arquitectura de sistemas o componentes";
            default -> "Diagrama técnico del documento";
        };
    }

    private String generarMermaid(String title, ResultadoRaster resultado) {
        StringBuilder sb = new StringBuilder();
        sb.append("graph TD\n");
        sb.append("  MAIN[\"📐 ").append(escapeMermaid(title)).append("\"]\n");

        Set<NodoRaster> conectados = new LinkedHashSet<>();
        Graph<NodoRaster, DefaultEdge> graph = construirGrafo(resultado);
        for (DefaultEdge edge : graph.edgeSet()) {
            NodoRaster from = graph.getEdgeSource(edge);
            NodoRaster to = graph.getEdgeTarget(edge);
            sb.append("  N_").append(from.id()).append("[\"")
                    .append(escapeMermaid(from.etiqueta())).append("\"]\n");
            sb.append("  N_").append(to.id()).append("[\"")
                    .append(escapeMermaid(to.etiqueta())).append("\"]\n");
            sb.append("  N_").append(from.id()).append(" --> N_").append(to.id()).append("\n");
            conectados.add(from);
            conectados.add(to);
        }
        for (NodoRaster nodo : graph.vertexSet()) {
            if (!conectados.contains(nodo)) {
                sb.append("  N_").append(nodo.id()).append("[\"")
                        .append(escapeMermaid(nodo.etiqueta())).append("\"]\n");
            }
        }
        return sb.toString().trim();
    }

    private String escapeMermaid(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\"", "'")
                .replace("[", "(")
                .replace("]", ")")
                .replace("{", "(")
                .replace("}", ")")
                .replace("\n", " ")
                .trim();
    }
}