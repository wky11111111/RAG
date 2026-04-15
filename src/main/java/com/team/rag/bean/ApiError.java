package com.team.rag.bean;

import java.time.Instant;

public record ApiError(String message, Instant timestamp) {
}
