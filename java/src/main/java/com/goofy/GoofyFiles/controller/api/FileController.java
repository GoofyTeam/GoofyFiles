package com.goofy.GoofyFiles.controller.api;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.goofy.GoofyFiles.model.FileEntity;
import com.goofy.GoofyFiles.repository.FileRepository;
import com.goofy.GoofyFiles.service.FileReconstructionService;

@RestController
@RequestMapping("api/files")
public class FileController {

    private final FileReconstructionService fileReconstructionService;
    private final FileRepository fileRepository;

    @Autowired
    public FileController(FileReconstructionService fileReconstructionService, FileRepository fileRepository) {
        this.fileReconstructionService = fileReconstructionService;
        this.fileRepository = fileRepository;
    }

    @GetMapping("/{fileId}/download")
    public ResponseEntity<?> downloadFile(@PathVariable Long fileId) {
        try {
            FileEntity fileEntity = fileRepository.findById(fileId)
                    .orElseThrow(() -> new IllegalArgumentException("Fichier non trouvé: " + fileId));

            byte[] fileContent = fileReconstructionService.reconstructFile(fileId);
            
            String fileName = fileEntity.getName();
            if (fileEntity.getExtension() != null && !fileEntity.getExtension().isEmpty()) {
                fileName += "." + fileEntity.getExtension();
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .body(new ByteArrayResource(fileContent));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                    .body("Erreur lors de la reconstruction du fichier: " + e.getMessage());
        }
    }
}
