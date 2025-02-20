package com.goofy.GoofyFiles.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.goofy.GoofyFiles.model.FileEntity;

public interface FileRepository extends JpaRepository<FileEntity, Long> {
  Optional<FileEntity> findById(Long id);
}