const uploadForm = document.getElementById("uploadForm");
const manualForm = document.getElementById("manualForm");
const qaForm = document.getElementById("qaForm");
const filterForm = document.getElementById("filterForm");
const promptForm = document.getElementById("promptForm");
const policyForm = document.getElementById("policyForm");
const logForm = document.getElementById("logForm");
const evaluationForm = document.getElementById("evaluationForm");

const uploadButton = document.getElementById("uploadButton");
const manualButton = document.getElementById("manualButton");
const promptButton = document.getElementById("promptButton");
const policyButton = document.getElementById("policyButton");
const logButton = document.getElementById("logButton");
const evaluationButton = document.getElementById("evaluationButton");

const uploadMessage = document.getElementById("uploadMessage");
const manualMessage = document.getElementById("manualMessage");
const promptMessage = document.getElementById("promptMessage");
const policyMessage = document.getElementById("policyMessage");
const logMessage = document.getElementById("logMessage");
const evaluationMessage = document.getElementById("evaluationMessage");

const citationList = document.getElementById("citationList");
const agentStepList = document.getElementById("agentStepList");
const documentMetaList = document.getElementById("documentMetaList");
const chatTimeline = document.getElementById("chatTimeline");
const sessionList = document.getElementById("sessionList");
const logList = document.getElementById("logList");
const policySummary = document.getElementById("policySummary");
const evaluationResult = document.getElementById("evaluationResult");

const docCount = document.getElementById("docCount");
const sessionCount = document.getElementById("sessionCount");
const currentMemoryIdLabel = document.getElementById("currentMemoryId");
const currentTokenCountLabel = document.getElementById("currentTokenCount");
const summaryThresholdLabel = document.getElementById("summaryThreshold");

const newConversationButton = document.getElementById("newConversationButton");
const openMemoryButton = document.getElementById("openMemoryButton");
const closeMemoryButton = document.getElementById("closeMemoryButton");
const memoryModal = document.getElementById("memoryModal");
const openKnowledgeButton = document.getElementById("openKnowledgeButton");
const closeKnowledgeButton = document.getElementById("closeKnowledgeButton");
const knowledgeModal = document.getElementById("knowledgeModal");
const composerAttachButton = document.getElementById("composerAttachButton");
const agentModeToggle = document.getElementById("agentModeToggle");
const aiProviderSelect = document.getElementById("aiProviderSelect");
const userIdInput = document.getElementById("userIdInput");
const saveUserButton = document.getElementById("saveUserButton");
const currentUserLabel = document.getElementById("currentUserLabel");
const userIdentityMessage = document.getElementById("userIdentityMessage");

const state = {
    currentMemoryId: Number(localStorage.getItem("rag-current-memory-id")) || Date.now(),
    currentUserId: localStorage.getItem("rag-current-user-id") || "",
    aiProvider: localStorage.getItem("rag-ai-provider") || "dashscope",
    uploadPolicy: null
};

function persistMemoryId() {
    localStorage.setItem("rag-current-memory-id", String(state.currentMemoryId));
    currentMemoryIdLabel.textContent = String(state.currentMemoryId);
}

function defaultUserId() {
    const random = Math.random().toString(36).slice(2, 8);
    return `u_${random}`;
}

function persistUserId(showSaved = false) {
    if (!state.currentUserId) {
        state.currentUserId = defaultUserId();
    }
    localStorage.setItem("rag-current-user-id", state.currentUserId);
    if (userIdInput) userIdInput.value = state.currentUserId;
    if (currentUserLabel) currentUserLabel.textContent = state.currentUserId;
    applyUserIdToForms();
    if (showSaved && userIdentityMessage) {
        setStatus(userIdentityMessage, `当前用户 ID 已保存：${state.currentUserId}`);
    }
}

function applyUserIdToForms() {
    const userId = state.currentUserId;
    if (!userId) return;
    [uploadForm, manualForm, logForm].forEach((form) => {
        if (form?.elements?.userId && !form.elements.userId.value.trim()) {
            form.elements.userId.value = userId;
        }
    });
}

async function requestJson(url, options = {}) {
    const response = await fetch(url, options);
    const text = await response.text();
    let payload = {};
    if (text) {
        try {
            payload = JSON.parse(text);
        } catch {
            payload = { message: text };
        }
    }
    if (!response.ok) {
        throw new Error(payload.message || `请求失败：${response.status}`);
    }
    return payload;
}

