package com.team.rag.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SemanticSplitter {

    private static final Pattern SENTENCE_PATTERN = Pattern.compile("[^。！？!?；;\\n]+[。！？!?；;\\n]?");

    @Value("${rag.chunk.max-length}")
    private int maxLength;

    @Value("${rag.chunk.overlap}")
    private int overlap;

    public List<String> split(String text) {
        String normalized = normalize(text);
        if (normalized.isBlank()) {
            return List.of();
        }

        List<String> baseChunks = new ArrayList<>();
        List<String> paragraphs = Arrays.stream(normalized.split("\\n\\s*\\n"))
                .map(String::trim)
                .filter(part -> !part.isBlank())
                .toList();

        StringBuilder current = new StringBuilder();
        for (String paragraph : paragraphs) {
            if (paragraph.length() > maxLength) {
                flush(baseChunks, current);
                baseChunks.addAll(splitLongParagraph(paragraph));
                continue;
            }

            if (current.length() == 0) {
                current.append(paragraph);
                continue;
            }

            if (current.length() + paragraph.length() + 2 <= maxLength) {
                current.append("\n\n").append(paragraph);
            } else {
                flush(baseChunks, current);
                current.append(paragraph);
            }
        }
        flush(baseChunks, current);
        return addOverlap(baseChunks);
    }

    private List<String> splitLongParagraph(String paragraph) {
        List<String> chunks = new ArrayList<>();
        List<String> sentences = splitSentences(paragraph);
        if (sentences.isEmpty()) {
            return fixedWindows(paragraph);
        }

        StringBuilder current = new StringBuilder();
        for (String sentence : sentences) {
            if (sentence.length() > maxLength) {
                flush(chunks, current);
                chunks.addAll(fixedWindows(sentence));
                continue;
            }

            if (current.length() == 0) {
                current.append(sentence);
                continue;
            }

            if (current.length() + sentence.length() <= maxLength) {
                current.append(sentence);
            } else {
                flush(chunks, current);
                current.append(sentence);
            }
        }
        flush(chunks, current);
        return chunks;
    }

    private List<String> fixedWindows(String text) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + maxLength);
            chunks.add(text.substring(start, end).trim());
            if (end >= text.length()) {
                break;
            }
            start = Math.max(end - overlap, start + 1);
        }
        return chunks;
    }

    private List<String> addOverlap(List<String> chunks) {
        List<String> result = new ArrayList<>();
        String previous = "";
        for (String chunk : chunks) {
            if (previous.isBlank()) {
                result.add(chunk);
            } else {
                String tail = previous.substring(Math.max(0, previous.length() - overlap));
                result.add((tail + "\n" + chunk).trim());
            }
            previous = chunk;
        }
        return result;
    }

    private List<String> splitSentences(String text) {
        List<String> sentences = new ArrayList<>();
        Matcher matcher = SENTENCE_PATTERN.matcher(text);
        while (matcher.find()) {
            String sentence = matcher.group().trim();
            if (!sentence.isBlank()) {
                sentences.add(sentence);
            }
        }
        return sentences;
    }

    private String normalize(String text) {
        return text == null ? "" : text
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace("\uFEFF", "")
                .replaceAll("[\\t\\x0B\\f]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private void flush(List<String> chunks, StringBuilder buffer) {
        if (buffer.length() == 0) {
            return;
        }
        chunks.add(buffer.toString().trim());
        buffer.setLength(0);
    }
}
