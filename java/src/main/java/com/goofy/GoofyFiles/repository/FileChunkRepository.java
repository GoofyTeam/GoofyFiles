package com.goofy.GoofyFiles.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.goofy.GoofyFiles.model.FileChunkEntity;

public interface FileChunkRepository extends JpaRepository<FileChunkEntity, Long> {
}