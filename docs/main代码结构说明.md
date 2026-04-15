# main 目录代码结构说明

本文档用于解释 `src/main/java/com/team/rag` 下面各个包、各类文件的职责。可以把它理解为这个 RAG 项目的“后端代码导览图”。

项目整体采用 Spring Boot 常见分层结构：

```text
controller -> service -> repository/entity
              |
              -> client
              -> util
              -> bean
```

简单来说：

- `controller`：对外提供 HTTP 接口。
- `service`：写核心业务逻辑。
- `repository`：访问数据库。
- `entity`：数据库表映射。
- `client`：调用外部 AI / 向量库服务。
- `config`：配置项和基础设施 Bean。
- `bean`：请求体、响应体、业务传输对象。
- `util`：文本切分、BM25、分词等工具。

## 1. 根启动类

### `TeamArg.java`

Spring Boot 启动入口。

主要作用：

- 启动整个后端应用。
- 扫描 `com.team.rag` 包下的 Controller、Service、Repository 等组件。
- 通过 `@ConfigurationPropertiesScan` 加载配置类，例如 `DashScopeProperties`、`PineconeProperties`、`RagProperties` 等。

运行项目时实际启动的就是这个类。

## 2. bean 包

`bean` 包主要放 DTO、VO、请求对象、响应对象和业务中间对象。

这些类一般不直接操作数据库，也不直接调用外部服务，而是在 Controller、Service、Client 之间传递数据。

### 2.1 Agent 相关

### `AgentAnswerResponse.java`

Agent 问答接口的响应对象。

通常包含：

- 最终回答。
- 引用文档。
- 召回片段。
- memoryId。
- 耗时。
- Agent 执行步骤。

它用于 `/api/agent/qa` 和 `/api/agent/qa/stream`。

### `AgentStep.java`

表示 Agent 执行过程中的一步。

字段含义：

- `name`：步骤名称，例如 `plan`、`route`、`tool.retrieve_and_answer`、`reflect`。
- `status`：步骤状态，例如 `SUCCESS`、`ERROR`、`RETRY`。
- `detail`：步骤说明。
- `costTimeMs`：该步骤耗时。

前端 Agent 步骤展示就是基于这个对象。

## 2.2 通用响应与消息

### `ApiError.java`

统一异常响应对象。

当后端接口出错时，`GlobalExceptionHandler` 会把异常转成这种结构返回给前端。

常见字段：

- 错误信息。
- 错误码或状态。
- traceId。

### `ChatMessage.java`

大模型对话消息对象。

字段通常是：

- `role`：`system`、`user`、`assistant`。
- `content`：消息内容。

DashScope、DeepSeek 调用聊天模型时都会把消息列表转换成这种结构。

## 2.3 分片上传相关

### `ChunkUploadInitRequest.java`

初始化分片上传的请求对象。

前端上传大文件前会先调用：

```http
POST /api/upload/chunk/init
```

该对象包含：

- 文件名。
- 文件大小。
- 分片大小。
- 分片总数。
- sourceKind。
- 分类元数据。
- userId。
- conversationId。

### `ChunkUploadCompleteRequest.java`

完成分片上传的请求对象。

当所有分片上传完毕后，前端调用：

```http
POST /api/upload/chunk/complete
```

该对象主要携带 `uploadId`，后端根据它找到所有分片并合并文件。

### `ChunkUploadStatusResponse.java`

查询分片上传状态的响应对象。

用于断点续传。

它告诉前端：

- 哪些分片已经上传。
- 总分片数。
- 当前上传状态。
- uploadId。

### `ChunkView.java`

文档切块预览对象。

用于接口：

```http
GET /api/document/{documentId}/chunks
```

字段通常包括：

- chunkId。
- chunkIndex。
- tokenCount。
- content。

前端点击“切块”时展示的就是这个对象。

## 2.4 文档与知识库相关

### `DocumentForm.java`

文档上传和长期记忆录入的表单对象。

用于普通文件上传：

```http
POST /api/document/upload
```

也用于长期记忆录入：

```http
POST /api/document/manual
```

它包含：

- 文件对象。
- 标题。
- 内容。
- sourceKind。
- userId。
- conversationId。
- categoryPath。
- docType。
- businessScene。
- projectName。
- department。
- description。

### `DocumentMetadata.java`

文档元数据对象。

它是 `DocumentService` 和 `KnowledgeBaseService` 之间传递元数据用的对象。

主要描述：

