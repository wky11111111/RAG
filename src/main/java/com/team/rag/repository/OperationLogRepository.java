package com.team.rag.repository;

import com.team.rag.entity.OperationLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OperationLogRepository extends JpaRepository<OperationLogEntity, Long> {

    List<OperationLogEntity> findTop200ByTraceIdOrderByTimestampAsc(String traceId);

    List<OperationLogEntity> findTop200ByUserIdOrderByTimestampDesc(String userId);

    List<OperationLogEntity> findTop200ByOrderByTimestampDesc();
}
