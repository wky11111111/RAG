# MCP 工具化说明

本文档说明当前项目完成的第一步 MCP 工具化：先把已有 RAG 能力封装成统一工具入口，后续可以继续接入标准 MCP Server、Agent Planner 或外部自动化客户端。

## 1. 当前实现目标

本阶段没有重写业务链路，而是把现有 Service 能力做成“可发现、可调用、可观测”的工具层：

- 工具发现：`GET /api/mcp/tools`
- 工具调用：`POST /api/mcp/call`
- 统一返回：`success/result/error/costTimeMs`
- 统一日志：所有工具调用都会写入 `mcp` 模块链路日志
- 安全保护：删除类工具必须显式传入 `confirm=true`

这样做的好处是：前端原接口不变，Agent 和 MCP 后续可以复用同一套工具定义。

## 2. 已封装工具

| 工具名 | 作用 | 是否危险操作 |
| --- | --- | --- |
| `rag.search_knowledge` | 基于 RAG 知识库回答问题，支持分类过滤和模型选择 | 否 |
| `rag.search_memory` | 基于长期记忆回答问题，按 `userId/conversationId` 隔离 | 否 |
| `rag.list_documents` | 查询知识库或长期记忆文档元数据 | 否 |
| `rag.get_document_chunks` | 查询某个文档的切片内容 | 否 |
| `rag.delete_document` | 删除 RAG 文档或长期记忆 | 是 |
| `rag.reindex_document` | 对已有文档重新切分、向量化、入库 | 是 |
| `rag.get_trace_logs` | 根据 `traceId/userId` 查询链路日志 | 否 |
| `rag.run_evaluation` | 运行 RAG 评测集并返回 Recall/Precision | 否 |

## 3. 调用示例

查询工具列表：

```http
GET /api/mcp/tools
```

调用知识库问答：

```http
POST /api/mcp/call
Content-Type: application/json

{
  "name": "rag.search_knowledge",
  "arguments": {
    "query": "八股文档讲了什么？",
    "topK": 5,
    "categoryPath": "八股",
    "aiProvider": "dashscope"
  }
}
```

查询文档列表：

```http
POST /api/mcp/call
Content-Type: application/json

{
  "name": "rag.list_documents",
  "arguments": {
    "sourceKind": "RAG",
    "projectName": "HMDP"
  }
}
```

删除文档：

```http
POST /api/mcp/call
Content-Type: application/json

{
  "name": "rag.delete_document",
  "arguments": {
    "documentId": "xxx",
    "sourceKind": "RAG",
    "confirm": true
  }
}
```

## 4. 与标准 MCP 的关系

当前实现是“项目内部 MCP 工具层”，还不是完整的 MCP JSON-RPC Server。它已经具备标准 MCP 需要的三个核心要素：

- 工具定义：工具名、描述、参数 schema
- 工具调用：按工具名执行
- 工具结果：结构化返回

下一步如果要接真正 MCP，可以新增一个适配层，把 `/api/mcp/tools` 和 `/api/mcp/call` 映射到 MCP 协议的 `tools/list` 与 `tools/call`。

## 5. 后续建议

- 增加工具权限：区分普通用户、管理员、Agent 内部调用。
- 增加工具版本：例如 `rag.search_knowledge@v1`。
- 增加工具超时和熔断：避免 LLM 或向量库阻塞工具链。
- 给前端加一个 MCP 调试面板，方便直接输入工具参数调试。
- 把轻量 Agent 的每一步都改成调用这些工具，形成 `plan -> tool -> observe -> reflect` 链路。
