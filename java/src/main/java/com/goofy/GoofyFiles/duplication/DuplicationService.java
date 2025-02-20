package com.goofy.GoofyFiles.duplication;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.Blake3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.goofy.GoofyFiles.chunking.Chunk;
import com.goofy.GoofyFiles.chunking.ChunkingService;
import com.goofy.GoofyFiles.compression.CompressionService;
import com.goofy.GoofyFiles.compression.CompressionService.CompressionType;
import com.goofy.GoofyFiles.model.ChunkEntity;
import com.goofy.GoofyFiles.model.FileChunkEntity;
import com.goofy.GoofyFiles.model.FileEntity;
import com.goofy.GoofyFiles.repository.ChunkRepository;
import com.goofy.GoofyFiles.repository.FileChunkRepository;
import com.goofy.GoofyFiles.repository.FileRepository;
import com.google.common.hash.Hashing;

@Service
public class DuplicationService {

  private static final Logger logger = LoggerFactory.getLogger(DuplicationService.class);

  private final ChunkingService chunkingService;
  private final FileRepository fileRepository;
  private final ChunkRepository chunkRepository;
  private final FileChunkRepository fileChunkRepository;
  private final CompressionService compressionService;

  /**
   * Constructeur principal pour l'utilisation en production
   */
  @Autowired
  public DuplicationService(
      ChunkingService chunkingService,
      FileRepository fileRepository,
      ChunkRepository chunkRepository,
      FileChunkRepository fileChunkRepository,
      CompressionService compressionService) {
    this.chunkingService = chunkingService;
    this.fileRepository = fileRepository;
    this.chunkRepository = chunkRepository;
    this.fileChunkRepository = fileChunkRepository;
    this.compressionService = compressionService;
  }