- 文档属于 RAG 还是 MEMORY。
- 文档分类。
- 用户信息。
- 会话信息。
- 文件大小。
- checksum。

### `DocumentStatus.java`

文档索引状态枚举。

常见状态：

- `PROCESSING`：处理中。
- `READY`：已完成索引，可以检索。
- `FAILED`：处理失败。

### `DocumentSummary.java`

文档列表展示对象。

用于：

```http
GET /api/document/list
GET /api/document/memory/list
```

前端知识库文件列表和长期记忆列表展示的就是它。

一般包含：

- 文档 ID。
- 文件名。
- sourceKind。
- 分类。
- 文档类型。
- chunk 数量。
- 文件大小。
- 上传时间。
- 索引状态。

### `UploadResponse.java`

上传或录入知识后的统一响应。

通常包含：

- 是否成功。
- 提示消息。
- documentId。
- chunkCount。
- sourceKind。

文件上传、长期记忆录入、重索引都会返回它。

## 2.5 RAG 问答相关

### `RagQo.java`

RAG 问答请求对象。

用于：

```http
POST /api/rag/qa
POST /api/rag/qa/stream
POST /api/agent/qa
POST /api/agent/qa/stream
```

主要字段：

- `query`：用户问题。
- `memoryId`：当前会话 ID。
- `topK`：最终召回片段数量。
- `sourceKind`：RAG、MEMORY、ALL。
- `categoryPath`：分类过滤。
- `businessScene`：业务场景过滤。
- `fileType`：文件类型过滤。
- `projectName`：项目过滤。
- `department`：部门过滤。
- `userId`：用户 ID。
- `conversationId`：会话 ID。
- `aiProvider`：选择使用的聊天模型，例如 `dashscope` 或 `deepseek`。

### `RagAnswerResponse.java`

RAG 问答响应对象。

通常包含：

- answer：最终回答。
- citations：引用文档。
- retrievedChunks：召回片段。
- memoryId：会话 ID。
- costTimeMs：耗时。

### `RetrievedChunk.java`

一次检索召回的文档片段。

字段通常包括：

- chunkId。
- documentId。
- documentName。
- docType。
- score。
- content。

BM25、向量检索、RRF 融合、重排序后传递的都是这个对象。

### `Citation.java`

引用文档对象。

当前项目按“文件级引用”聚合，而不是每个 chunk 都显示一次。

字段通常包括：

- documentId。
- documentName。
- score。
- docType。
- hitCount。

### `SearchFilter.java`

检索过滤条件对象。

用于统一处理：

- RAG / MEMORY / ALL 来源过滤。
- 分类过滤。
- 业务场景过滤。
- 文件类型过滤。
- 项目过滤。
- 部门过滤。
- 用户过滤。
- 会话过滤。

当前规则：

- 查 RAG 知识库时，不按 `userId` 和 `conversationId` 过滤。
- 查长期记忆 MEMORY 时，才按 `userId` 和 `conversationId` 过滤。
- 查 ALL 时，RAG 部分不看用户，MEMORY 部分看用户。

这可以避免公共知识库因为用户 ID 不一致而查不到。

## 2.6 Pinecone 向量相关

### `PineconeMatch.java`

Pinecone 查询返回的单条匹配结果。

通常包含：

- 向量 ID。
- 相似度分数。
- metadata。

### `UpsertVector.java`

写入 Pinecone 的向量对象。

包含：

- 向量 ID。
- embedding 数组。
- metadata。

文档入库时，`DocumentService` 会把 chunk 转成 `UpsertVector` 后写入 Pinecone。

## 2.7 长短期记忆相关

### `MemorySessionView.java`

最近会话列表展示对象。

用于前端侧栏“最近会话”。

字段通常包括：

- memoryId。
- title。
- roundCount。
- lastPreview。

### `MemoryDetailResponse.java`

某个会话详情响应。

用于：

```http
GET /api/rag/memory/{memoryId}
```

包含：

- memoryId。
- 对话轮次。
- 消息列表。
- 摘要。
- tokenCount。
- summaryThreshold。
- 是否已经摘要。

### `MemoryRoundView.java`

对话轮次视图。

用于把 user / assistant 消息整理成一轮一轮的展示结构。

## 2.8 系统提示词相关

### `SystemPromptRequest.java`

保存系统提示词的请求对象。

用于：

```http
PUT /api/system-prompt
```

### `SystemPromptResponse.java`

读取系统提示词的响应对象。

用于：

