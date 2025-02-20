package com.goofy.GoofyFiles.compression;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.StopWatch;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class CompressionPerformanceTest {

    private CompressionService compressionService;
    private static final int[] IMAGE_SIZES = {100, 500, 1000}; // Tailles d'images en pixels
    private static final CompressionService.CompressionType[] COMPRESSION_TYPES = {
            CompressionService.CompressionType.LZ4,
            CompressionService.CompressionType.ZSTD,
            CompressionService.CompressionType.SNAPPY
    };

    @BeforeEach
    void setUp() {
        compressionService = new CompressionService();
    }

    @Test
    void testImageCompressionPerformance(@TempDir Path tempDir) throws IOException {
        System.out.println("\n=== Test de Performance sur Images ===");
        System.out.println("Format: Taille | Algorithme | Temps Compression | Taux Compression | Temps Décompression");
        System.out.println("------------------------------------------------------------------------");

        for (int size : IMAGE_SIZES) {
            // Créer une image test
            File imageFile = createTestImage(tempDir, size);
            byte[] originalData = readFileToBytes(imageFile);
            
            for (CompressionService.CompressionType type : COMPRESSION_TYPES) {
                // Mesurer la compression
                StopWatch compressionWatch = new StopWatch();
                compressionWatch.start();
                byte[] compressedData = compressionService.compress(originalData, type);
                compressionWatch.stop();

                // Mesurer la décompression
                StopWatch decompressionWatch = new StopWatch();
                decompressionWatch.start();
                byte[] decompressedData = compressionService.decompress(compressedData, type, originalData.length);
                decompressionWatch.stop();

                // Calculer les métriques
                double compressionRatio = (double) compressedData.length / originalData.length * 100;
                double compressionSpeed = originalData.length / (compressionWatch.getTotalTimeMillis() / 1000.0) / (1024 * 1024); // MB/s
                double decompressionSpeed = originalData.length / (decompressionWatch.getTotalTimeMillis() / 1000.0) / (1024 * 1024); // MB/s

                // Afficher les résultats
                System.out.printf("%dx%d pixels avec %s:\n", size, size, type);
                System.out.printf("  - Taille originale: %.2f MB\n", originalData.length / (1024.0 * 1024.0));
                System.out.printf("  - Taille compressée: %.2f MB\n", compressedData.length / (1024.0 * 1024.0));
                System.out.printf("  - Taux de compression: %.2f%%\n", compressionRatio);
                System.out.printf("  - Vitesse de compression: %.2f MB/s\n", compressionSpeed);
                System.out.printf("  - Vitesse de décompression: %.2f MB/s\n", decompressionSpeed);
                System.out.printf("  - Temps de compression: %d ms\n", compressionWatch.getTotalTimeMillis());
                System.out.printf("  - Temps de décompression: %d ms\n", decompressionWatch.getTotalTimeMillis());
                System.out.println("------------------------------------------------------------------------");
            }
        }
    }

    @Test
    void testCompressionDashboard(@TempDir Path tempDir) throws IOException {
        Map<String, Map<String, Double>> metrics = new HashMap<>();
        String[] fileTypes = {"text", "image", "binary"};
        int[] fileSizes = {1, 10, 50}; // MB

        System.out.println("\n=== Dashboard de Performance de Compression ===");
        System.out.println("Format: Type | Taille | Algorithme | Compression (ms) | Décompression (ms) | Ratio | Vitesse (MB/s)");
        System.out.println("--------------------------------------------------------------------------------");

        for (String fileType : fileTypes) {
            metrics.put(fileType, new HashMap<>());
            for (int size : fileSizes) {
                File testFile = createTestFile(tempDir, fileType, size * 1024 * 1024);
                byte[] originalData = readFileToBytes(testFile);
                
                for (CompressionService.CompressionType type : COMPRESSION_TYPES) {
                    PerformanceMetrics perfMetrics = measureCompression(originalData, type);
                    
                    System.out.printf("%s (%dMB) avec %s:\n", fileType, size, type);
                    System.out.printf("  - Temps compression: %.2f ms\n", perfMetrics.compressionTime);
                    System.out.printf("  - Temps décompression: %.2f ms\n", perfMetrics.decompressionTime);
                    System.out.printf("  - Taux de compression: %.2f%%\n", perfMetrics.compressionRatio);
                    System.out.printf("  - Vitesse moyenne: %.2f MB/s\n", perfMetrics.averageSpeed);
                    System.out.println("--------------------------------------------------------------------------------");
                }
            }
        }
    }

    private File createTestImage(Path tempDir, int size) throws IOException {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        // Créer un motif simple
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                image.setRGB(x, y, (x + y) % 256 * 65536);
            }
        }
        File imageFile = tempDir.resolve("test_" + size + ".png").toFile();
        ImageIO.write(image, "PNG", imageFile);
        return imageFile;
    }

    private File createTestFile(Path tempDir, String type, int size) throws IOException {
        File file = tempDir.resolve("test_" + type + "_" + size + ".dat").toFile();
        try (FileOutputStream fos = new FileOutputStream(file)) {
            byte[] buffer = new byte[8192];
            int remaining = size;
            while (remaining > 0) {
                int toWrite = Math.min(remaining, buffer.length);
                if ("text".equals(type)) {
                    // Données texte répétitives
                    for (int i = 0; i < toWrite; i++) {
                        buffer[i] = (byte) ((i % 26) + 'a');
                    }
                } else if ("binary".equals(type)) {
                    // Données binaires pseudo-aléatoires
                    for (int i = 0; i < toWrite; i++) {
                        buffer[i] = (byte) (Math.random() * 256);
                    }
                }
                fos.write(buffer, 0, toWrite);
                remaining -= toWrite;
            }
        }
        return file;
    }

    private byte[] readFileToBytes(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            return fis.readAllBytes();
        }
    }

    private static class PerformanceMetrics {
        double compressionTime;
        double decompressionTime;
        double compressionRatio;
        double averageSpeed;
    }

    private PerformanceMetrics measureCompression(byte[] originalData, CompressionService.CompressionType type) throws IOException {
        PerformanceMetrics metrics = new PerformanceMetrics();

        // Mesurer la compression
        StopWatch watch = new StopWatch();
        watch.start();
        byte[] compressedData = compressionService.compress(originalData, type);
        watch.stop();
        metrics.compressionTime = watch.getTotalTimeMillis();

        // Mesurer la décompression
        watch = new StopWatch();
        watch.start();
        byte[] decompressedData = compressionService.decompress(compressedData, type, originalData.length);
        watch.stop();
        metrics.decompressionTime = watch.getTotalTimeMillis();

        // Calculer les métriques
        metrics.compressionRatio = (double) compressedData.length / originalData.length * 100;
        double totalTime = (metrics.compressionTime + metrics.decompressionTime) / 1000.0; // en secondes
        metrics.averageSpeed = (originalData.length / (1024.0 * 1024.0)) / totalTime; // MB/s

        return metrics;
    }
}
