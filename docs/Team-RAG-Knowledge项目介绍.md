# Team RAG Knowledge 项目介绍

## 1. 项目概述

`Team RAG Knowledge` 是一个基于 Spring Boot 的企业级 RAG 知识库问答项目。项目目标是把企业内部文档、业务规则、长期记忆和多轮会话整合成一个可检索、可追踪、可评测、可扩展的智能问答系统。

当前项目已经不是单纯的“上传文档后问答”Demo，而是具备完整工程链路的 RAG 应用基座：

- 支持文档上传、分片上传、断点续传、配额控制和文件安全校验。
- 支持 PDF、Word、Markdown、TXT、JSON、HTML、CSV 等文本型资料解析。
- 支持 RAG 知识库和长期记忆分离管理，避免知识文档和用户记忆互相污染。
- 支持 BM25 + 向量检索 + RRF 融合 + 轻量重排序。
- 支持查询重写、短期记忆、多轮上下文、Redis 3 天会话保存。
- 支持流式回答、引用展示、系统提示词动态配置。
- 支持轻量级 Agentic RAG，能够展示规划、检索、生成等执行步骤。
- 支持链路日志、traceId、阶段耗时、异常记录和 RAG 效果评测。

一句话概括：这是一个面向企业知识问答场景的“可运行、可维护、可观测、可继续升级为 Agentic RAG”的知识库系统。

## 2. 技术栈

后端技术：

- Java 17
- Spring Boot 3.2.6
- Spring Web
- Spring Data JPA
- Spring Data Redis
- MySQL
- Apache Tika
- Maven

AI 与检索：

- DashScope Chat Model，默认 `qwen-plus`
- DashScope Embedding Model，默认 `text-embedding-v3`
- Pinecone 向量数据库
- BM25 关键词检索
- RRF 多路召回融合
- 轻量文本重排序

前端技术：

- 原生 HTML
- 原生 JavaScript
- CSS
- SSE 流式输出

存储组件：

- MySQL：文档元数据、chunk 数据、链路日志。
- Redis：短期记忆、系统提示词、上传策略缓存。
- Pinecone：RAG 知识库向量、长期记忆向量。
- 本地磁盘：上传原始文件与分片临时文件。

## 3. 核心功能

### 3.1 文件上传与知识库构建

项目支持普通上传和超大文件分片上传。

普通上传适合中小型文档，接口为：

```http
POST /api/document/upload
```

分片上传适合大文件，链路为：

```text
初始化上传 -> 上传分片 -> 查询上传状态 -> 合并分片 -> 文本解析 -> 文档切分 -> Embedding -> Pinecone 入库
```

对应接口：

```http
POST /api/upload/chunk/init
POST /api/upload/chunk
GET  /api/upload/chunk/{uploadId}
POST /api/upload/chunk/complete
```

上传限制支持动态配置：

- 单文件普通上传大小上限。
- 分片大小上限。
- 分片上传总大小上限。
- 系统每日总上传容量。
- 单用户每日上传容量。
- 单用户总存储容量。
- 文件类型白名单。

配置查询与更新接口：

```http
GET /api/upload/policy
PUT /api/upload/policy
```

### 3.2 文档解析与切分

项目使用 Apache Tika 解析文档内容，支持：

- `txt`
- `md`
- `markdown`
- `csv`
- `json`
- `xml`
- `html`
- `htm`
- `log`
- `yml`
- `yaml`
- `pdf`
- `doc`
- `docx`

当前文档切分使用工程化轻量语义切分策略：

- 优先按段落切分。
- 保留中文和英文句末边界。
- 支持 chunk overlap。
- 控制单个 chunk 最大长度。
- 保存 chunkIndex、tokenCount、documentId 等元数据。

注意：当前项目暂未实现图片 OCR、图表理解、流程图转 Mermaid 等多模态解析能力。

### 3.3 RAG 知识库管理

