# Agentic RAG 项目升级学习路线

本文档用于指导当前 `Team RAG Knowledge` 项目升级到“基于 Agentic RAG 的智能问答/会议纪要协作系统”。目标不是简单加几个接口，而是把项目从“传统 RAG 问答”升级为“可规划、可调用工具、可处理多模态资料、可评测优化的企业级 Agentic RAG 系统”。

## 1. 目标项目画像

截图中的目标项目核心能力可以拆成四条主线：

- 架构升级：从 `DeepSeek V3.1` 或同类大模型出发，设计“任务拆解 -> 工具调度 -> 结果整合 -> 自我反思”的 Agent 工作流。
- 多模态处理：对会议纪要、协议文档、流程图、图片、表格等材料做 OCR、版面解析、图表理解和结构化描述。
- 检索增强：用 `BGE-M3 Embedding + BM25 + RRF 融合 + BGE Reranker` 形成多路召回和重排序链路。
- 质量闭环：通过 Query Rewrite、Recall@K、Precision@K、人工标注集、日志追踪和错误分析持续优化效果。

当前项目已经具备：文件上传、分片上传、RAG/MEMORY 分离、Pinecone 向量库、DashScope LLM、查询重写、BM25 + 向量混合检索、流式回答、链路日志。后续升级重点是“Agent 化、多模态化、评测体系化、工具化”。

## 2. 必须掌握的知识地图

### 2.1 Java 后端与工程基础

需要掌握：

- Spring Boot 分层架构：Controller、Service、Repository、Config、DTO/VO。
- REST API 与 SSE 流式输出。
- MySQL 表结构设计、索引、事务、分页查询。
- Redis 缓存、TTL、分布式状态保存。
- 异步任务：线程池、MDC 上下文传递、任务状态追踪。
- 文件上传：普通上传、分片上传、断点续传、checksum 校验。
- 可观测性：traceId、结构化日志、链路耗时、异常栈、ELK/Loki 接入思路。

对应当前项目模块：

- `DocumentService`：文档解析、切分、向量入库。
- `RagQAService`：问答、查询重写、检索、LLM 响应。
- `ChunkedUploadService`：大文件分片上传。
- `ObservabilityService`：链路日志。

### 2.2 RAG 基础

需要掌握：

- 文档解析：PDF、Word、Markdown、HTML、纯文本。
- 文本清洗：去噪、去页眉页脚、去重复段落、保留结构。
- 文档切分：固定长度切分、递归切分、标题层级切分、语义切分。
- Embedding：向量维度、归一化、相似度、批量 embedding 限制。
- 向量数据库：Pinecone namespace、metadata filter、upsert、delete、query。
- Prompt 拼接：系统提示词、上下文片段、引用约束、防幻觉指令。
- 引用生成：按 chunk 引用、按文件聚合引用、引用置信度。

推荐学习顺序：

1. 先理解“一个文件如何变成多个 chunk”。
2. 再理解“一个问题如何变成 query embedding”。
3. 再理解“向量召回如何变成上下文 prompt”。
4. 最后理解“为什么答案必须带引用和边界约束”。

### 2.3 混合检索与重排序

目标项目里提到：

- `BGE-M3 Embedding`
- `BM25`
- `RRF 融合`
- `BGE-Reranker`
- `Top-50 -> Top-20 -> Top-10`
- `Recall@10`
- `Precision@10`

需要掌握：

- BM25：关键词召回，适合精确术语、编号、制度条款。
- Dense Vector：语义召回，适合同义表达、上下文理解。
- RRF：Reciprocal Rank Fusion，用于融合多个召回列表。
- Reranker：对候选结果做二次精排，提高 TopK 质量。
- 多阶段检索：先高召回，再精排序，最后进入 prompt。
- metadata filter：按业务线、项目、部门、文件类型过滤。

建议升级路径：

1. 当前项目已有 BM25 + Pinecone 融合，可以先替换融合逻辑为 RRF。
2. 增加 Reranker 接口，例如 DashScope rerank、BGE reranker 或本地模型服务。
3. 记录每次检索的 `recallCount`、`topKScore`、`sourceDocument`、`chunkId`。
4. 建立评测集，计算 Recall@K 和 Precision@K。

