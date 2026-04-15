package com.team.rag.service;

import com.team.rag.bean.EvaluationCaseRequest;
import com.team.rag.bean.EvaluationCaseResult;
import com.team.rag.bean.EvaluationRunRequest;
import com.team.rag.bean.EvaluationRunResponse;
import com.team.rag.bean.RagAnswerResponse;
import com.team.rag.bean.RagQo;
import com.team.rag.bean.RetrievedChunk;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class EvaluationService {

    private final RagQAService ragQAService;

    public EvaluationService(RagQAService ragQAService) {
        this.ragQAService = ragQAService;
    }

    public EvaluationRunResponse run(EvaluationRunRequest request) {
        List<EvaluationCaseRequest> cases = request == null || request.cases() == null
                ? List.of()
                : request.cases();
        List<EvaluationCaseResult> results = cases.stream()
                .map(item -> evaluate(item, request.topK()))
                .toList();

        double averageRecall = results.stream().mapToDouble(EvaluationCaseResult::recallAtK).average().orElse(0.0);
        double averagePrecision = results.stream().mapToDouble(EvaluationCaseResult::precisionAtK).average().orElse(0.0);
        double hitRate = results.isEmpty()
                ? 0.0
                : results.stream().filter(EvaluationCaseResult::hitExpectedDocument).count() / (double) results.size();
        return new EvaluationRunResponse(results.size(), averageRecall, averagePrecision, hitRate, results);
    }

    private EvaluationCaseResult evaluate(EvaluationCaseRequest item, Integer topK) {
        RagQo qo = new RagQo();
        qo.setQuery(item.query());
        qo.setTopK(topK);
        qo.setSourceKind(item.sourceKind());
        qo.setCategoryPath(item.categoryPath());
        qo.setBusinessScene(item.businessScene());
        qo.setFileType(item.fileType());
        qo.setProjectName(item.projectName());
        qo.setDepartment(item.department());
        qo.setUserId(item.userId());
        qo.setConversationId(item.conversationId());

        RagAnswerResponse response = ragQAService.qa(qo);
        Set<String> actual = new LinkedHashSet<>();
        for (RetrievedChunk chunk : response.retrievedChunks()) {
            actual.add(chunk.documentId());
        }
        Set<String> expected = new LinkedHashSet<>(item.expectedDocumentIds() == null ? List.of() : item.expectedDocumentIds());
        long hits = actual.stream().filter(expected::contains).count();
        double recall = expected.isEmpty() ? 0.0 : hits / (double) expected.size();
        double precision = actual.isEmpty() ? 0.0 : hits / (double) actual.size();
        return new EvaluationCaseResult(
                item.query(),
                hits > 0,
                recall,
                precision,
                List.copyOf(expected),
                List.copyOf(actual),
                response.answer()
        );
    }
}