```http
GET /api/system-prompt
```

## 2.9 上传策略相关

### `UploadPolicy.java`

上传策略对象。

用于配置：

- 普通上传单文件上限。
- 分片大小。
- 分片文件总大小上限。
- 系统每日总上传量。
- 单用户每日上传量。
- 单用户总存储量。
- 文件类型白名单。

接口：

```http
GET /api/upload/policy
PUT /api/upload/policy
```

## 2.10 评测相关

### `EvaluationCaseRequest.java`

单条评测用例。

包含：

- query。
- expectedAnswer。
- expectedDocumentIds。
- 检索过滤条件。

### `EvaluationRunRequest.java`

一次评测运行请求。

包含：

- 多条评测 case。
- topK。

### `EvaluationCaseResult.java`

单条评测结果。

包含：

- 是否命中期望文档。
- Recall@K。
- Precision@K。
- 实际召回文档。
- 回答内容。

### `EvaluationRunResponse.java`

整次评测汇总结果。

包含：

- 样本数。
- 平均 Recall@K。
- 平均 Precision@K。
- hitRate。
- 每条 case 的结果列表。

## 2.11 可观测日志相关

### `OperationLogView.java`

链路日志展示对象。

用于：

```http
GET /api/observability/logs
```

前端链路日志面板展示的就是它。

## 3. client 包

`client` 包负责调用外部服务。

### `DashScopeClient.java`

阿里云百炼 DashScope 客户端。

负责：

- 调用 `qwen-plus` 等聊天模型。
- 调用 `text-embedding-v3` 生成 embedding。
- 支持流式聊天输出。

当前项目中 embedding 仍然主要走 DashScope，因为 Pinecone 向量维度依赖它。

### `DeepSeekClient.java`

DeepSeek 聊天模型客户端。

负责：

- 调用 DeepSeek Chat Completions 接口。
- 支持普通聊天。
- 支持流式聊天。

它只负责聊天模型，不负责 embedding。

### `PineconeClient.java`

Pinecone 向量数据库客户端。

负责：

- 自动创建 index。
- upsert 向量。
- query 向量。
- delete 向量。
- 按 sourceKind 使用不同 namespace。

RAG 知识库和长期记忆的向量物理隔离主要在这里实现。

## 4. config 包

`config` 包负责配置属性绑定和基础设施 Bean。

### `DashScopeProperties.java`

绑定 `dashscope.*` 配置。

包含：

- baseUrl。
- apiKey。
- chatModel。
- embeddingModel。
- embeddingDimensions。
- temperature。

### `DeepSeekProperties.java`

绑定 `deepseek.*` 配置。

包含：

- baseUrl。
- apiKey。
- chatModel。
- temperature。

### `ChatModelProperties.java`

绑定 `chat.*` 配置。

当前主要用于设置默认聊天模型提供方：

```yaml
chat:
  default-provider: dashscope
```

### `PineconeProperties.java`

绑定 `pinecone.*` 配置。

包含：

- apiKey。
- indexName。
- indexHost。
- namespace。
- dimension。
- cloud。
- region。
- metric。

### `RagProperties.java`

绑定 `rag.*` 配置。

包含：

- 上传目录。
- chunk 最大长度。
- overlap。
- 检索 topK。
- 回答历史轮数。
- 会话摘要阈值。
- 上传配额。

### `RAGConfig.java`

通用 Bean 配置类。

通常用于配置：

- RestClient。
- 线程池。
- 其他项目级 Bean。

### `TraceFilter.java`

HTTP 请求链路追踪过滤器。

作用：

- 为每个请求生成或透传 traceId。
- 把 traceId 放入 MDC。
- 把 traceId 写入响应头。

这样日志、接口响应和前端 trace 查询可以串起来。

### `DatabaseSchemaInitializer.java`

数据库结构初始化/兼容处理类。

用于补齐一些表字段或做数据库兼容初始化。

## 5. controller 包

`controller` 包负责 HTTP API。

### `DocumentController.java`

文档和长期记忆管理接口。

主要接口：

```http
POST   /api/document/upload
POST   /api/document/manual
GET    /api/document/list
GET    /api/document/memory/list
DELETE /api/document/{documentId}
GET    /api/document/{documentId}/chunks
POST   /api/document/{documentId}/reindex
PUT    /api/document/memory/{documentId}
DELETE /api/document/memory/{documentId}
```

### `UploadController.java`

分片上传和上传策略接口。

主要接口：

