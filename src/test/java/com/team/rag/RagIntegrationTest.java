package com.team.rag;

import com.jayway.jsonpath.JsonPath;
import com.team.rag.bean.PineconeMatch;
import com.team.rag.client.DashScopeClient;
import com.team.rag.client.PineconeClient;
import com.team.rag.service.ConversationMemoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RagIntegrationTest {

    private static final Path TEST_DIR = createTempDir();

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DashScopeClient dashScopeClient;

    @MockBean
    private PineconeClient pineconeClient;

    @MockBean
    private ConversationMemoryService conversationMemoryService;

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    private ValueOperations<String, String> valueOperations;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:ragdb;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("rag.storage.upload-dir", () -> TEST_DIR.resolve("uploads").toString());
    }

    @BeforeEach
    void setUp() {
        valueOperations = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        doNothing().when(valueOperations).set(anyString(), anyString(), org.mockito.ArgumentMatchers.any());

        when(dashScopeClient.embedAll(anyList())).thenAnswer(invocation -> {
            List<String> texts = invocation.getArgument(0);
            return texts.stream()
                    .map(text -> List.of(0.11f, 0.22f, 0.33f, 0.44f))
                    .toList();
        });
        when(dashScopeClient.chat(anyList())).thenReturn("差旅报销需要提交交通发票、酒店发票和出差审批单。");
        when(pineconeClient.query(anyList(), anyInt())).thenReturn(List.<PineconeMatch>of());
        doNothing().when(pineconeClient).upsert(anyList());
        when(conversationMemoryService.getRecentMessages(anyLong(), anyInt())).thenReturn(List.of());
    }

    @Test
    void homePageShouldLoad() throws Exception {
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Team RAG Knowledge")));
    }

    @Test
    void shouldUploadDocumentAndAnswerFromKnowledgeBase() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "reimbursement.md",
                "text/markdown",
                """
                # 差旅报销制度

                员工差旅报销需要提交交通发票、酒店发票和出差审批单。
                金额超过 2000 元时，还需要补充部门负责人签字。
                所有资料需要在出差结束后 5 个工作日内提交。
                """.getBytes(UTF_8)
        );

        mockMvc.perform(multipart("/api/document/upload")
                        .file(file)
                        .param("docType", "财务制度")
                        .param("description", "用于联调的测试文档"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.chunkCount", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.indexStatus").value("READY"));

        mockMvc.perform(post("/api/rag/qa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "query": "差旅报销需要提交什么材料？",
                                  "memoryId": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer", containsString("交通发票")))
                .andExpect(jsonPath("$.citations[0].documentName").value("reimbursement.md"));
    }

    @Test
    void shouldDeleteUploadedRagDocument() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "delete-me.md",
                "text/markdown",
                "这是一份用于删除接口测试的知识库文件。".getBytes(UTF_8)
        );

        mockMvc.perform(multipart("/api/document/upload")
                        .file(file)
                        .param("docType", "删除测试")
                        .param("categoryPath", "test/delete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        String listBody = mockMvc.perform(get("/api/document/list")
                        .param("sourceKind", "RAG")
                        .param("categoryPath", "test/delete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("delete-me.md"))
                .andReturn()
                .getResponse()
                .getContentAsString(UTF_8);

        String documentId = JsonPath.read(listBody, "$[0].id");

        mockMvc.perform(delete("/api/document/{documentId}", documentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/document/list")
                        .param("sourceKind", "RAG")
                        .param("categoryPath", "test/delete"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    private static Path createTempDir() {
        try {
            return Files.createTempDirectory("team-rag-test-");
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
