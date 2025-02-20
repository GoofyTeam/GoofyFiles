package com.goofy.GoofyFiles.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.util.StopWatch;

import com.goofy.GoofyFiles.compression.CompressionService;
import com.goofy.GoofyFiles.model.ChunkEntity;
import com.goofy.GoofyFiles.model.FileChunkEntity;
import com.goofy.GoofyFiles.model.FileEntity;
import com.goofy.GoofyFiles.repository.FileChunkRepository;
import com.goofy.GoofyFiles.repository.FileRepository;

class FileReconstructionServiceTest {

    @Mock
    private FileRepository fileRepository;

    @Mock
    private FileChunkRepository fileChunkRepository;

    @Mock
    private CompressionService compressionService;

    private FileReconstructionService service;

    private static final int[] FILE_SIZES = { 1, 10, 50, 100 }; // Tailles en MB
    private static final String[] FILE_TYPES = { "text", "binary", "mixed" };
    private static final int[] CHUNK_SIZES = { 1024, 4096, 16384 }; // Tailles en bytes

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new FileReconstructionService(fileRepository, fileChunkRepository, compressionService);
    }

    @Test
    void testReconstructionPerformanceDashboard(@TempDir Path tempDir) throws IOException {
        System.out.println("\n=== Dashboard de Performance de Reconstitution des Fichiers ===");
        System.out.println("Format: Type | Taille | Taille Chunk | Temps Total | Vitesse | Chunks/s");
        System.out.println("------------------------------------------------------------------------");

        for (String fileType : FILE_TYPES) {
            for (int fileSize : FILE_SIZES) {
                for (int chunkSize : CHUNK_SIZES) {
                    // Créer un fichier test et le découper en chunks
                    TestFileData testData = createTestFileWithChunks(tempDir, fileType, fileSize * 1024 * 1024,
                            chunkSize);

                    // Configuration des mocks
                    when(fileRepository.findById(testData.fileEntity.getId()))
                            .thenReturn(Optional.of(testData.fileEntity));
                    when(fileChunkRepository.findByFileIdOrderByPosition(testData.fileEntity.getId()))
                            .thenReturn(testData.fileChunks);

                    // Mesurer la performance de reconstitution
                    StopWatch watch = new StopWatch();
                    watch.start();
                    byte[] reconstructedFile = service.reconstructFile(testData.fileEntity.getId());
                    watch.stop();

                    // Calculer les métriques
                    double totalTimeSeconds = watch.getTotalTimeMillis() / 1000.0;
                    double speedMBps = fileSize / totalTimeSeconds;
                    double chunksPerSecond = testData.fileChunks.size() / totalTimeSeconds;

                    // Afficher les résultats
                    System.out.printf("%s (%dMB, chunks %dKB):\n", fileType, fileSize, chunkSize / 1024);
                    System.out.printf("  - Temps total: %.2f s\n", totalTimeSeconds);
                    System.out.printf("  - Vitesse: %.2f MB/s\n", speedMBps);
                    System.out.printf("  - Chunks traités: %d chunks\n", testData.fileChunks.size());
                    System.out.printf("  - Débit: %.2f chunks/s\n", chunksPerSecond);
                    System.out.printf("  - Taille finale: %.2f MB\n", reconstructedFile.length / (1024.0 * 1024.0));
                    System.out.println("------------------------------------------------------------------------");
                }
            }
        }
    }

    private static class TestFileData {
        FileEntity fileEntity;
        List<FileChunkEntity> fileChunks;
        byte[] originalData;
    }

    private TestFileData createTestFileWithChunks(Path tempDir, String type, int size, int chunkSize)
            throws IOException {
        TestFileData data = new TestFileData();

        // Créer les données du fichier
        data.originalData = new byte[size];
        Random random = new Random();
        if ("text".equals(type)) {
            // Données texte répétitives
            for (int i = 0; i < size; i++) {
                data.originalData[i] = (byte) ((i % 26) + 'a');
            }
        } else if ("binary".equals(type)) {
            // Données binaires aléatoires
            random.nextBytes(data.originalData);
        } else {
            // Données mixtes
            for (int i = 0; i < size; i++) {
                data.originalData[i] = (byte) (i % 256);
            }
        }

        // Créer l'entité fichier
        data.fileEntity = new FileEntity();
        data.fileEntity.setId(random.nextLong());
        data.fileEntity.setName("test_" + type);
        data.fileEntity.setExtension(type);

        // Découper en chunks
        data.fileChunks = new ArrayList<>();
        for (int offset = 0; offset < size; offset += chunkSize) {
            int length = Math.min(chunkSize, size - offset);
            byte[] chunkData = Arrays.copyOfRange(data.originalData, offset, offset + length);

            ChunkEntity chunk = new ChunkEntity();
            chunk.setId(random.nextLong());
            chunk.setData(chunkData);

            FileChunkEntity fileChunk = new FileChunkEntity();
            fileChunk.setFile(data.fileEntity);
            fileChunk.setChunk(chunk);
            fileChunk.setPosition(offset / chunkSize);

            data.fileChunks.add(fileChunk);
        }

        return data;
    }

    @Test
    void testReconstructFile() throws IOException {
        // Préparation des données de test
        Long fileId = 1L;
        FileEntity fileEntity = new FileEntity();
        fileEntity.setId(fileId);
        fileEntity.setName("test");
        fileEntity.setExtension("txt");

        // Création de 3 chunks avec des données différentes
        byte[] chunk1Data = "Hello ".getBytes();
        byte[] chunk2Data = "World".getBytes();
        byte[] chunk3Data = "!".getBytes();

        ChunkEntity chunk1 = new ChunkEntity();
        chunk1.setData(chunk1Data);
        ChunkEntity chunk2 = new ChunkEntity();
        chunk2.setData(chunk2Data);
        ChunkEntity chunk3 = new ChunkEntity();
        chunk3.setData(chunk3Data);

        FileChunkEntity fileChunk1 = new FileChunkEntity();
        fileChunk1.setFile(fileEntity);
        fileChunk1.setChunk(chunk1);
        fileChunk1.setPosition(0);

        FileChunkEntity fileChunk2 = new FileChunkEntity();
        fileChunk2.setFile(fileEntity);
        fileChunk2.setChunk(chunk2);
        fileChunk2.setPosition(1);

        FileChunkEntity fileChunk3 = new FileChunkEntity();
        fileChunk3.setFile(fileEntity);
        fileChunk3.setChunk(chunk3);
        fileChunk3.setPosition(2);

        // Configuration des mocks
        when(fileRepository.findById(fileId)).thenReturn(Optional.of(fileEntity));
        when(fileChunkRepository.findByFileIdOrderByPosition(fileId))
                .thenReturn(Arrays.asList(fileChunk1, fileChunk2, fileChunk3));

        // Test de la reconstruction
        byte[] reconstructedFile = service.reconstructFile(fileId);

        // Vérifications
        assertNotNull(reconstructedFile);
        assertEquals("Hello World!", new String(reconstructedFile));
    }

    @Test
    void testReconstructCompressedFile() throws IOException {
        Long fileId = 1L;
        FileEntity fileEntity = new FileEntity();
        fileEntity.setId(fileId);
        fileEntity.setName("test");
        fileEntity.setExtension("txt");

        // Création d'un chunk compressé
        byte[] compressedData = "compressed".getBytes();
        byte[] originalData = "Hello World!".getBytes();
        int originalSize = originalData.length;

        ChunkEntity chunk = new ChunkEntity();
        chunk.setData(compressedData);
        chunk.setCompressionType(CompressionService.CompressionType.LZ4.name());
        chunk.setOriginalSize(originalSize);

        FileChunkEntity fileChunk = new FileChunkEntity();
        fileChunk.setFile(fileEntity);
        fileChunk.setChunk(chunk);
        fileChunk.setPosition(0);

        // Configuration des mocks
        when(fileRepository.findById(fileId)).thenReturn(Optional.of(fileEntity));
        when(fileChunkRepository.findByFileIdOrderByPosition(fileId))
                .thenReturn(List.of(fileChunk));
        when(compressionService.decompress(compressedData, CompressionService.CompressionType.LZ4, originalSize))
                .thenReturn(originalData);

        // Test de la reconstruction
        byte[] reconstructedFile = service.reconstructFile(fileId);

        // Vérifications
        assertNotNull(reconstructedFile);
        assertEquals("Hello World!", new String(reconstructedFile));
        verify(compressionService).decompress(compressedData, CompressionService.CompressionType.LZ4, originalSize);
    }

    @Test
    void testFileNotFound() {
        Long fileId = 999L;
        when(fileRepository.findById(fileId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> {
            service.reconstructFile(fileId);
        });
    }

    @Test
    void testNoChunksFound() {
        Long fileId = 1L;
        FileEntity fileEntity = new FileEntity();
        fileEntity.setId(fileId);

        when(fileRepository.findById(fileId)).thenReturn(Optional.of(fileEntity));
        when(fileChunkRepository.findByFileIdOrderByPosition(fileId)).thenReturn(List.of());

        assertThrows(IllegalStateException.class, () -> {
            service.reconstructFile(fileId);
        });
    }
}
