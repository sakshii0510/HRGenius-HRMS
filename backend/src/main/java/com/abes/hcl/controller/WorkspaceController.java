package com.abes.hcl.controller;

import com.abes.hcl.dto.WorkspaceRequest;
import com.abes.hcl.dto.WorkspaceResponse;
import com.abes.hcl.dto.WorkspaceStatusRequest;
import com.abes.hcl.entity.WorkspaceModule;
import com.abes.hcl.entity.WorkspaceRecord;
import com.abes.hcl.service.EmployeeAccessService;
import com.abes.hcl.service.WorkspaceService;
import jakarta.validation.Valid;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/workspace")
@PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
public class WorkspaceController {

    private final WorkspaceService workspaceService;
    private final EmployeeAccessService employeeAccess;

    public WorkspaceController(WorkspaceService workspaceService, EmployeeAccessService employeeAccess) {
        this.workspaceService = workspaceService;
        this.employeeAccess = employeeAccess;
    }

    @GetMapping("/{module}")
    public List<WorkspaceResponse> search(@PathVariable WorkspaceModule module,
                                          @RequestParam(required = false) Long employeeId,
                                          @RequestParam(required = false) String status,
                                          @RequestParam(required = false) String search,
                                          Authentication authentication) {
        return workspaceService.search(module, employeeId, status, search, authentication.getName(),
                employeeAccess.isAdmin(authentication));
    }

    @GetMapping("/{module}/{id}")
    public WorkspaceResponse get(@PathVariable WorkspaceModule module, @PathVariable Long id,
                                 Authentication authentication) {
        return workspaceService.get(module, id, authentication.getName(), employeeAccess.isAdmin(authentication));
    }

    @PostMapping("/{module}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkspaceResponse create(@PathVariable WorkspaceModule module,
                                    @Valid @RequestBody WorkspaceRequest request) {
        return workspaceService.create(module, request);
    }

    @PostMapping(value = "/DOCUMENTS/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkspaceResponse uploadDocument(@Valid @RequestPart("metadata") WorkspaceRequest request,
                                            @RequestPart("file") MultipartFile file) {
        return workspaceService.createDocument(request, file);
    }

    @PutMapping("/{module}/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public WorkspaceResponse update(@PathVariable WorkspaceModule module, @PathVariable Long id,
                                    @Valid @RequestBody WorkspaceRequest request) {
        return workspaceService.update(module, id, request);
    }

    @PutMapping("/{module}/{id}/status")
    public WorkspaceResponse updateStatus(@PathVariable WorkspaceModule module, @PathVariable Long id,
                                          @Valid @RequestBody WorkspaceStatusRequest request,
                                          Authentication authentication) {
        return workspaceService.updateStatus(module, id, request.status(), authentication.getName(),
                employeeAccess.isAdmin(authentication));
    }

    @PostMapping("/TRAINING/{id}/enroll")
    @PreAuthorize("hasRole('EMPLOYEE')")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkspaceResponse enrollTraining(@PathVariable Long id, Authentication authentication) {
        return workspaceService.enrollTraining(id, authentication.getName());
    }

    @DeleteMapping("/{module}/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable WorkspaceModule module, @PathVariable Long id) {
        workspaceService.delete(module, id);
    }

    @GetMapping("/DOCUMENTS/{id}/file")
    public ResponseEntity<ByteArrayResource> downloadDocument(@PathVariable Long id,
                                                               Authentication authentication) {
        WorkspaceRecord document = workspaceService.getDocument(
                id, authentication.getName(), employeeAccess.isAdmin(authentication));
        String fileName = document.getFileName() == null ? "document" : document.getFileName();
        MediaType contentType;
        try {
            contentType = MediaType.parseMediaType(document.getContentType());
        } catch (IllegalArgumentException | NullPointerException exception) {
            contentType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(fileName, StandardCharsets.UTF_8).build().toString())
                .contentLength(document.getFileSize())
                .body(new ByteArrayResource(document.getFileContent()));
    }
}
