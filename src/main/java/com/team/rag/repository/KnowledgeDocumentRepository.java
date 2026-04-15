package com.team.rag.repository;

import com.team.rag.entity.KnowledgeDocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocumentEntity, Long> {

    Optional<KnowledgeDocumentEntity> findByDocumentUuid(String documentUuid);

    Optional<KnowledgeDocumentEntity> findFirstBySourceKindAndUserIdAndChecksumAndIndexStatusOrderByUploadedAtDesc(
            String sourceKind,
            String userId,
            String checksum,
            String indexStatus
    );

    List<KnowledgeDocumentEntity> findByUserId(String userId);

    @Query("select coalesce(sum(d.fileSizeBytes), 0) from KnowledgeDocumentEntity d where d.userId = :userId")
    Long sumFileSizeByUserId(String userId);
}
