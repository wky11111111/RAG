package com.team.rag.util;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TextTokenizer {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{IsHan}]+|[a-zA-Z0-9_]+");

    public List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return tokens;
        }

        Matcher matcher = TOKEN_PATTERN.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String block = matcher.group();
            if (block.chars().allMatch(TextTokenizer::isChineseChar)) {
                appendChineseTokens(block, tokens);
            } else if (block.length() > 1 || Character.isDigit(block.charAt(0))) {
                tokens.add(block);
            }
        }
        return tokens;
    }

    public Map<String, Integer> countTokens(List<String> tokens) {
        Map<String, Integer> counter = new HashMap<>();
        for (String token : tokens) {
            counter.merge(token, 1, Integer::sum);
        }
        return counter;
    }

    private void appendChineseTokens(String text, List<String> tokens) {
        for (int i = 0; i < text.length(); i++) {
            tokens.add(String.valueOf(text.charAt(i)));
            if (i < text.length() - 1) {
                tokens.add(text.substring(i, i + 2));
            }
        }
    }

    private static boolean isChineseChar(int codePoint) {
        Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
        return script == Character.UnicodeScript.HAN;
    }
}