function setStatus(element, message, isError = false) {
    element.textContent = message;
    element.className = `status ${isError ? "error" : "success"}`;
}

function escapeHtml(value) {
    return String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#39;");
}

function formatTime(value) {
    return value ? new Date(value).toLocaleString("zh-CN", { hour12: false }) : "未知时间";
}

function formatBytes(bytes) {
    const value = Number(bytes || 0);
    if (value >= 1024 ** 3) return `${(value / 1024 ** 3).toFixed(2)}GB`;
    if (value >= 1024 ** 2) return `${(value / 1024 ** 2).toFixed(2)}MB`;
    if (value >= 1024) return `${(value / 1024).toFixed(2)}KB`;
    return `${value}B`;
}

function formObject(form) {
    const data = {};
    for (const [key, value] of new FormData(form).entries()) {
        if (value instanceof File) continue;
        if (String(value).trim()) data[key] = String(value).trim();
    }
    return data;
}

function renderTokenStats(memoryDetail = {}) {
    currentTokenCountLabel.textContent = String(memoryDetail.tokenCount ?? 0);
    summaryThresholdLabel.textContent = String(memoryDetail.summaryThreshold ?? 32000);
}

function renderDocuments(documents) {
    docCount.textContent = `${documents.length} 个文件`;
    if (!documents.length) {
        documentMetaList.className = "document-list empty";
        documentMetaList.textContent = "暂无上传文件。";
        return;
    }
    documentMetaList.className = "document-list";
    documentMetaList.innerHTML = documents.map((document) => `
        <article class="file-card" data-document-id="${escapeHtml(document.id)}">
            <div class="file-card-head">
                <strong>${escapeHtml(document.name)}</strong>
                <span class="file-actions">
                    <button type="button" class="soft-link" data-view-chunks-id="${escapeHtml(document.id)}">切块</button>
                    <button type="button" class="soft-link" data-reindex-document-id="${escapeHtml(document.id)}">重索引</button>
                    <button type="button" class="danger-link" data-delete-document-id="${escapeHtml(document.id)}">删除</button>
                </span>
            </div>
            <div class="file-meta">
                <span>${escapeHtml(document.sourceKind || "RAG")}</span>
                <span>${escapeHtml(document.categoryPath || "未分类")}</span>
                <span>${escapeHtml(document.docType || "未分类")}</span>
                <span>${document.chunkCount || 0} 块</span>
                <span>${formatBytes(document.fileSizeBytes)}</span>
                <span>${escapeHtml(document.indexStatus || "UNKNOWN")}</span>
            </div>
            <small>${formatTime(document.uploadedAt)}</small>
        </article>
    `).join("");
    documentMetaList.querySelectorAll("[data-delete-document-id]").forEach((button) => {
        button.addEventListener("click", () => deleteDocument(button.dataset.deleteDocumentId));
    });
    documentMetaList.querySelectorAll("[data-view-chunks-id]").forEach((button) => {
        button.addEventListener("click", () => viewChunks(button.dataset.viewChunksId));
    });
    documentMetaList.querySelectorAll("[data-reindex-document-id]").forEach((button) => {
        button.addEventListener("click", () => reindexDocument(button.dataset.reindexDocumentId));
    });
}

function renderSessions(sessions) {
    sessionCount.textContent = `${sessions.length} 个会话`;
    if (!sessions.length) {
        sessionList.className = "session-list empty";
        sessionList.textContent = "暂无最近会话。";
        return;
    }
    sessionList.className = "session-list";
    sessionList.innerHTML = sessions.map((session) => `
        <button type="button" class="session-card ${session.memoryId === state.currentMemoryId ? "active" : ""}" data-memory-id="${session.memoryId}">
            <strong>${escapeHtml(session.title || "新会话")}</strong>
            <span>${session.roundCount || 0} 轮对话</span>
            <small>${escapeHtml(session.lastPreview || "暂无摘要")}</small>
        </button>
    `).join("");
    sessionList.querySelectorAll("[data-memory-id]").forEach((button) => {
        button.addEventListener("click", async () => {
            state.currentMemoryId = Number(button.dataset.memoryId);
            persistMemoryId();
            await refreshMemory();
            await refreshSessions();
        });
    });
}