```http
GET  /api/upload/policy
PUT  /api/upload/policy
POST /api/upload/chunk/init
POST /api/upload/chunk
GET  /api/upload/chunk/{uploadId}
POST /api/upload/chunk/complete
```

### `RagQAController.java`

普通 RAG 问答接口。

主要接口：

```http
POST /api/rag/qa
POST /api/rag/qa/stream
GET  /api/rag/memory/sessions
GET  /api/rag/memory/{memoryId}
```

### `AgentController.java`

轻量 Agent 问答接口。

主要接口：

```http
POST /api/agent/qa
POST /api/agent/qa/stream
GET  /api/agent/trace/{traceId}
```

### `EvaluationController.java`

RAG 效果评测接口。

主要接口：

```http
POST /api/evaluation/run
```

### `SystemPromptController.java`

系统提示词接口。

主要接口：

```http
GET /api/system-prompt
PUT /api/system-prompt
```

### `ObservabilityController.java`

链路日志查询接口。

主要接口：

```http
GET /api/observability/logs
```

### `GlobalExceptionHandler.java`

统一异常处理器。

负责把后端异常转换成统一 JSON 返回给前端。

## 6. entity 包

`entity` 包是数据库表映射。

### `KnowledgeDocumentEntity.java`

知识文档表实体。

对应 RAG 文档和长期记忆文档。

保存：

- 文档名称。
- sourceKind。
- 分类元数据。
- 用户 ID。
- 会话 ID。
- 文件大小。
- checksum。
- 原始文本。
- 索引状态。
- 上传时间。

### `KnowledgeChunkEntity.java`

文档切块表实体。

一个文档会拆成多个 chunk。

保存：

- chunkUuid。
- chunkIndex。
- content。
- tokenCount。
- 所属文档。

### `OperationLogEntity.java`

链路日志表实体。

保存：

- traceId。
- userId。
- sessionId。
- module。
- action。
- status。
- costTime。
- detailJson。
- errorStack。
- timestamp。

## 7. repository 包

`repository` 包是 Spring Data JPA 数据访问层。

### `KnowledgeDocumentRepository.java`

操作 `KnowledgeDocumentEntity`。

用于：

- 查询文档。
- 查重。
- 删除文档。
- 统计用户存储容量。

### `KnowledgeChunkRepository.java`

操作 `KnowledgeChunkEntity`。

用于：

- 根据 documentUuid 查询 chunk。
- 根据 chunkUuid 批量查询 chunk。
- 删除文档对应的 chunk。

### `OperationLogRepository.java`

操作 `OperationLogEntity`。

用于：

- 按 traceId 查询日志。
- 按 userId 查询日志。
- 查询最近链路日志。

## 8. service 包

`service` 包是项目核心业务逻辑。

### `DocumentService.java`

文档入库核心服务。

负责：

- 文件安全检查。
- 文件保存。
- Apache Tika 文本抽取。
- 语义切分。
- embedding。
- 写 MySQL。
- 写 Pinecone。
- 删除 RAG 文档。
- 更新/删除长期记忆。
- 重索引文档。

### `ChunkedUploadService.java`

大文件分片上传服务。

负责：

- 初始化分片上传。
- 接收单个分片。
- 校验分片 SHA-256。
- 查询已上传分片。
- 合并分片。
- 合并后调用 `DocumentService` 入库。

### `KnowledgeBaseService.java`

知识库数据管理服务。

负责：

- 保存文档和 chunk 元数据。
- 查询文档列表。
- 查询 chunk。
- 查重。
- 统计文档数量和用户存储量。

它是 MySQL 层的核心业务封装。

### `RagQAService.java`

RAG 问答核心服务。

负责：

- 读取短期记忆。
- 查询重写。
- BM25 检索。
- Pinecone 向量检索。
- RRF 融合。
- 轻量重排序。
- prompt 拼接。
- 调用聊天模型生成答案。
- 流式输出。
- 生成引用。
- 写回短期记忆。
- 触发记忆摘要。

### `LightAgentService.java`

轻量 Agent 服务。

负责：

- 生成 plan 步骤。
- 判断是否需要查全部来源。
- 调用 RAG 工具。
- 根据召回结果做 reflect。
- 非流式模式下支持空召回时二次检索。
- 返回 Agent 步骤。

当前它是轻量 Agent，不是完整多 Agent 编排框架。

### `ChatModelService.java`

聊天模型路由服务。

负责在多个 AI 模型之间切换：

