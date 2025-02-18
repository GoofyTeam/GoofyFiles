package com.goofy.GoofyFiles.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.goofy.GoofyFiles.model.FileEntity;

public interface FileRepository extends JpaRepository<FileEntity, Long> {
}