function chatBubbleHtml(role, content) {
    return `
        <article class="chat-row ${role === "assistant" ? "assistant" : "user"}">
            <div class="bubble">
                <div class="bubble-role">${role === "assistant" ? "知识库助手" : "我"}</div>
                <div class="bubble-content">${escapeHtml(content)}</div>
            </div>
        </article>
    `;
}

function renderChatTimeline(memoryDetail) {
    renderTokenStats(memoryDetail);
    if (!memoryDetail.messages || !memoryDetail.messages.length) {
        chatTimeline.className = "chat-timeline muted";
        chatTimeline.textContent = memoryDetail.summary
            ? `历史摘要已保留：${memoryDetail.summary}`
            : "选择左侧文件上传，或点击“录入长期记忆”补充知识后，就可以开始提问。";
        return;
    }
    chatTimeline.className = "chat-timeline";
    chatTimeline.innerHTML = memoryDetail.messages.map((message) => chatBubbleHtml(message.role, message.content)).join("");
    scrollChatToBottom();
}

function appendChatBubble(role, content = "") {
    if (chatTimeline.classList.contains("muted")) {
        chatTimeline.className = "chat-timeline";
        chatTimeline.innerHTML = "";
    }
    const wrapper = document.createElement("article");
    wrapper.className = `chat-row ${role === "assistant" ? "assistant" : "user"}`;
    wrapper.innerHTML = `
        <div class="bubble">
            <div class="bubble-role">${role === "assistant" ? "知识库助手" : "我"}</div>
            <div class="bubble-content"></div>
        </div>
    `;
    wrapper.querySelector(".bubble-content").textContent = content;
    chatTimeline.appendChild(wrapper);
    scrollChatToBottom();
    return wrapper.querySelector(".bubble-content");
}

function scrollChatToBottom() {
    chatTimeline.scrollTop = chatTimeline.scrollHeight;
}

function renderCitations(citations) {
    if (!citations || !citations.length) {
        citationList.className = "citation-list muted";
        citationList.textContent = "本轮没有生成引用文件。";
        return;
    }
    citationList.className = "citation-list";
    citationList.innerHTML = citations.map((citation) => `
        <article class="citation-card">
            <strong>${escapeHtml(citation.documentName || "未命名文件")}</strong>
            <span>${escapeHtml(citation.docType || "未分类")}</span>
            <small>命中 ${citation.hitCount || 0} 次，相关度 ${Number(citation.score || 0).toFixed(3)}</small>
        </article>
    `).join("");
}

function resetAgentSteps() {
    agentStepList.className = "agent-step-list muted";
    agentStepList.textContent = agentModeToggle.checked
        ? "Agent 正在规划..."
        : "开启 Agent 模式后，这里展示 plan / tool / reflect 步骤。";
}

function appendAgentStep(step) {
    if (!step) return;
    if (agentStepList.classList.contains("muted")) {
        agentStepList.className = "agent-step-list";
        agentStepList.innerHTML = "";
    }
    const item = document.createElement("article");
    item.className = `agent-step-card ${step.status === "ERROR" ? "error" : ""}`;
    item.innerHTML = `
        <strong>${escapeHtml(step.name || "step")} · ${escapeHtml(step.status || "SUCCESS")}</strong>
        <span>${escapeHtml(step.detail || "")}</span>
        <small>${Number(step.costTimeMs || 0)}ms</small>
    `;
    agentStepList.appendChild(item);
}

function renderPolicy(policy) {
    state.uploadPolicy = policy;
    for (const [key, value] of Object.entries(policy)) {
        if (key === "allowedExtensions") {
            policyForm.elements.allowedExtensions.value = Array.isArray(value) ? value.join(",") : "";
        } else if (policyForm.elements[key]) {
            policyForm.elements[key].value = value ?? "";
        }
    }
    policySummary.className = "policy-summary";
    policySummary.innerHTML = `
        <div>普通上传：${formatBytes(policy.directMaxFileBytes)}</div>
        <div>分片文件：${formatBytes(policy.maxChunkedFileBytes)}，分片 ${formatBytes(policy.chunkSizeBytes)}</div>
        <div>系统每日：${formatBytes(policy.dailyTotalQuotaBytes)}，用户每日：${formatBytes(policy.userDailyQuotaBytes)}</div>
        <div>用户总存储：${formatBytes(policy.userTotalStorageQuotaBytes)}</div>
        <div>白名单：${escapeHtml((policy.allowedExtensions || []).join(", "))}</div>
    `;
}

