package com.goofy.GoofyFiles.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.goofy.GoofyFiles.compression.CompressionService;
import com.goofy.GoofyFiles.model.FileChunkEntity;
import com.goofy.GoofyFiles.model.FileEntity;
import com.goofy.GoofyFiles.repository.FileChunkRepository;
import com.goofy.GoofyFiles.repository.FileRepository;

@Service
public class FileReconstructionService {
    private static final Logger logger = LoggerFactory.getLogger(FileReconstructionService.class);

    private final FileRepository fileRepository;
    private final FileChunkRepository fileChunkRepository;
    private final CompressionService compressionService;

    @Autowired
    public FileReconstructionService(
            FileRepository fileRepository,
            FileChunkRepository fileChunkRepository,
            CompressionService compressionService) {
        this.fileRepository = fileRepository;
        this.fileChunkRepository = fileChunkRepository;
        this.compressionService = compressionService;
    }

    @Transactional(readOnly = true)
    public byte[] reconstructFile(Long fileId) throws IOException {
        FileEntity file = fileRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("Fichier non trouvé: " + fileId));

        List<FileChunkEntity> chunks = fileChunkRepository.findByFileIdOrderByPosition(fileId);
        if (chunks.isEmpty()) {
            throw new IllegalStateException("Aucun chunk trouvé pour le fichier: " + fileId);
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        
        for (FileChunkEntity chunk : chunks) {
            byte[] chunkData = chunk.getChunk().getData();
            
            // Si le chunk est compressé, le décompresser
            String compressionType = chunk.getChunk().getCompressionType();
            if (compressionType != null) {
                try {
                    // Si la taille originale n'est pas définie, utiliser la taille des données compressées
                    int originalSize = chunk.getChunk().getOriginalSize() != null 
                        ? chunk.getChunk().getOriginalSize() 
                        : chunkData.length * 2; // Estimation conservatrice

                    chunkData = compressionService.decompress(
                        chunkData, 
                        CompressionService.CompressionType.valueOf(compressionType),
                        originalSize
                    );
                    
                    logger.debug("Chunk décompressé à la position {}: taille originale={}, taille décompressée={}", 
                        chunk.getPosition(), 
                        originalSize,
                        chunkData.length);
                        
                } catch (Exception e) {
                    logger.error("Erreur lors de la décompression du chunk à la position {}", chunk.getPosition(), e);
                    throw new IOException("Erreur de décompression", e);
                }
            }
            
            outputStream.write(chunkData);
        }

        byte[] reconstructedFile = outputStream.toByteArray();
        logger.info("Fichier reconstruit: id={}, nom={}, taille={} octets", 
            fileId, file.getName(), reconstructedFile.length);

        return reconstructedFile;
    }
}