### 2.4 Query Rewrite 与多轮上下文

目标项目强调“RAG Rewrite 策略”和“多轮问答信息关联”。

需要掌握：

- 短期记忆：最近 N 轮对话。
- 长期记忆：用户偏好、业务事实、历史确认信息。
- 查询改写：把“那这个怎么处理”改写成完整问题。
- 查询扩展：生成同义词、业务术语、可能的检索关键词。
- 查询分解：把复杂问题拆成多个子问题。
- 二次检索：当 Agent 判断召回不足时，自动改写并再次检索。

示例：

```text
用户：如果骑手超时了怎么办？
下一轮用户：那超过 30 分钟呢？

改写后：
外卖配送场景中，骑手配送超时超过 30 分钟时，客服应该如何处理，是否需要补偿？
```

当前项目已有基础 Query Rewrite，后续要升级为：

- `rewrite`
- `decompose`
- `retrieve`
- `judge`
- `retry retrieve`
- `answer`

### 2.5 Agentic RAG

Agentic RAG 与传统 RAG 的区别：

- 传统 RAG：用户问题 -> 检索 -> 拼 prompt -> 回答。
- Agentic RAG：用户问题 -> 判断意图 -> 拆任务 -> 选择工具 -> 多轮检索/计算/生成 -> 自检 -> 回答。

需要掌握：

- Agent 状态机：Plan、Act、Observe、Reflect。
- 工具调用：检索工具、图表解析工具、数据库查询工具、Mermaid 生成工具。
- Function Calling / Tool Calling 思想：用 JSON Schema 定义工具入参出参。
- 任务编排：单 Agent、Multi-Agent、Supervisor Agent。
- 失败恢复：工具失败重试、低置信度二次检索、无答案拒答。
- 自我反思：检查答案是否基于引用、是否遗漏子问题、是否需要补充检索。

建议先实现一个轻量 Agent：

```text
用户问题
  -> 判断问题类型
  -> 是否需要检索
  -> 是否需要查询长期记忆
  -> 是否需要图表/流程图工具
  -> 汇总上下文
  -> 生成答案
  -> 检查引用完整性
```

### 2.6 多模态文档处理

截图里提到“多模态图表解析、流程图、结构化文本描述、目标 Mermaid 代码”。

需要掌握：

- OCR：识别扫描 PDF、图片里的文字。
- 版面分析：标题、段落、表格、图片、页眉页脚。
- 表格抽取：HTML table、Excel-like table、PDF 表格。
- 图片理解：对流程图、架构图、截图进行视觉问答。
- Mermaid：把流程图、时序图、状态图转成可编辑代码。
- 多模态数据集：原始图片 + 结构化文本描述 + Mermaid 代码。

建议升级路径：

1. 文档上传时识别图片页和图表页。
2. 对图片调用多模态模型生成结构化描述。
3. 对流程图类图片生成 Mermaid。
4. 把“图片描述”和“Mermaid 代码”作为独立 chunk 入库。
5. 回答时引用图片来源页码和图表标题。

### 2.7 数据集与微调

截图里提到“精选 50 张典型图表，构建原始图片 + 结构化文本描述 + 目标 Mermaid 代码数据集，基于 QLoRA 对 Qwen2.5-VL-7B 微调”。

需要掌握：

- 数据标注规范：输入是什么，输出是什么，质量标准是什么。
- 多模态训练样本格式：image、instruction、response。
- LoRA/QLoRA：低成本参数高效微调。
- 训练集/验证集/测试集划分。
- 过拟合检查。
- 微调前后效果对比。

对于当前项目，建议先不要立刻微调。更稳的路线是：

1. 先用现成多模态模型完成图表描述。
2. 收集失败案例。
3. 人工修正成高质量样本。
4. 样本达到 200-500 条后再考虑 LoRA/QLoRA。

## 3. 升级路线图

