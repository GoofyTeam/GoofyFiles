package com.goofy.GoofyFiles.duplication;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.goofy.GoofyFiles.chunking.Chunk;
import com.goofy.GoofyFiles.chunking.ChunkingService;
import com.google.common.hash.Hashing;

@Service
public class DuplicationService {

    private static final Logger logger = LoggerFactory.getLogger(DuplicationService.class);
    private final ChunkingService chunkingService;

    public DuplicationService(ChunkingService chunkingService) {
        this.chunkingService = chunkingService;
    }

    public Map<String, Object> analyzeFile(File file, HashingAlgorithm algorithm) throws IOException {
        List<Chunk> chunks = chunkingService.chunkFile(file);
        Map<String, Integer> duplicates = new HashMap<>();
        
        for (Chunk chunk : chunks) {
            String hash = calculateHash(chunk.getData(), algorithm);
            duplicates.merge(hash, 1, Integer::sum);
            logger.debug("Chunk at position {} with size {} bytes has hash: {}", 
                      chunk.getPosition(), chunk.getData().length, hash);
        }

        // Log les chunks qui apparaissent plus d'une fois
        duplicates.entrySet().stream()
            .filter(e -> e.getValue() > 1);

        long uniqueChunks = duplicates.size();
        long totalChunks = chunks.size();
        long duplicatedChunks = duplicates.entrySet().stream()
                .filter(e -> e.getValue() > 1)
                .count();

        return Map.of(
            "fileName", file.getName(),
            "totalChunks", totalChunks,
            "uniqueChunks", uniqueChunks,
            "duplicatedChunks", duplicatedChunks,
            "algorithm", algorithm.name(),
            "duplicateDetails", duplicates.entrySet().stream()
                .filter(e -> e.getValue() > 1)
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    Map.Entry::getValue
                ))
        );
    }

    private String calculateHash(byte[] data, HashingAlgorithm algorithm) {
        try {
            switch (algorithm) {
                case SHA1:
                    return Hashing.sha1().hashBytes(data).toString();
                case SHA256:
                    return Hashing.sha256().hashBytes(data).toString();
                case BLAKE3:
                    // return DigestUtils.sha256Hex(data);
                    throw new UnsupportedOperationException("BLAKE3 not supported yet");
                default:
                    throw new IllegalArgumentException("Algorithme de hachage non supporté: " + algorithm);
            }
        } catch (Exception e) {
            throw new RuntimeException("Erreur lors du calcul du hash", e);
        }
    }
}