function renderLogs(logs) {
    if (!logs || !logs.length) {
        logList.className = "log-list empty";
        logList.textContent = "没有查到链路日志。";
        return;
    }
    logList.className = "log-list";
    logList.innerHTML = logs.map((log) => `
        <article class="log-card ${log.status === "ERROR" ? "error" : ""}">
            <div class="log-head">
                <strong>${escapeHtml(log.module)}/${escapeHtml(log.action)}</strong>
                <span>${escapeHtml(log.status)} · ${log.costTime || 0}ms</span>
            </div>
            <small>${formatTime(log.timestamp)}</small>
            <small>traceId：${escapeHtml(log.traceId || "-")}</small>
            <small>userId：${escapeHtml(log.userId || "-")}，sessionId：${escapeHtml(log.sessionId || "-")}</small>
            <pre>${escapeHtml(log.detailJson || "{}")}</pre>
        </article>
    `).join("");
}

async function sha256Hex(blob) {
    const buffer = await blob.arrayBuffer();
    const hash = await crypto.subtle.digest("SHA-256", buffer);
    return [...new Uint8Array(hash)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

async function uploadChunkWithRetry(uploadId, chunkIndex, chunk) {
    const chunkSha256 = await sha256Hex(chunk);
    for (let attempt = 1; attempt <= 3; attempt++) {
        try {
            const body = new FormData();
            body.append("uploadId", uploadId);
            body.append("chunkIndex", String(chunkIndex));
            body.append("chunkSha256", chunkSha256);
            body.append("chunk", chunk, `${chunkIndex}.part`);
            return await requestJson("/api/upload/chunk", { method: "POST", body });
        } catch (error) {
            if (attempt === 3) throw error;
            await new Promise((resolve) => setTimeout(resolve, attempt * 600));
        }
    }
}

async function chunkedUpload(file, metadata) {
    const policy = state.uploadPolicy || await refreshUploadPolicy();
    const chunkSize = policy.chunkSizeBytes;
    const totalChunks = Math.ceil(file.size / chunkSize);
    const init = await requestJson("/api/upload/chunk/init", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
            fileName: file.name,
            fileSize: file.size,
            chunkSize,
            totalChunks,
            sourceKind: "RAG",
            ...metadata
        })
    });
    const uploaded = new Set(init.uploadedChunks || []);
    for (let index = 0; index < totalChunks; index++) {
        if (uploaded.has(index)) continue;
        const start = index * chunkSize;
        const end = Math.min(file.size, start + chunkSize);
        setStatus(uploadMessage, `正在分片上传：${index + 1}/${totalChunks}`);
        await uploadChunkWithRetry(init.uploadId, index, file.slice(start, end));
    }
    setStatus(uploadMessage, "分片上传完成，正在合并并索引...");
    return requestJson("/api/upload/chunk/complete", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ uploadId: init.uploadId })
    });
}

async function refreshUploadPolicy() {
    const policy = await requestJson("/api/upload/policy");
    renderPolicy(policy);
    return policy;
}

async function refreshDocuments() {
    try {
        renderDocuments(await requestJson("/api/document/list?sourceKind=RAG"));
    } catch (error) {
        documentMetaList.className = "document-list empty";
        documentMetaList.textContent = `上传文件列表加载失败：${error.message}`;
    }
}

async function deleteDocument(documentId) {
    if (!documentId) return;
    if (!window.confirm("确定删除这个知识库文档吗？删除后会同时清理向量索引，需要重新上传新版文件。")) return;
    setStatus(uploadMessage, "正在删除知识库文档...");
    try {
        const result = await requestJson(`/api/document/${encodeURIComponent(documentId)}`, { method: "DELETE" });
        setStatus(uploadMessage, result.message || "知识库文档已删除。");
        await refreshDocuments();
    } catch (error) {
        setStatus(uploadMessage, `删除失败：${error.message}`, true);
    }
}

async function viewChunks(documentId) {
    setStatus(uploadMessage, "正在加载文档切块...");
    try {
        const chunks = await requestJson(`/api/document/${encodeURIComponent(documentId)}/chunks`);
        const preview = chunks
            .slice(0, 5)
            .map((chunk) => `#${chunk.chunkIndex} ${chunk.tokenCount} tokens\n${chunk.content}`)
            .join("\n\n---\n\n");
        window.alert(preview || "这个文档暂无切块。");
        setStatus(uploadMessage, `已加载 ${chunks.length} 个切块。`);
    } catch (error) {
        setStatus(uploadMessage, `切块加载失败：${error.message}`, true);
    }
}

