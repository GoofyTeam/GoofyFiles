package com.goofy.GoofyFiles.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.goofy.GoofyFiles.model.ChunkEntity;

public interface ChunkRepository extends JpaRepository<ChunkEntity, Long> {

  Optional<ChunkEntity> findByHashSha1(String hash);

  Optional<ChunkEntity> findByHashSha256(String hash);

  Optional<ChunkEntity> findByHashBlake3(String hash);
}