RAG 知识库使用 `sourceKind=RAG` 独立管理，适合存放企业文档、业务规则、产品说明、客服手册、项目资料等。

支持的分类元数据包括：

- 多级分类路径。
- 业务场景。
- 项目。
- 部门。
- 文档类型。
- 用户 ID。
- 会话 ID。

常用接口：

```http
GET    /api/document/list
DELETE /api/document/{documentId}
GET    /api/document/{documentId}/chunks
POST   /api/document/{documentId}/reindex
```

这些接口可以实现：

- 查看知识库文档列表。
- 查看某个文档的切块内容。
- 删除不需要的文档。
- 对文档重新索引。

### 3.4 长期记忆管理

长期记忆使用 `sourceKind=MEMORY` 独立管理，和 RAG 知识库物理隔离。

长期记忆适合存放：

- 用户偏好。
- 用户身份信息。
- 历史确认过的业务事实。
- 会话沉淀出的长期信息。
- 手动录入的重要背景。

常用接口：

```http
POST   /api/document/manual
GET    /api/document/memory/list
PUT    /api/document/memory/{documentId}
DELETE /api/document/memory/{documentId}
```

长期记忆支持按用户、会话和时间维度管理。问答时可以选择是否召回长期记忆，避免所有知识混在一起导致答案污染。

### 3.5 短期记忆与多轮会话

项目支持短期会话记忆，默认保存到 Redis，TTL 为 3 天。

短期记忆用于解决这类问题：

```text
用户：骑手超时怎么处理？
用户：那超过 30 分钟呢？
```

系统会结合最近对话，把第二个问题重写成更完整的检索问题：

```text
外卖配送场景中，骑手配送超时超过 30 分钟时，客服应该如何处理？
```

会话相关接口：

```http
GET /api/rag/memory/sessions
GET /api/rag/memory/{memoryId}
```

当对话 token 估算达到阈值后，系统会触发摘要机制，减少上下文过长带来的成本和性能压力。

### 3.6 查询重写

问答前，系统会将用户问题、短期记忆和必要上下文交给大模型进行查询重写。

查询重写的目标是：

- 补全省略主语。
- 消解“这个、那个、上面说的”等指代。
- 加入业务场景关键词。
- 生成更适合检索的完整问题。

查询重写后再进入 BM25 和向量检索链路。

### 3.7 混合检索与排序

项目当前检索链路如下：

```text
用户问题
  -> 查询重写
  -> BM25 关键词召回
  -> Pinecone 向量召回
  -> RRF 融合排序
  -> 轻量文本重排序
  -> TopK chunk 进入 Prompt
  -> LLM 生成答案
```

BM25 适合召回精确关键词、编号、术语和制度条款。

向量检索适合召回语义相似内容、同义表达和上下文相关片段。

RRF 用于融合多个召回列表，避免只依赖单一路径。

轻量重排序会结合检索分数和文本重合度，对最终候选 chunk 进行二次排序。

### 3.8 流式问答与引用

普通 RAG 问答接口：

```http
POST /api/rag/qa
```

流式 RAG 问答接口：

```http
POST /api/rag/qa/stream
```

前端默认使用流式输出，回答不会一次性全部出现，而是像聊天产品一样逐步输出。

引用展示做了文件级聚合：

- 同一个文件只展示一次。
- 展示文件名、命中次数、最高相似度。
- 避免一个文件多个 chunk 重复刷屏。

### 3.9 轻量级 Agentic RAG

项目已经加入轻量 Agent 能力。

Agent 问答接口：

```http
POST /api/agent/qa
POST /api/agent/qa/stream
```

轻量 Agent 的作用是把传统 RAG 链路拆成可解释步骤：

```text
接收问题
  -> 判断是否需要长期记忆
  -> 规划检索来源
  -> 调用 RAG 问答能力
  -> 观察召回结果
  -> 必要时放宽来源重试
  -> 生成最终回答
```

