# Team RAG Knowledge

一个可本地运行、可扩展的 Spring Boot RAG 知识库项目，已整合 MySQL、Redis、Pinecone、阿里云 DashScope、中文前端页面，并补齐文件上传、分片续传、知识库分类、长期记忆、系统提示词、流式问答和全链路可观测能力。

## 当前能力

- 文件上传：支持 `txt`、`md`、`markdown`、`csv`、`json`、`xml`、`html`、`htm`、`log`、`yml`、`yaml`、`pdf`、`doc`、`docx`，PDF/Word 通过 Apache Tika 抽取文本。扫描版 PDF 需要先 OCR。
- 上传配额：支持普通单文件上限、分片文件上限、分片大小、系统每日总上传量、单用户每日上传量、单用户总存储上限、文件类型白名单，并可通过接口动态调整。
- 超大文件：支持初始化分片、断点续传、分片状态查询、失败重试、服务端分片 SHA-256 校验、最终合并和整文件 SHA-256 校验。合并后的大文件 checksum 采用流式计算，避免整文件读入内存。
- 安全校验：上传前做扩展名白名单校验、可执行文件头检测、高风险脚本扩展名拦截、同用户同来源 checksum 去重。
- RAG 知识库：使用 `sourceKind=RAG` 独立存储，支持业务线/分类路径、业务场景、项目、部门、文件类型等元数据。
- 长期记忆：使用 `sourceKind=MEMORY` 独立存储，支持按用户 ID、会话 ID、时间维度新增、查询、更新、删除。
- 物理隔离：Pinecone namespace 按来源拆分为基础命名空间加后缀，例如 `team-rag-knowledge-rag` 和 `team-rag-knowledge-memory`，避免 RAG 文档和长期记忆互相污染。
- 分类检索：问答时支持按来源、分类路径前缀、业务场景、文件类型、项目、部门、用户、会话筛选，只召回对应类别内容。
- 语义分块：当前是工程化轻量语义分块，按段落、中文/英文句末边界切分并带 overlap，不是 embedding 聚类式重型语义切分。
- 查询重写：问答前会结合短期记忆和当前问题调用 DashScope，把问题重写成更适合检索的完整问句。
- 混合检索：BM25 + Pinecone 向量检索融合排序。
- 流式回答：前端聊天使用 `/api/rag/qa/stream`，答案逐步输出。
- 引用展示：引用按文件聚合，同一个文件只显示一次，展示文件名、类型、命中数和最高分。
- 短期记忆：会话写入 Redis，TTL 默认为 3 天；Redis 不可用时使用本地内存兜底，但应用重启后会丢失。
- 记忆摘要：会话 token 估算达到 32000 后，调用大模型生成摘要并保留最近消息继续对话。
- 系统提示词：页面侧栏可提交系统提示词，后端支持 Redis 持久化和内存兜底。
- 可观测性：统一记录 `traceId`、`userId`、`sessionId`、`module`、`action`、`status`、`costTime`、`timestamp`，覆盖上传、解析、切分、embedding、向量入库、查询重写、检索、融合排序、Prompt/LLM 响应、结果返回和异常链路。

## 本地配置

项目默认读取：

```text
src/main/resources/application-local.yml
```

这个文件用于放本地密钥和数据库配置，不要提交到 Git。也可以使用环境变量：

```text
DASHSCOPE_API_KEY
PINECONE_API_KEY
MYSQL_URL
MYSQL_USERNAME
MYSQL_PASSWORD
REDIS_HOST
REDIS_PORT
RAG_ADMIN_TOKEN
```

上传、记忆和日志相关默认配置在 `src/main/resources/application.yml`：

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 12MB
      max-request-size: 12MB

rag:
  upload:
    direct-max-file-size: 10MB
    chunk-size: 8MB
    max-chunk-size: 10MB
    max-chunked-file-size: 2GB
    daily-total-quota: 5GB
    user-daily-quota: 1GB
    user-total-storage-quota: 10GB
    allowed-extensions:
      - txt
      - md
      - markdown
      - csv
      - json
      - xml
      - html
      - htm
      - log
      - yml
      - yaml
      - pdf
      - doc
      - docx
  answer:
    memory-summary-token-threshold: 32000
    memory-summary-keep-messages: 8
