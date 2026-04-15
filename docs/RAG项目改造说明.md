# Team RAG Knowledge 项目改造说明

本文档结合当前项目实际代码和截图中的预装运行环境，说明这个 RAG 项目应该使用哪些语言版本、哪些技术栈，以及后续升级时各环境分别承担什么角色。

## 1. 当前项目定位

当前项目是一个基于 Spring Boot 的企业级 RAG 知识库系统，核心目标不是单纯聊天，而是围绕“文档上传、知识入库、检索增强、长期记忆、链路日志、Agent 工具化”形成一套可运行、可扩展的知识库问答平台。

当前已经具备的主要能力：

- 文档上传与知识库入库
- PDF、DOC、DOCX、Markdown、TXT 等文档解析
- 语义分块与向量入库
- Pinecone 向量检索
- BM25 与向量混合检索
- Query Rewrite 查询重写
- Redis 短期会话记忆，默认保存 3 天
- RAG 知识库与长期记忆分离管理
- DashScope 与 DeepSeek 多模型可选
- SSE 流式问答输出
- 链路日志与评测接口
- 轻量 Agent 与 MCP 工具化入口

## 2. 预装环境版本选择

截图中可选环境如下：

| 环境 | 截图版本 | 当前项目是否需要 | 建议用途 |
| --- | --- | --- | --- |
| Java | 17 | 必须 | Spring Boot 后端主语言 |
| Node.js | 22 | 可选 | 如果后续把静态页面升级成 Vue/React 前端再使用 |
| Python | 3.14 | 可选 | RAG 评测脚本、数据清洗、模型实验 |
| Go | 1.25.1 | 暂不需要 | 高性能网关、独立文件处理服务可选 |
| Rust | 1.92.0 | 暂不需要 | 高性能文本解析、向量计算组件可选 |
| Ruby | 3.4.4 | 不需要 | 当前项目没有 Ruby 生态依赖 |
| PHP | 8.5 | 不需要 | 当前项目没有 PHP 服务 |
| Swift | 6.2 | 不需要 | 当前项目没有 iOS/macOS 客户端 |

当前项目最合适的版本组合：

- 后端：`Java 17`
- 构建：`Maven Wrapper`
- 前端：原生 `HTML + CSS + JavaScript`
- 数据库：`MySQL 8.x`
- 缓存：`Redis 6+ / 7+`
- 向量库：`Pinecone`
- 大模型：`DashScope qwen-plus`、`DeepSeek chat`
- Embedding：`DashScope text-embedding-v3`

## 3. 为什么 Java 17 是主版本

项目的 `pom.xml` 已经明确配置：

```xml
<java.version>17</java.version>
```

同时项目使用的是：

```xml
<spring-boot.version>3.2.6</spring-boot.version>
```

Spring Boot 3.x 要求 Java 17 起步，所以截图中 Java 17 是最稳妥的选择。虽然本地测试时也可以用 JDK 21 编译运行，但生产和文档标准建议保持 Java 17，避免团队环境不一致。

推荐命令：

```powershell
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run --spring.profiles.active=local
```

## 4. Node.js 22 在当前项目中的位置

当前前端页面位于：

```text
src/main/resources/static/
```

目前是原生静态页面，不需要 `package.json`，也不需要 npm 构建，所以 Node.js 22 暂时不是必须项。

如果后续要升级成更完整的前端工程，可以使用 Node.js 22 做这些事情：

- 使用 Vue / React 重构前端页面
- 增加前端路由
- 增加组件化上传弹窗、MCP 调试面板、Trace 可视化面板
- 使用 Vite 打包前端资源
- 用 ECharts 展示 RAG 评测指标

建议后续前端升级路线：

```text
当前静态页面
  -> Vite + Vue/React
  -> 组件化知识库管理
  -> Agent Trace 可视化
  -> MCP Tool 调试面板
```

## 5. Python 3.14 在当前项目中的位置

Python 不是当前后端主语言，但非常适合做 RAG 辅助能力。

建议用于：