- `dashscope`：Qwen Plus。
- `deepseek`：DeepSeek Chat。

注意：它只控制聊天模型，不控制 embedding。

### `ConversationMemoryService.java`

短期记忆接口。

定义会话记忆能力：

- append。
- getRecentMessages。
- getAllMessages。
- listMemoryIds。
- getSummary。
- replaceWithSummary。

### `RedisConversationMemoryService.java`

短期记忆的 Redis 实现。

负责：

- 把对话消息写入 Redis。
- 设置 3 天 TTL。
- 维护最近会话索引。
- Redis 不可用时使用本地内存兜底。
- 存储会话摘要。

### `SystemPromptService.java`

系统提示词服务。

负责：

- 读取系统提示词。
- 保存系统提示词。
- 优先使用 Redis 持久化。
- Redis 不可用时使用内存兜底。

### `UploadPolicyService.java`

上传策略和配额服务。

负责：

- 校验单文件大小。
- 校验分片大小。
- 校验系统每日总上传量。
- 校验单用户每日上传量。
- 校验单用户总存储量。
- 动态保存上传策略。
- 文件扩展名白名单。

### `EvaluationService.java`

RAG 评测服务。

负责：

- 执行多条评测用例。
- 调用 RAG 问答。
- 统计 Recall@K。
- 统计 Precision@K。
- 判断是否命中期望文档。

### `ObservabilityService.java`

链路日志服务。

负责：

- 记录结构化日志。
- 记录阶段耗时。
- 保存异常栈。
- 写入 MySQL。
- 输出到控制台日志。
- 支持 traceId 查询。

### `AdminGuardService.java`

管理员 Token 校验服务。

用于保护一些管理型接口，例如上传策略、系统提示词。

## 9. util 包

`util` 包是独立工具类。

### `SemanticSplitter.java`

文本切分工具。

当前是工程化轻量语义切分：

- 按段落切。
- 参考句末符号。
- 支持 overlap。
- 控制 chunk 最大长度。

它不是 embedding 聚类式重型语义切分。

### `BM25Retriever.java`

BM25 关键词检索器。

负责：

- 从 MySQL chunk 中读取候选文本。
- 对 query 分词。
- 计算 BM25 分数。
- 返回关键词召回结果。

### `TextTokenizer.java`

文本分词工具。

用于：

- BM25。
- token 粗略估算。
- 轻量重排序的词面重合度计算。

## 10. 一次完整链路如何串起来

### 10.1 上传文档链路

```text
DocumentController
  -> DocumentService
  -> UploadPolicyService
  -> SemanticSplitter
  -> DashScopeClient.embedAll
  -> KnowledgeBaseService
  -> PineconeClient.upsert
  -> ObservabilityService
```

### 10.2 普通问答链路

```text
RagQAController
  -> RagQAService
  -> RedisConversationMemoryService
  -> ChatModelService
  -> BM25Retriever
  -> DashScopeClient.embedAll
  -> PineconeClient.query
  -> RRF + rerank
  -> ChatModelService
  -> RedisConversationMemoryService
  -> ObservabilityService
```

### 10.3 Agent 问答链路

```text
AgentController
  -> LightAgentService
  -> RagQAService
  -> AgentStep
  -> AgentAnswerResponse
```

### 10.4 分片上传链路

```text
UploadController
  -> ChunkedUploadService
  -> 校验分片
  -> 合并文件
  -> DocumentService
```

## 11. 代码阅读建议

如果你是第一次读这个项目，推荐顺序：

1. 先看 `TeamArg.java`，知道启动入口。
2. 再看 `DocumentController.java` 和 `RagQAController.java`，知道接口入口。
3. 再看 `DocumentService.java`，理解文档如何入库。
4. 再看 `RagQAService.java`，理解问题如何回答。
5. 再看 `KnowledgeBaseService.java`，理解 MySQL 文档和 chunk 如何保存。
6. 再看 `PineconeClient.java` 和 `DashScopeClient.java`，理解外部服务调用。
7. 最后看 `LightAgentService.java`、`EvaluationService.java`、`ObservabilityService.java`。

## 12. 一句话总结

`main` 目录里的代码可以分成三块：`bean/entity/repository` 负责数据结构和存储，`controller/service` 负责业务接口和核心流程，`client/config/util` 负责外部模型、配置和检索工具。整个项目围绕“文档入库 -> 检索召回 -> 模型回答 -> 记忆沉淀 -> 日志评测”这条 RAG 主链路组织。
