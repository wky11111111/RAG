package com.team.rag.repository;

import com.team.rag.entity.KnowledgeChunkEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunkEntity, Long> {

    List<KnowledgeChunkEntity> findAllByOrderByIdAsc();

    List<KnowledgeChunkEntity> findByChunkUuidIn(Collection<String> chunkUuids);

    List<KnowledgeChunkEntity> findByDocumentDocumentUuidOrderByChunkIndexAsc(String documentUuid);

    void deleteByDocumentDocumentUuid(String documentUuid);
}
