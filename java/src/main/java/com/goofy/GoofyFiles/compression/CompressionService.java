package com.goofy.GoofyFiles.compression;

import java.nio.ByteBuffer;
import org.springframework.stereotype.Service;
import org.xerial.snappy.Snappy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.luben.zstd.Zstd;

import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FastDecompressor;

@Service
public class CompressionService {
    private static final Logger logger = LoggerFactory.getLogger(CompressionService.class);
    private final LZ4Factory lz4Factory;
    private final LZ4Compressor lz4Compressor;
    private final LZ4FastDecompressor lz4Decompressor;

    public enum CompressionType {
        LZ4,
        ZSTD,
        SNAPPY
    }

    public CompressionService() {
        this.lz4Factory = LZ4Factory.fastestInstance();
        this.lz4Compressor = lz4Factory.fastCompressor();
        this.lz4Decompressor = lz4Factory.fastDecompressor();
    }

    public byte[] compress(byte[] data, CompressionType type) {
        try {
            return switch (type) {
                case LZ4 -> compressLZ4(data);
                case ZSTD -> compressZstd(data);
                case SNAPPY -> compressSnappy(data);
            };
        } catch (Exception e) {
            throw new RuntimeException("Compression failed", e);
        }
    }

    public byte[] decompress(byte[] compressedData, CompressionType type, int originalLength) {
        try {
            return switch (type) {
                case LZ4 -> decompressLZ4(compressedData);
                case ZSTD -> decompressZstd(compressedData);
                case SNAPPY -> decompressSnappy(compressedData);
            };
        } catch (Exception e) {
            logger.error("Décompression échouée pour le type {}: longueur originale={}, données compressées={}",
                type, originalLength, compressedData.length, e);
            throw new RuntimeException("Decompression failed", e);
        }
    }

    private byte[] compressLZ4(byte[] data) {
        byte[] compressed = lz4Compressor.compress(data);
        
        // Format: [4 bytes for original length][4 bytes for compressed length][compressed data]
        ByteBuffer buffer = ByteBuffer.allocate(8 + compressed.length);
        buffer.putInt(data.length);          // Taille originale
        buffer.putInt(compressed.length);     // Taille compressée
        buffer.put(compressed);              // Données compressées
        return buffer.array();
    }

    private byte[] decompressLZ4(byte[] compressedData) {
        try {
            // Lire l'en-tête
            ByteBuffer buffer = ByteBuffer.wrap(compressedData);
            int originalLength = buffer.getInt();
            int compressedLength = buffer.getInt();
            
            // Vérifications de sécurité
            if (originalLength <= 0 || originalLength > 100_000_000) { // 100MB max
                throw new IllegalArgumentException("Taille originale invalide: " + originalLength);
            }
            if (compressedLength <= 0 || compressedLength > compressedData.length - 8) {
                throw new IllegalArgumentException("Taille compressée invalide: " + compressedLength);
            }
            
            // Extraire les données compressées
            byte[] compressedOnly = new byte[compressedLength];
            buffer.get(compressedOnly);
            
            logger.debug("Décompression LZ4: taille originale={}, taille compressée={}", 
                originalLength, compressedLength);
                
            return lz4Decompressor.decompress(compressedOnly, originalLength);
        } catch (Exception e) {
            logger.error("Erreur lors de la décompression LZ4", e);
            throw e;
        }
    }   

    private byte[] compressZstd(byte[] data) {
        return Zstd.compress(data);
    }

    private byte[] decompressZstd(byte[] compressedData) {
        long originalSize = Zstd.getFrameContentSize(compressedData);
        return Zstd.decompress(compressedData, (int) originalSize);
    }

    private byte[] compressSnappy(byte[] data) throws Exception {
        return Snappy.compress(data);
    }

    private byte[] decompressSnappy(byte[] compressedData) throws Exception {
        return Snappy.uncompress(compressedData);
    }
}