### 阶段一：补齐企业级 RAG 基座

目标：让当前项目成为稳定可用的 RAG 基座。

需要完成：

- 文档上传配额可视化。
- RAG/MEMORY 分类管理。
- 删除、更新、重新索引文档。
- 链路日志可查。
- 检索结果可视化。
- 召回结果导出。
- Prompt 模板管理。

验收标准：

- 能上传 PDF/Word/Markdown。
- 能按业务线/场景/项目/部门过滤。
- 能删除并重新上传文档。
- 能查看一次问答的完整 trace。
- 能看到每次召回了哪些 chunk。

### 阶段二：升级检索链路

目标：从普通混合检索升级成可评测的多阶段检索。

需要完成：

- BM25 召回 Top50。
- 向量召回 Top50。
- RRF 融合 Top20。
- Reranker 精排 Top10。
- 低召回自动 Query Rewrite 后二次检索。
- 记录 Recall@K、Precision@K。

验收标准：

- 同一问题能看到 BM25、Vector、RRF、Rerank 各阶段结果。
- 评测集 Recall@10 达到设定阈值。
- 分类过滤后召回结果不串库。

### 阶段三：Agent 化

目标：让系统具备任务拆解和工具调度能力。

需要完成：

- Agent Planner：判断用户问题是否需要拆解。
- Tool Registry：注册检索、长期记忆、文档查询、Mermaid 生成等工具。
- Agent Executor：按计划调用工具。
- Reflection：检查答案是否足够、是否需要二次检索。
- Agent Trace：记录每一步计划、工具入参、工具出参和耗时。

验收标准：

- 多跳问题可以自动拆成子问题。
- 检索不足时可以自动改写再查。
- 回答能解释使用了哪些资料和工具。

### 阶段四：多模态文档理解

目标：支持会议材料、流程图、协议图表等复杂资料。

需要完成：

- OCR 管线。
- PDF 页面级解析。
- 图片/图表抽取。
- 多模态模型生成图表描述。
- 流程图转 Mermaid。
- 图片描述 chunk 入库。
- 引用页码和图表元数据。

验收标准：

- 上传含流程图的 PDF 后，系统能检索到图表含义。
- 用户问“图里的流程是什么”时，能基于图表描述回答。
- 能生成或展示 Mermaid 代码。

### 阶段五：评测与优化闭环

目标：让系统效果可度量、可持续优化。

需要完成：

- 标注测试集。
- 自动评测脚本。
- Recall@K、Precision@K、MRR、答案引用准确率。
- Bad Case 管理。
- Prompt 版本管理。
- 检索参数实验。

验收标准：

- 每次改检索策略后能跑评测。
- 能证明效果提升，而不是凭感觉。
- 能定位失败原因：解析失败、切分失败、召回失败、重排失败、回答失败。

## 4. 推荐学习顺序

### 第 1 周：夯实当前项目

- 理解当前 Spring Boot 项目结构。
- 理解上传 -> 解析 -> 切分 -> embedding -> Pinecone 的链路。
- 理解问答 -> 查询重写 -> BM25/向量召回 -> prompt -> LLM 的链路。
- 学会查看链路日志和 traceId。

产出：

- 能画出当前项目架构图。
- 能独立解释一次文件上传和一次问答的完整链路。

### 第 2 周：混合检索和评测

- 学 BM25、向量检索、RRF、Reranker。
- 做 20-50 条问答评测集。
- 加 Recall@K 和 Precision@K 统计。

产出：

- 一个 `evaluation.md` 或评测脚本。
- 一张检索链路对比表。

### 第 3 周：Agentic RAG

- 学 Plan-Act-Observe-Reflect。
- 设计 Tool Registry。
- 实现基础 Planner。
- 加二次检索和自检。

产出：

- `AgentPlan`
- `ToolCall`
- `AgentTrace`
- 一个可运行的 Agentic QA 接口。

### 第 4 周：多模态处理

- 学 OCR、PDF 页面解析、图片理解。
- 实现图片描述 chunk。
- 尝试流程图转 Mermaid。