  /**
   * Constructeur simplifié pour les tests
   * Ne prend que le ChunkingService, les opérations de base de données ne seront
   * pas disponibles
   */
  public DuplicationService(ChunkingService chunkingService) {
    this(chunkingService, null, null, null, null);
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
                Map.Entry::getValue)));
  }

  private String calculateHash(byte[] data, HashingAlgorithm algorithm) {
    try {
      switch (algorithm) {
        case SHA1:
          return Hashing.sha1().hashBytes(data).toString();
        case SHA256:
          return Hashing.sha256().hashBytes(data).toString();
        case BLAKE3:
          byte[] hashBytes = Blake3.hash(data);
          return Hex.encodeHexString(hashBytes);
        default:
          throw new IllegalArgumentException("Algorithme de hachage non supporté: " + algorithm);
      }
    } catch (Exception e) {
      throw new RuntimeException("Erreur lors du calcul du hash", e);
    }
  }

  @Transactional
  public Map<String, Object> processAndStoreFile(
      File file,
      String fileName,
      long fileSize,
      HashingAlgorithm algorithm) throws IOException {
    if (fileRepository == null || chunkRepository == null || fileChunkRepository == null) {
      throw new UnsupportedOperationException(
          "Cette méthode nécessite les repositories qui n'ont pas été injectés. " +
              "Utilisez le constructeur avec tous les paramètres pour cette fonctionnalité.");
    }

    // 1. Extraire le nom et l'extension
    String name = fileName;
    String extension = "";
    int lastDotIndex = fileName.lastIndexOf('.');
    if (lastDotIndex > 0) {
      name = fileName.substring(0, lastDotIndex);
      extension = fileName.substring(lastDotIndex + 1);
    }

    // 2. Créer et sauvegarder l'entité de fichier
    FileEntity fileEntity = new FileEntity();
    fileEntity.setName(name);
    fileEntity.setExtension(extension);
    fileEntity.setSize(fileSize);
    fileEntity = fileRepository.save(fileEntity);

    // 3. Découper le fichier
    List<Chunk> chunks = chunkingService.chunkFile(file);

    // Statistiques pour le résultat
    int totalChunks = chunks.size();
    int duplicateChunks = 0;
    int uniqueChunks = 0;
    long savedStorage = 0;

    // 4. Traiter chaque chunk
    for (Chunk chunk : chunks) {
      String hash = calculateHash(chunk.getData(), algorithm);

      // Chercher si ce chunk existe déjà en base
      Optional<ChunkEntity> existingChunk;
      switch (algorithm) {
        case SHA1:
          existingChunk = chunkRepository.findByHashSha1(hash);
          break;
        case SHA256:
          existingChunk = chunkRepository.findByHashSha256(hash);
          break;
        case BLAKE3:
          existingChunk = chunkRepository.findByHashBlake3(hash);
          break;
        default:
          existingChunk = Optional.empty();
      }

      ChunkEntity chunkEntity;
      if (existingChunk.isPresent()) {
        chunkEntity = existingChunk.get();
        duplicateChunks++;
        savedStorage += chunk.getOriginalSize();
        logger.info("Chunk dupliqué trouvé: {}", hash);
      } else {
        chunkEntity = new ChunkEntity();
        chunkEntity.setData(chunk.getData());

        // Stocker le hash selon l'algorithme
        switch (algorithm) {
          case SHA1:
            chunkEntity.setHashSha1(hash);
            break;
          case SHA256:
            chunkEntity.setHashSha256(hash);
            break;
          case BLAKE3:
            chunkEntity.setHashBlake3(hash);
            break;
        }

        chunkEntity = chunkRepository.save(chunkEntity);
        uniqueChunks++;
      }

      // Créer la relation entre le fichier et le chunk
      FileChunkEntity fileChunk = new FileChunkEntity();
      fileChunk.setFile(fileEntity);
      fileChunk.setChunk(chunkEntity);
      fileChunk.setPosition(chunk.getPosition());
      fileChunkRepository.save(fileChunk);
    }

    // 5. Préparer le résultat
    Map<String, Object> result = new HashMap<>();
    result.put("fileId", fileEntity.getId());
    result.put("fileName", fileEntity.getName());
    result.put("extension", fileEntity.getExtension());
    result.put("fileSize", fileEntity.getSize());
    result.put("algorithm", algorithm.name());
    result.put("totalChunks", totalChunks);
    result.put("uniqueChunks", uniqueChunks);
    result.put("duplicateChunks", duplicateChunks);
    result.put("savedStorage", savedStorage);
    result.put("deduplicationRatio", totalChunks > 0 ? (double) duplicateChunks / totalChunks : 0);

    logger.info("Fichier traité: id={}, nom={}, chunks={}, uniques={}, doublons={}",
        fileEntity.getId(), fileName, totalChunks, uniqueChunks, duplicateChunks);

    return result;
  }

  @Transactional
  public Map<String, Object> processAndStoreFileCompressed(
      File file,
      String fileName,
      long fileSize,
      HashingAlgorithm algorithm,
      CompressionType compressionType) throws IOException {
    if (fileRepository == null || chunkRepository == null || fileChunkRepository == null
        || compressionService == null) {
      throw new UnsupportedOperationException(
          "Cette méthode nécessite les repositories et le service de compression qui n'ont pas été injectés. " +
              "Utilisez le constructeur avec tous les paramètres pour cette fonctionnalité.");
    }

    // 1. Extraire le nom et l'extension
    String name = fileName;
    String extension = "";
    int lastDotIndex = fileName.lastIndexOf('.');
    if (lastDotIndex > 0) {
      name = fileName.substring(0, lastDotIndex);
      extension = fileName.substring(lastDotIndex + 1);
    }

    // 2. Créer et sauvegarder l'entité de fichier
    FileEntity fileEntity = new FileEntity();
    fileEntity.setName(name);
    fileEntity.setExtension(extension);
    fileEntity.setSize(fileSize);
    fileEntity = fileRepository.save(fileEntity);

    // 3. Découper le fichier
    List<Chunk> chunks = chunkingService.chunkFile(file);

    // Statistiques pour le résultat
    int totalChunks = chunks.size();
    int duplicateChunks = 0;
    int uniqueChunks = 0;
    long savedStorage = 0;
    long totalCompressedSize = 0;

    // 4. Traiter chaque chunk
    for (Chunk chunk : chunks) {
      String hash = calculateHash(chunk.getData(), algorithm);

      // Chercher si ce chunk existe déjà en base
      Optional<ChunkEntity> existingChunk;
      switch (algorithm) {
        case SHA1:
          existingChunk = chunkRepository.findByHashSha1(hash);
          break;
        case SHA256:
          existingChunk = chunkRepository.findByHashSha256(hash);
          break;
        case BLAKE3:
          existingChunk = chunkRepository.findByHashBlake3(hash);
          break;
        default:
          existingChunk = Optional.empty();
      }

      ChunkEntity chunkEntity;
      if (existingChunk.isPresent()) {
        chunkEntity = existingChunk.get();
        duplicateChunks++;
        savedStorage += chunk.getOriginalSize();
        logger.info("Chunk dupliqué trouvé: {}", hash);
      } else {
        // Compression du chunk
        byte[] compressedData = compressionService.compress(chunk.getData(), compressionType);
        totalCompressedSize += compressedData.length;

        chunkEntity = new ChunkEntity();
        // Stocker les données compressées
        chunkEntity.setData(compressedData);
        // Vous pouvez ajouter une propriété pour stocker la taille originale si besoin,
        // ex :
        chunkEntity.setCompressionType(compressionType.name());

        // Stocker le hash selon l'algorithme
        switch (algorithm) {
          case SHA1:
            chunkEntity.setHashSha1(hash);
            break;
          case SHA256:
            chunkEntity.setHashSha256(hash);
            break;
          case BLAKE3:
            chunkEntity.setHashBlake3(hash);
            break;
        }

        chunkEntity = chunkRepository.save(chunkEntity);
        uniqueChunks++;
      }

      // Créer la relation entre le fichier et le chunk
      FileChunkEntity fileChunk = new FileChunkEntity();
      fileChunk.setFile(fileEntity);
      fileChunk.setChunk(chunkEntity);
      fileChunk.setPosition(chunk.getPosition());
      fileChunkRepository.save(fileChunk);
    }

    // 5. Préparer le résultat
    Map<String, Object> result = new HashMap<>();
    result.put("fileId", fileEntity.getId());
    result.put("fileName", fileEntity.getName());
    result.put("extension", fileEntity.getExtension());
    result.put("fileSize", fileEntity.getSize());
    result.put("algorithm", algorithm.name());
    result.put("compressionType", compressionType.name());
    result.put("totalChunks", totalChunks);
    result.put("uniqueChunks", uniqueChunks);
    result.put("duplicateChunks", duplicateChunks);
    result.put("savedStorage", savedStorage);
    result.put("deduplicationRatio", totalChunks > 0 ? (double) duplicateChunks / totalChunks : 0);
    result.put("totalCompressedSize", totalCompressedSize);

    logger.info("Fichier compressé traité: id={}, nom={}, chunks={}, uniques={}, doublons={}, taille compressée={}",
        fileEntity.getId(), fileName, totalChunks, uniqueChunks, duplicateChunks, totalCompressedSize);

    return result;
  }
}
