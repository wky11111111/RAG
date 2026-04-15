package com.team.rag.controller;

import com.team.rag.bean.ChunkUploadCompleteRequest;
import com.team.rag.bean.ChunkUploadInitRequest;
import com.team.rag.bean.ChunkUploadStatusResponse;
import com.team.rag.bean.UploadPolicy;
import com.team.rag.bean.UploadResponse;
import com.team.rag.service.AdminGuardService;
import com.team.rag.service.ChunkedUploadService;
import com.team.rag.service.UploadPolicyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/upload")
public class UploadController {

    private final UploadPolicyService uploadPolicyService;
    private final ChunkedUploadService chunkedUploadService;
    private final AdminGuardService adminGuardService;

    public UploadController(UploadPolicyService uploadPolicyService,
                            ChunkedUploadService chunkedUploadService,
                            AdminGuardService adminGuardService) {
        this.uploadPolicyService = uploadPolicyService;
        this.chunkedUploadService = chunkedUploadService;
        this.adminGuardService = adminGuardService;
    }

    @GetMapping("/policy")
    public UploadPolicy policy() {
        return uploadPolicyService.currentPolicy();
    }

    @PutMapping("/policy")
    public UploadPolicy updatePolicy(@RequestHeader(value = "X-Admin-Token", required = false) String adminToken,
                                     @RequestBody UploadPolicy policy) {
        adminGuardService.requireAdmin(adminToken);
        return uploadPolicyService.updatePolicy(policy);
    }

    @PostMapping("/chunk/init")
    public ChunkUploadStatusResponse initChunkUpload(@RequestBody ChunkUploadInitRequest request) {
        return chunkedUploadService.initiate(request);
    }

    @PostMapping("/chunk")
    public ChunkUploadStatusResponse uploadChunk(@RequestParam String uploadId,
                                                 @RequestParam int chunkIndex,
                                                 @RequestParam(required = false) String chunkSha256,
                                                 @RequestParam MultipartFile chunk) {
        return chunkedUploadService.uploadChunk(uploadId, chunkIndex, chunkSha256, chunk);
    }

    @GetMapping("/chunk/{uploadId}")
    public ChunkUploadStatusResponse chunkStatus(@PathVariable String uploadId) {
        return chunkedUploadService.status(uploadId);
    }

    @PostMapping("/chunk/complete")
    public UploadResponse complete(@RequestBody ChunkUploadCompleteRequest request) {
        return chunkedUploadService.complete(request.getUploadId());
    }
}