前端开启 Agent 模式后，会显示 Agent 执行步骤。这样可以更清楚地看到系统为什么这样回答、使用了哪些检索动作。

Agent trace 查询接口：

```http
GET /api/agent/trace/{traceId}
```

### 3.10 系统提示词管理

项目支持在页面侧栏动态提交系统提示词。

接口：

```http
GET /api/system-prompt
PUT /api/system-prompt
```

系统提示词用于控制回答风格和约束，例如：

- 必须基于知识库回答。
- 不知道就说明知识库中未找到依据。
- 回答要简洁、专业、条目化。
- 不要编造文档中不存在的信息。

### 3.11 可观测性与链路日志

项目实现了统一链路日志结构。

每次请求会携带或生成 `traceId`，日志字段包括：

- traceId
- userId
- sessionId
- module
- action
- status
- costTime
- timestamp

链路日志覆盖：

```text
文件上传
  -> 安全校验
  -> 文本抽取
  -> 文档切分
  -> Embedding
  -> 向量入库
  -> 查询重写
  -> 检索召回
  -> 融合排序
  -> Prompt 拼接
  -> LLM 响应
  -> 结果返回
```

日志查询接口：

```http
GET /api/observability/logs
GET /api/observability/logs?traceId=xxx
GET /api/observability/logs?userId=u001
```

这些日志后续可以接入 ELK、Grafana Loki 或其他可观测平台。

### 3.12 RAG 效果评测

项目新增了基础 RAG 评测接口：

```http
POST /api/evaluation/run
```

评测输入包括：

- 用户问题。
- 标准答案。
- 期望命中的文档 ID。
- 检索过滤条件。
- topK。

评测输出包括：

- Recall@K。
- Precision@K。
- Hit Rate。
- 实际命中文档。
- 回答结果。

这个能力用于证明检索策略是否真的变好，而不是只凭感觉判断效果。

## 4. 系统架构

整体架构可以理解为四层。

### 4.1 前端交互层

负责：

- 文件上传。
- 分片上传。
- 分类筛选。
- 系统提示词配置。
- 知识库问答。
- Agent 模式切换。
- 引用展示。
- 链路日志查看。
- RAG 评测。

主要文件：

```text
src/main/resources/static/index.html
src/main/resources/static/app.js
src/main/resources/static/styles.css
```

### 4.2 API 控制层

负责接收 HTTP 请求并调用业务服务。

主要 Controller：

```text
DocumentController
UploadController
RagQAController
AgentController
EvaluationController
SystemPromptController
ObservabilityController
```

### 4.3 业务服务层

负责核心业务逻辑。

主要 Service：

```text
DocumentService
ChunkedUploadService
KnowledgeBaseService
RagQAService
LightAgentService
ConversationMemoryService
RedisConversationMemoryService
EvaluationService
ObservabilityService
SystemPromptService
UploadPolicyService
```

### 4.4 基础设施层

负责外部系统集成。

包括：

```text
DashScopeClient
PineconeClient
MySQL Repository
Redis
本地文件存储
```

## 5. 核心链路

### 5.1 文档入库链路

```text
用户上传文件
  -> 校验文件类型和大小
  -> 扫描高风险文件头
  -> 判断配额
  -> 保存原始文件
  -> Tika 抽取文本
  -> SemanticSplitter 切分 chunk
  -> DashScope 生成 embedding
  -> MySQL 保存文档和 chunk 元数据
  -> Pinecone 写入向量
  -> 返回上传结果
```

### 5.2 RAG 问答链路

```text
用户提问
  -> 读取短期记忆
  -> 查询重写
  -> BM25 检索
  -> Pinecone 向量检索
  -> RRF 融合
  -> 轻量重排序
  -> 拼接 Prompt
  -> DashScope 生成回答
  -> 写入短期记忆
  -> 聚合引用
  -> 流式返回给前端
```

### 5.3 Agent 问答链路