```

## 启动

```powershell
.\mvnw.cmd spring-boot:run
```

启动后访问：

```text
http://localhost:8080/
```

如果页面样式、按钮或上传逻辑没有变化，浏览器按 `Ctrl + F5` 强制刷新静态资源。

## 主要接口

- `POST /api/document/upload`：普通文件上传。超过普通上传上限时，前端会自动使用分片上传。
- `GET /api/document/list`：按来源和分类元数据查询知识库文件。
- `DELETE /api/document/{documentId}`：删除 RAG 知识库文档，同时清理 MySQL 分块、Pinecone 向量和本地上传文件。
- `POST /api/document/manual`：录入长期记忆。
- `GET /api/document/memory/list`：查询长期记忆列表。
- `PUT /api/document/memory/{documentId}`：更新长期记忆。
- `DELETE /api/document/memory/{documentId}`：删除长期记忆。
- `GET /api/upload/policy`：查看当前上传限制。
- `PUT /api/upload/policy`：动态调整上传限制。生产环境建议设置 `RAG_ADMIN_TOKEN`，并通过请求头 `X-Admin-Token` 调用。
- `POST /api/upload/chunk/init`：初始化分片上传。
- `POST /api/upload/chunk`：上传单个分片。
- `GET /api/upload/chunk/{uploadId}`：查询已上传分片，用于断点续传。
- `POST /api/upload/chunk/complete`：合并分片并写入 RAG 知识库。
- `GET /api/system-prompt`：读取当前系统提示词。
- `PUT /api/system-prompt`：保存系统提示词。
- `POST /api/rag/qa/stream`：流式问答。
- `GET /api/observability/logs`：查询最近链路日志，支持 `traceId` 或 `userId` 过滤。

## 动态调整上传限制示例

```http
PUT /api/upload/policy
Content-Type: application/json
X-Admin-Token: your-admin-token

{
  "directMaxFileBytes": 10485760,
  "chunkSizeBytes": 8388608,
  "maxChunkSizeBytes": 10485760,
  "maxChunkedFileBytes": 2147483648,
  "dailyTotalQuotaBytes": 5368709120,
  "userDailyQuotaBytes": 1073741824,
  "userTotalStorageQuotaBytes": 10737418240,
  "allowedExtensions": ["txt", "md", "pdf", "doc", "docx"]
}
```

动态策略会优先保存到 Redis，Redis 可用时应用重启后仍能恢复；Redis 不可用时以内存策略兜底。

## 可观测性

每个请求会自动生成或透传 `X-Trace-Id`，响应头也会返回该 traceId。链路日志会同时输出到控制台结构化日志，并持久化到 MySQL 表 `rag_operation_log`，后续可以接入 ELK 或 Grafana Loki。

典型链路动作包括：

- `security/malware_scan`
- `document/text_extract`
- `document/file_save`
- `document/text_split`
- `vector/embedding`
- `vector/pinecone_upsert`
- `rag/query_rewrite`
- `vector/query_embedding`
- `vector/pinecone_query`
- `rag/retrieval_rerank`
- `rag/retrieval_result`
- `rag/llm_response`
- `rag/qa_total`
- `api/exception`

查询示例：

```http
GET /api/observability/logs?traceId=xxx
GET /api/observability/logs?userId=u001
```

## 测试

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
.\mvnw.cmd test
```

当前集成测试覆盖基础上传、问答链路和文档删除链路。

## 常见问题

- `using password: NO`：MySQL 密码没有被读取到，检查 `application-local.yml` 是否存在且缩进正确。
- `Access denied`：密码已经读取，但 MySQL 用户名或密码不正确。
- PDF 上传失败：优先确认文件不是加密、损坏或纯扫描件。扫描件需要 OCR 后才能抽取文本。
- 大文件上传失败：先看 `/api/upload/policy` 中的 `maxChunkedFileBytes`、`dailyTotalQuotaBytes`、`userDailyQuotaBytes`、`userTotalStorageQuotaBytes` 是否足够。
- DashScope embedding 报 batch size：后端已经按最大 10 条自动拆批，如果仍报错，检查是否运行的是最新代码。
- Redis 未启动：问答仍可运行，但会话只会保存在本地内存，应用重启后丢失。
