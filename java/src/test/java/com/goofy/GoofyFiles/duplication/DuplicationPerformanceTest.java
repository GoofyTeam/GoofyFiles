package com.goofy.GoofyFiles.duplication;

import com.goofy.GoofyFiles.chunking.ChunkingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Map;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class DuplicationPerformanceTest {

    private DuplicationService duplicationService;
    private ChunkingService chunkingService;

    @BeforeEach
    void setUp() throws NoSuchAlgorithmException {
        chunkingService = new ChunkingService();
        duplicationService = new DuplicationService(chunkingService);
    }

    @Test
    void testDuplicationDetectionWithDifferentAlgorithms(@TempDir Path tempDir) throws IOException {
        // Créer un fichier de test avec des données répétitives (1MB)
        File testFile = createTestFile(tempDir, 1024 * 1024);

        // Tester avec SHA-1
        long startTime = System.nanoTime();
        Map<String, Object> sha1Results = duplicationService.analyzeFile(testFile, HashingAlgorithm.SHA1);
        long sha1Time = System.nanoTime() - startTime;

        // Tester avec SHA-256
        startTime = System.nanoTime();
        Map<String, Object> sha256Results = duplicationService.analyzeFile(testFile, HashingAlgorithm.SHA256);
        long sha256Time = System.nanoTime() - startTime;

        // Tester avec BLAKE3
        startTime = System.nanoTime();
        Map<String, Object> blake3Results = duplicationService.analyzeFile(testFile, HashingAlgorithm.BLAKE3);
        long blake3Time = System.nanoTime() - startTime;

        // Affichage des résultats
        System.out.println("=== Résultats des tests de performance ===");
        System.out.println("SHA-1:");
        System.out.println("  - Temps d'exécution: " + sha1Time / 1_000_000.0 + " ms");
        System.out.println("  - Chunks uniques: " + sha1Results.get("uniqueChunks"));
        System.out.println("  - Chunks dupliqués: " + sha1Results.get("duplicatedChunks"));
        System.out.println("  - Détails des doublons: " + sha1Results.get("duplicateDetails"));

        System.out.println("\nSHA-256:");
        System.out.println("  - Temps d'exécution: " + sha256Time / 1_000_000.0 + " ms");
        System.out.println("  - Chunks uniques: " + sha256Results.get("uniqueChunks"));
        System.out.println("  - Chunks dupliqués: " + sha256Results.get("duplicatedChunks"));
        System.out.println("  - Détails des doublons: " + sha256Results.get("duplicateDetails"));

        System.out.println("\nBLAKE3:");
        System.out.println("  - Temps d'exécution: " + blake3Time / 1_000_000.0 + " ms");
        System.out.println("  - Chunks uniques: " + blake3Results.get("uniqueChunks"));
        System.out.println("  - Chunks dupliqués: " + blake3Results.get("duplicatedChunks"));
        System.out.println("  - Détails des doublons: " + blake3Results.get("duplicateDetails"));

        // Vérifications
        assertTrue((Long) sha1Results.get("duplicatedChunks") > 0, "Des doublons devraient être détectés pour SHA-1");
        assertTrue((Long) blake3Results.get("duplicatedChunks") > 0, "Des doublons devraient être détectés pour BLAKE3");
        // Le nombre de chunks uniques doit être identique pour tous les algorithmes
        assertEquals(sha1Results.get("uniqueChunks"), sha256Results.get("uniqueChunks"),
                "Le nombre de chunks uniques doit être le même pour SHA-1 et SHA-256");
        assertEquals(sha1Results.get("uniqueChunks"), blake3Results.get("uniqueChunks"),
                "Le nombre de chunks uniques doit être le même pour SHA-1 et BLAKE3");
    }

    private File createTestFile(Path tempDir, int size) throws IOException {
        File file = tempDir.resolve("test.dat").toFile();
        try (FileOutputStream fos = new FileOutputStream(file)) {
            // Créer quelques patterns fixes pour garantir des doublons
            byte[][] patterns = new byte[4][];
            for (int i = 0; i < patterns.length; i++) {
                patterns[i] = new byte[8192]; // 8KB par pattern
                Arrays.fill(patterns[i], (byte)i);
            }
            // Écrire les patterns de manière répétitive
            Random random = new Random(42);
            int written = 0;
            while (written < size) {
                byte[] pattern = patterns[random.nextInt(patterns.length)];
                fos.write(pattern);
                written += pattern.length;
            }
        }
        return file;
    }
}
