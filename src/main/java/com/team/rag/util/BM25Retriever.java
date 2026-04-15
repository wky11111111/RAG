package com.team.rag.util;

import com.team.rag.bean.RetrievedChunk;
import com.team.rag.bean.SearchFilter;
import com.team.rag.config.RagProperties;
import com.team.rag.entity.KnowledgeChunkEntity;
import com.team.rag.service.KnowledgeBaseService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class BM25Retriever {

    private final KnowledgeBaseService knowledgeBaseService;
    private final TextTokenizer textTokenizer;
    private final RagProperties ragProperties;

    public BM25Retriever(KnowledgeBaseService knowledgeBaseService,
                         TextTokenizer textTokenizer,
                         RagProperties ragProperties) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.textTokenizer = textTokenizer;
        this.ragProperties = ragProperties;
    }

    public List<RetrievedChunk> retrieve(String query) {
        return retrieve(query, SearchFilter.empty());
    }

    public List<RetrievedChunk> retrieve(String query, SearchFilter filter) {
        List<String> queryTokens = textTokenizer.tokenize(query);
        if (queryTokens.isEmpty()) {
            return List.of();
        }

        List<KnowledgeChunkEntity> chunks = knowledgeBaseService.getChunks(filter);
        if (chunks.isEmpty()) {
            return List.of();
        }

        Map<String, Long> documentFrequency = new HashMap<>();
        Map<String, List<String>> chunkTokens = new HashMap<>();
        double totalLength = 0.0;
        for (KnowledgeChunkEntity chunk : chunks) {
            List<String> tokens = textTokenizer.tokenize(chunk.getContent());
            chunkTokens.put(chunk.getChunkUuid(), tokens);
            totalLength += tokens.size();

            Set<String> seen = new HashSet<>(tokens);
            for (String token : seen) {
                documentFrequency.merge(token, 1L, Long::sum);
            }
        }

        double avgDocLength = Math.max(1.0, totalLength / chunks.size());
        Set<String> uniqueQueryTerms = new HashSet<>(queryTokens);
        List<RetrievedChunk> hits = new ArrayList<>();

        for (KnowledgeChunkEntity chunk : chunks) {
            List<String> tokens = chunkTokens.get(chunk.getChunkUuid());
            if (tokens.isEmpty()) {
                continue;
            }
            Map<String, Integer> tf = textTokenizer.countTokens(tokens);
            double score = 0.0;
            for (String term : uniqueQueryTerms) {
                int frequency = tf.getOrDefault(term, 0);
                if (frequency == 0) {
                    continue;
                }
                long df = documentFrequency.getOrDefault(term, 0L);
                double idf = Math.log(1.0 + (chunks.size() - df + 0.5) / (df + 0.5));
                double k1 = 1.5;
                double b = 0.75;
                double denominator = frequency + k1 * (1 - b + b * tokens.size() / avgDocLength);
                score += idf * (frequency * (k1 + 1)) / denominator;
            }
            if (score > 0.0) {
                hits.add(toRetrievedChunk(chunk, score));
            }
        }

        return hits.stream()
                .sorted(Comparator.comparingDouble(RetrievedChunk::score).reversed())
                .limit(ragProperties.getRetrieval().getBm25TopK())
                .toList();
    }

    private RetrievedChunk toRetrievedChunk(KnowledgeChunkEntity chunk, double score) {
        return new RetrievedChunk(
                chunk.getChunkUuid(),
                chunk.getDocument().getDocumentUuid(),
                chunk.getDocument().getName(),
                chunk.getDocument().getDocType(),
                score,
                chunk.getContent()
        );
    }
}