```text
用户提问
  -> Agent 生成执行步骤
  -> 判断是否需要长期记忆
  -> 选择检索范围
  -> 调用 RAG 问答工具
  -> 检查召回结果
  -> 低召回时重试
  -> 返回答案和 Agent 步骤
```

## 6. 运行方式

### 6.1 准备环境

需要准备：

- JDK 17 或更高版本。
- MySQL。
- Redis。
- Pinecone API Key。
- DashScope API Key。

### 6.2 配置本地密钥

建议创建：

```text
src/main/resources/application-local.yml
```

填写本地配置。该文件用于存放 API Key 和数据库密码，不建议提交到 Git。

示例：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/hmdp?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
    username: root
    password: your-password
  data:
    redis:
      host: 127.0.0.1
      port: 6379

dashscope:
  api-key: your-dashscope-key

pinecone:
  api-key: your-pinecone-key
  index-name: team-rag-knowledge
  namespace: team-rag-knowledge
```

### 6.3 启动项目

```powershell
.\mvnw.cmd spring-boot:run
```

启动后访问：

```text
http://localhost:8080/
```

如果前端页面没有更新，使用浏览器强制刷新：

```text
Ctrl + F5
```

### 6.4 运行测试

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
.\mvnw.cmd test
```

## 7. 项目目录说明

```text
src/main/java/com/team/rag
  bean          请求体、响应体、视图对象
  client        DashScope、Pinecone 客户端
  config        配置类、TraceFilter、属性绑定
  controller    HTTP API 控制器
  entity        JPA 实体
  repository    MySQL Repository
  service       核心业务服务
  util          BM25、语义切分、分词工具

src/main/resources
  application.yml
  application-local.yml.example
  static
    index.html
    app.js
    styles.css

docs
  Agentic-RAG升级学习路线.md
  RAG项目改造说明.md
  Team-RAG-Knowledge项目介绍.md
```

## 8. 当前项目边界

已经实现：

- 文本型文档解析和入库。
- RAG 知识库与长期记忆分离。
- 分片上传和断点续传。
- 上传配额与安全校验。
- 查询重写。
- BM25 + 向量混合检索。
- RRF 融合与轻量重排序。
- 流式回答。
- 文件级引用聚合。
- Redis 短期记忆。
- 系统提示词配置。
- 轻量 Agent。
- 链路日志。
- RAG 评测。

暂未实现：

- 图片 OCR。
- 扫描版 PDF OCR。
- 表格结构化抽取。
- 流程图理解。
- Mermaid 自动生成。
- 真正的 BGE Cross-Encoder Reranker 服务。
- 多 Agent 协作编排。
- 权限系统和多租户隔离。
- ELK、Loki、Grafana 的正式接入。

## 9. 适合的应用场景

该项目适合用于：

- 企业内部知识库问答。
- 客服话术和业务规则问答。
- 项目文档检索助手。
- 个人长期记忆助手。
- RAG 原型系统。
- Agentic RAG 学习和毕业设计项目。
- 可观测 AI 应用链路演示。

## 10. 后续升级方向

推荐升级顺序：

1. 接入真正的 Reranker 模型服务，例如 BGE Reranker 或 DashScope Rerank。
2. 完善 Agent Tool Registry，把检索、记忆、文档查询、评测等能力抽象成工具。
3. 加入 Agent Reflection，让系统在召回不足时自动改写并二次检索。
4. 建立标准评测集，持续统计 Recall@K、Precision@K、引用准确率。
5. 接入权限、租户、审计和生产级日志平台。
6. 在后续阶段再考虑 OCR、图表理解和多模态文档处理。

## 11. 项目一句话介绍

`Team RAG Knowledge` 是一个基于 Spring Boot、DashScope、Pinecone、MySQL 和 Redis 构建的企业级 RAG 知识库问答系统，支持文档上传、长期记忆、混合检索、流式问答、轻量 Agent、链路日志和效果评测，是从传统 RAG 升级到 Agentic RAG 的可运行工程基座。