产出：

- 上传带图片的文档后，能检索图片内容。
- 图表有独立元数据：页码、图片 ID、描述、Mermaid。

### 第 5 周及以后：微调和生产化

- 收集 bad case。
- 建立标注规范。
- 积累多模态样本。
- 再考虑 QLoRA 微调。
- 接入 ELK/Loki/Grafana。
- 做权限、租户、审计、安全扫描。

## 5. 当前项目建议新增模块

建议后续新增这些包：

```text
com.team.rag.agent
com.team.rag.agent.tool
com.team.rag.agent.plan
com.team.rag.eval
com.team.rag.multimodal
com.team.rag.rerank
com.team.rag.ocr
```

建议新增实体：

```text
AgentTraceEntity
AgentStepEntity
EvaluationDatasetEntity
EvaluationCaseEntity
EvaluationRunEntity
DocumentPageEntity
DocumentImageEntity
DocumentTableEntity
```

建议新增接口：

```text
POST /api/agent/qa/stream
GET  /api/agent/trace/{traceId}
POST /api/evaluation/run
GET  /api/evaluation/runs
POST /api/document/{documentId}/reindex
GET  /api/document/{documentId}/chunks
GET  /api/document/{documentId}/assets
```

## 6. 需要重点补的能力

### 6.1 Reranker

当前项目已有 BM25 + 向量融合，但还缺真正的 cross-encoder reranker。

学习重点：

- Reranker 输入：query + passage。
- Reranker 输出：相关性分数。
- 为什么 reranker 比 embedding 更适合精排。
- 如何控制 reranker 成本。

### 6.2 Agent 工具调用

当前项目是 Service 直接串流程，后续需要抽象工具。

示例工具：

```text
retrieve_knowledge
retrieve_memory
rewrite_query
rerank_chunks
parse_mermaid
summarize_document
inspect_upload_status
```

### 6.3 多模态图表理解

当前项目主要处理文本，目标项目需要理解图表。

学习重点：

- 图片 OCR。
- 图表标题识别。
- 流程节点识别。
- 箭头关系识别。
- Mermaid 生成。

### 6.4 评测体系

没有评测就无法证明升级有效。

至少需要：

- 问题。
- 标准答案。
- 标准引用文档。
- 标准引用 chunk。
- 分类过滤条件。
- 期望召回结果。

## 7. 推荐小项目练习

### 练习一：实现 RRF 融合

输入 BM25 排名和向量排名，输出融合排名。

目标：

- 理解 rank-based fusion。
- 替换当前简单加权分数。

### 练习二：实现 Reranker 接口

输入 query 和 Top20 chunks，输出 Top10。

目标：

- 理解二阶段检索。
- 记录 rerank 前后变化。

### 练习三：实现 Agent Trace

记录一次 Agent 问答的每一步：

```text
plan -> rewrite -> retrieve -> rerank -> reflect -> answer
```

目标：

- 让 Agent 行为可解释。

### 练习四：流程图转 Mermaid

上传一张流程图图片，输出：

- 图片文字 OCR。
- 流程节点。
- 节点关系。
- Mermaid 代码。

目标：

- 为多模态 RAG 打基础。

## 8. 最终能力对标

升级完成后，项目应具备：

- 能上传会议纪要、协议、PDF、Word、流程图。
- 能自动解析文本、表格、图片、图表。
- 能基于分类和权限过滤召回资料。
- 能使用 BM25 + Embedding + RRF + Reranker 多阶段检索。
- 能在召回不足时自动 Query Rewrite 并二次检索。
- 能通过 Agent 拆解复杂问题并调用工具。
- 能流式输出答案并给出引用。
- 能查看完整 Agent Trace 和 RAG Trace。
- 能用评测集证明效果提升。

## 9. 一句话总结

如果说当前项目是“能上传文档并基于知识库回答问题”的传统 RAG，那么目标项目就是“能理解复杂资料、会自己规划检索步骤、会调用工具、会反思答案质量、并能被评测证明效果”的 Agentic RAG 系统。
