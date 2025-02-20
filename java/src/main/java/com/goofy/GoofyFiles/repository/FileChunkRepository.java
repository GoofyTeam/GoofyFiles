package com.goofy.GoofyFiles.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.goofy.GoofyFiles.model.FileChunkEntity;

@Repository
public interface FileChunkRepository extends JpaRepository<FileChunkEntity, Long> {
    List<FileChunkEntity> findByFileIdOrderByPosition(Long fileId);
}