- 构造 RAG 评测集
- 批量清洗文档
- 统计 chunk token 分布
- 分析 Recall@K、Precision@K、MRR
- 生成 bad case 报告
- 后续做 OCR、多模态处理、模型实验

但是注意：当前生产主链路仍然建议放在 Java 里，Python 更适合作为离线工具或独立实验服务，不要一开始就把核心问答链路拆得太散。

## 6. Go、Rust、Ruby、PHP、Swift 的取舍

这些环境虽然截图中都有，但当前项目不用全部引入。技术栈越多，维护成本越高。

当前建议：

- Go：暂不使用，除非后续需要独立高并发文件上传网关。
- Rust：暂不使用，除非后续需要极致性能的解析或检索组件。
- Ruby：不使用。
- PHP：不使用。
- Swift：不使用，除非未来要做 iOS/macOS 客户端。

结论：当前阶段保持 `Java + 原生前端 + MySQL + Redis + Pinecone + LLM API` 就足够清晰。

## 7. 当前后端模块对应关系

| 模块 | 说明 |
| --- | --- |
| `controller` | 对外 HTTP 接口，包括上传、问答、Agent、MCP、日志、评测 |
| `service` | 核心业务逻辑，包括文档处理、RAG 问答、长期记忆、链路日志 |
| `client` | 外部服务客户端，包括 DashScope、DeepSeek、Pinecone |
| `entity` | MySQL 持久化实体 |
| `repository` | Spring Data JPA 数据访问层 |
| `bean` | 请求、响应、DTO、VO |
| `mcp` | MCP 工具化封装层 |
| `util` | BM25、语义切分、token 计算等工具类 |
| `config` | 模型、向量库、数据库、追踪等配置 |

## 8. 当前核心链路

### 文档入库链路

```text
上传文件
  -> 文件安全校验
  -> 文本解析
  -> 语义分块
  -> MySQL 保存文档与 chunk
  -> DashScope 生成 embedding
  -> Pinecone 向量入库
  -> 文档状态变为 READY
```

### 问答链路

```text
用户问题
  -> 读取短期记忆
  -> Query Rewrite
  -> BM25 召回
  -> Pinecone 向量召回
  -> RRF 融合与轻量重排
  -> Prompt 拼接
  -> DashScope / DeepSeek 生成答案
  -> SSE 流式返回
  -> 写入 Redis 会话记忆
```

### Agent / MCP 链路

```text
用户问题
  -> Agent 判断是否需要工具
  -> 调用 MCP Tool Registry
  -> 工具执行 RAG / Memory / Trace / Evaluation 能力
  -> Agent 汇总结果
  -> 返回答案与步骤
```

## 9. 推荐开发环境

本项目推荐环境：

```text
JDK: 17
Maven: 使用项目自带 mvnw.cmd
MySQL: 8.x
Redis: 6.x 或 7.x
浏览器: Chrome / Edge
IDE: IntelliJ IDEA 或 VS Code
```

本地配置文件：

```text
src/main/resources/application-local.yml
```

注意：`application-local.yml` 用于放本地数据库密码、API Key 等敏感配置，不应该提交到 Git。

## 10. 后续升级建议

短期建议：

- 给 MCP 工具化增加前端调试面板
- 把轻量 Agent 的内部步骤全部改成 Tool 调用
- 给 Trace 页面增加更清晰的阶段耗时图
- 给文档管理增加重新上传与版本管理

中期建议：

- 使用 Node.js 22 + Vite 重构前端
- 使用 Python 增加离线评测脚本
- 增加真正的 Reranker 服务
- 增加标准 MCP Server 适配层

长期建议：

- 支持多租户权限
- 支持企业级审计日志
- 支持多模态 OCR 和图表理解
- 支持 Grafana Loki / ELK 可观测平台

## 11. 一句话结论

结合截图中的版本环境，当前项目最应该使用的是 `Java 17` 作为后端主版本；`Node.js 22` 和 `Python 3.14` 可以作为后续前端工程化、评测和数据处理的辅助环境；Go、Rust、Ruby、PHP、Swift 暂时不需要引入，避免项目复杂度过高。