async function reindexDocument(documentId) {
    if (!window.confirm("确定重新索引这个文档吗？会删除旧向量并重新切分、embedding、入库。")) return;
    setStatus(uploadMessage, "正在重新索引文档...");
    try {
        const result = await requestJson(`/api/document/${encodeURIComponent(documentId)}/reindex`, { method: "POST" });
        setStatus(uploadMessage, `${result.message}，${result.chunkCount} 个切块。`);
        await refreshDocuments();
    } catch (error) {
        setStatus(uploadMessage, `重新索引失败：${error.message}`, true);
    }
}

async function refreshSessions() {
    try {
        renderSessions(await requestJson("/api/rag/memory/sessions"));
    } catch (error) {
        sessionList.className = "session-list empty";
        sessionList.textContent = `最近会话加载失败：${error.message}`;
    }
}

async function refreshMemory() {
    try {
        renderChatTimeline(await requestJson(`/api/rag/memory/${state.currentMemoryId}`));
    } catch (error) {
        renderTokenStats();
        chatTimeline.className = "chat-timeline muted";
        chatTimeline.textContent = `当前会话加载失败：${error.message}`;
    }
}

async function refreshSystemPrompt() {
    try {
        const result = await requestJson("/api/system-prompt");
        promptForm.elements.prompt.value = result.prompt || "";
    } catch (error) {
        setStatus(promptMessage, `系统提示词加载失败：${error.message}`, true);
    }
}

function openMemoryModal() {
    memoryModal.classList.remove("hidden");
}

function closeMemoryModal() {
    memoryModal.classList.add("hidden");
}

function openKnowledgeModal() {
    knowledgeModal.classList.remove("hidden");
    applyUserIdToForms();
}

function closeKnowledgeModal() {
    knowledgeModal.classList.add("hidden");
}

function parseSseEvents(buffer) {
    const events = [];
    const parts = buffer.replaceAll("\r\n", "\n").split("\n\n");
    const rest = parts.pop() ?? "";
    for (const part of parts) {
        let eventName = "message";
        const dataLines = [];
        for (const line of part.split("\n")) {
            if (line.startsWith("event:")) eventName = line.slice(6).trim();
            if (line.startsWith("data:")) dataLines.push(line.slice(5).trimStart());
        }
        events.push({ event: eventName, data: dataLines.join("\n") });
    }
    return { events, rest };
}

async function streamQuestion(query, assistantContent) {
    const filters = formObject(filterForm);
    const endpoint = agentModeToggle.checked ? "/api/agent/qa/stream" : "/api/rag/qa/stream";
    const response = await fetch(endpoint, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ query, memoryId: state.currentMemoryId, aiProvider: state.aiProvider, ...filters })
    });
    if (!response.ok || !response.body) {
        const text = await response.text();
        throw new Error(text || `流式请求失败：${response.status}`);
    }
    const traceId = response.headers.get("X-Trace-Id");
    if (traceId && logForm.elements.traceId) {
        logForm.elements.traceId.value = traceId;
    }
    const reader = response.body.getReader();
    const decoder = new TextDecoder("utf-8");
    let buffer = "";
    while (true) {
        const { value, done } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        const parsed = parseSseEvents(buffer);
        buffer = parsed.rest;
        for (const item of parsed.events) {
            if (item.event === "delta" || item.event === "message") {
                assistantContent.textContent += item.data;
                scrollChatToBottom();
            } else if (item.event === "references") {
                const payload = JSON.parse(item.data || "{}");
                renderCitations(payload.citations || []);
                if (Array.isArray(payload.steps)) {
                    payload.steps.forEach(appendAgentStep);
                }
            } else if (item.event === "step") {
                appendAgentStep(JSON.parse(item.data || "{}"));
            } else if (item.event === "error") {
                const payload = JSON.parse(item.data || "{}");
                throw new Error(payload.message || "流式回答失败");
            }
        }
    }
}

uploadForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    applyUserIdToForms();
    const file = uploadForm.elements.file.files[0];
    if (!file) {
        setStatus(uploadMessage, "请先选择文件。", true);
        return;
    }
    uploadButton.disabled = true;
    const metadata = formObject(uploadForm);
    try {
        const policy = state.uploadPolicy || await refreshUploadPolicy();
        let result;
        if (file.size <= policy.directMaxFileBytes) {
            setStatus(uploadMessage, "正在普通上传并索引...");
            const body = new FormData(uploadForm);
            body.set("sourceKind", "RAG");
            result = await requestJson("/api/document/upload", { method: "POST", body });
        } else {
            result = await chunkedUpload(file, metadata);
        }
        setStatus(uploadMessage, `${result.message}，${result.chunkCount} 个切块。`);
        uploadForm.reset();
        applyUserIdToForms();
        await refreshDocuments();
    } catch (error) {
        setStatus(uploadMessage, `上传失败：${error.message}`, true);
    } finally {
        uploadButton.disabled = false;
    }
});

manualForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    applyUserIdToForms();
    const content = manualForm.elements.content.value;
    if (!content || !content.trim()) {
        setStatus(manualMessage, "请填写长期记忆内容。", true);
        return;
    }
    manualButton.disabled = true;
    setStatus(manualMessage, "正在保存到长期记忆...");
    try {
        const payload = {
            ...formObject(manualForm),
            sourceKind: "MEMORY",
            conversationId: manualForm.elements.conversationId.value || String(state.currentMemoryId),
            content
        };
        const result = await requestJson("/api/document/manual", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload)
        });
        setStatus(manualMessage, `${result.message}，${result.chunkCount} 个切块。`);
        manualForm.reset();
        applyUserIdToForms();
        setTimeout(closeMemoryModal, 600);
    } catch (error) {
        setStatus(manualMessage, `保存失败：${error.message}`, true);
    } finally {
        manualButton.disabled = false;
    }
});

promptForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    promptButton.disabled = true;
    setStatus(promptMessage, "正在保存系统提示词...");
    try {
        const headers = { "Content-Type": "application/json" };
        const token = promptForm.elements.adminToken.value.trim();
        if (token) headers["X-Admin-Token"] = token;
        await requestJson("/api/system-prompt", {
            method: "PUT",
            headers,
            body: JSON.stringify({ prompt: promptForm.elements.prompt.value })
        });
        setStatus(promptMessage, "系统提示词已保存。");
    } catch (error) {
        setStatus(promptMessage, `保存失败：${error.message}`, true);
    } finally {
        promptButton.disabled = false;
    }
});

policyForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    policyButton.disabled = true;
    setStatus(policyMessage, "正在保存上传策略...");
    try {
        const headers = { "Content-Type": "application/json" };
        const token = policyForm.elements.adminToken.value.trim();
        if (token) headers["X-Admin-Token"] = token;
        const payload = {
            directMaxFileBytes: Number(policyForm.elements.directMaxFileBytes.value),
            chunkSizeBytes: Number(policyForm.elements.chunkSizeBytes.value),
            maxChunkSizeBytes: Number(policyForm.elements.maxChunkSizeBytes.value),
            maxChunkedFileBytes: Number(policyForm.elements.maxChunkedFileBytes.value),
            dailyTotalQuotaBytes: Number(policyForm.elements.dailyTotalQuotaBytes.value),
            userDailyQuotaBytes: Number(policyForm.elements.userDailyQuotaBytes.value),
            userTotalStorageQuotaBytes: Number(policyForm.elements.userTotalStorageQuotaBytes.value),
            allowedExtensions: policyForm.elements.allowedExtensions.value
                .split(",")
                .map((item) => item.trim().replace(/^\./, ""))
                .filter(Boolean)
        };
        renderPolicy(await requestJson("/api/upload/policy", {
            method: "PUT",
            headers,
            body: JSON.stringify(payload)
        }));
        setStatus(policyMessage, "上传策略已保存。");
    } catch (error) {
        setStatus(policyMessage, `保存失败：${error.message}`, true);
    } finally {
        policyButton.disabled = false;
    }
});

logForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    logButton.disabled = true;
    setStatus(logMessage, "正在查询链路日志...");
    try {
        const params = new URLSearchParams(formObject(logForm));
        renderLogs(await requestJson(`/api/observability/logs?${params.toString()}`));
        setStatus(logMessage, "链路日志已加载。");
    } catch (error) {
        setStatus(logMessage, `查询失败：${error.message}`, true);
    } finally {
        logButton.disabled = false;
    }
});

evaluationForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    evaluationButton.disabled = true;
    setStatus(evaluationMessage, "正在运行评测...");
    try {
        const cases = JSON.parse(evaluationForm.elements.casesJson.value || "[]");
        const topKValue = evaluationForm.elements.topK.value;
        const result = await requestJson("/api/evaluation/run", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                cases,
                topK: topKValue ? Number(topKValue) : undefined
            })
        });
        evaluationResult.className = "policy-summary";
        evaluationResult.innerHTML = `
            <div>样本数：${result.totalCases}</div>
            <div>平均 Recall@K：${Number(result.averageRecallAtK || 0).toFixed(3)}</div>
            <div>平均 Precision@K：${Number(result.averagePrecisionAtK || 0).toFixed(3)}</div>
            <div>命中文档率：${Number(result.hitRate || 0).toFixed(3)}</div>
        `;
        setStatus(evaluationMessage, "评测完成。");
    } catch (error) {
        setStatus(evaluationMessage, `评测失败：${error.message}`, true);
    } finally {
        evaluationButton.disabled = false;
    }
});

qaForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    const sendButton = qaForm.querySelector("button[type='submit']");
    const query = new FormData(qaForm).get("query");
    if (!query || !String(query).trim()) return;
    citationList.className = "citation-list muted";
    citationList.textContent = "正在整理引用文件...";
    resetAgentSteps();
    appendChatBubble("user", query);
    const assistantContent = appendChatBubble("assistant", "");
    qaForm.reset();
    sendButton.disabled = true;
    try {
        await streamQuestion(String(query), assistantContent);
        await refreshMemory();
        await refreshSessions();
    } catch (error) {
        assistantContent.textContent = `问答失败：${error.message}`;
        citationList.className = "citation-list muted";
        citationList.textContent = "本次未生成引用文件。";
    } finally {
        sendButton.disabled = false;
    }
});

newConversationButton.addEventListener("click", async () => {
    state.currentMemoryId = Date.now();
    persistMemoryId();
    applyUserIdToForms();
    citationList.className = "citation-list muted";
    citationList.textContent = "新会话暂无引用文件。";
    await refreshMemory();
    await refreshSessions();
});

openMemoryButton.addEventListener("click", openMemoryModal);
closeMemoryButton.addEventListener("click", closeMemoryModal);
memoryModal.addEventListener("click", (event) => {
    if (event.target === memoryModal) closeMemoryModal();
});
openKnowledgeButton.addEventListener("click", openKnowledgeModal);
closeKnowledgeButton.addEventListener("click", closeKnowledgeModal);
knowledgeModal.addEventListener("click", (event) => {
    if (event.target === knowledgeModal) closeKnowledgeModal();
});
composerAttachButton.addEventListener("click", () => {
    openKnowledgeModal();
    setTimeout(() => uploadForm.elements.file?.click(), 80);
});

persistMemoryId();
persistUserId();
if (aiProviderSelect) {
    aiProviderSelect.value = state.aiProvider;
    aiProviderSelect.addEventListener("change", () => {
        state.aiProvider = aiProviderSelect.value || "dashscope";
        localStorage.setItem("rag-ai-provider", state.aiProvider);
    });
}
refreshUploadPolicy();
refreshSystemPrompt();
refreshDocuments();
refreshSessions();
refreshMemory();

if (saveUserButton) {
    saveUserButton.addEventListener("click", () => {
        const value = userIdInput?.value?.trim();
        if (!value) {
            setStatus(userIdentityMessage, "用户 ID 不能为空，可以填 u001 / admin / student_001。", true);
            return;
        }
        state.currentUserId = value;
        persistUserId(true);
    });
}

if (userIdInput) {
    userIdInput.addEventListener("keydown", (event) => {
        if (event.key !== "Enter") return;
        event.preventDefault();
        saveUserButton?.click();
    });
}

document.querySelectorAll("[data-prompt]").forEach((button) => {
    button.addEventListener("click", () => {
        qaForm.elements.query.value = button.dataset.prompt || "";
        qaForm.elements.query.focus();
    });
});

const traceShortcut = document.querySelector("input[name='traceShortcut']");
if (traceShortcut) {
    traceShortcut.addEventListener("keydown", (event) => {
        if (event.key !== "Enter") return;
        event.preventDefault();
        if (logForm.elements.traceId) {
            logForm.elements.traceId.value = traceShortcut.value.trim();
        }
        logForm.requestSubmit();
    });
}
