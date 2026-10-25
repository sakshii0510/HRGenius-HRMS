package com.abes.hcl.service;

import com.abes.hcl.dto.WorkspaceRequest;
import com.abes.hcl.dto.WorkspaceResponse;
import com.abes.hcl.entity.Employee;
import com.abes.hcl.entity.WorkspaceModule;
import com.abes.hcl.entity.WorkspaceRecord;
import com.abes.hcl.repository.EmployeeRepository;
import com.abes.hcl.repository.WorkspaceRecordRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.Set;

@Service
@Transactional
public class WorkspaceService {

    private static final long MAX_DOCUMENT_BYTES = 10L * 1024 * 1024;
    private static final Set<String> DOCUMENT_TYPES = Set.of(
            "application/pdf",
            "image/jpeg",
            "image/png",
            "text/plain",
            "text/csv",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final WorkspaceRecordRepository records;
    private final EmployeeRepository employees;

    public WorkspaceService(WorkspaceRecordRepository records, EmployeeRepository employees) {
        this.records = records;
        this.employees = employees;
    }

    @Transactional(readOnly = true)
    public List<WorkspaceResponse> search(WorkspaceModule module, Long employeeId, String status,
                                          String search, String userEmail, boolean admin) {
        validateModuleAccess(module, admin);
        Long ownerId = admin ? null : employees.findByEmailIgnoreCase(userEmail)
                .map(Employee::getId).orElse(null);
        if (!admin && employeeId != null && !employeeId.equals(ownerId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "You can only view your own workspace records");
        }
        boolean ownerOnly = !admin;
        return records.search(module, admin ? employeeId : null, normalize(status), normalize(search),
                        ownerOnly, ownerId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public WorkspaceResponse get(WorkspaceModule module, Long id, String userEmail, boolean admin) {
        validateModuleAccess(module, admin);
        WorkspaceRecord record = records.findByIdAndModule(id, module)
                .orElseThrow(() -> notFound(id));
        if (!admin && !canView(record, userEmail)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "You do not have access to this workspace record");
        }
        return toResponse(record);
    }

    public WorkspaceResponse create(WorkspaceModule module, WorkspaceRequest request) {
        validateRequest(module, request);
        Employee employee = findEmployee(request.employeeId());
        WorkspaceRecord record = new WorkspaceRecord(module, request.title().trim(),
                normalizeDescription(request.description()), request.status().trim().toUpperCase(),
                employee, request.dueDate(), request.score(), request.visibleToEmployees());
        return toResponse(records.save(record));
    }

    public WorkspaceResponse createDocument(WorkspaceRequest request, MultipartFile file) {
        validateRequest(WorkspaceModule.DOCUMENTS, request);
        validateFile(file);
        Employee employee = findEmployee(request.employeeId());
        WorkspaceRecord record = new WorkspaceRecord(WorkspaceModule.DOCUMENTS, request.title().trim(),
                normalizeDescription(request.description()), request.status().trim().toUpperCase(),
                employee, request.dueDate(), request.score(), request.visibleToEmployees());
        try {
            record.attachFile(safeFileName(file.getOriginalFilename()), file.getContentType(), file.getBytes());
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to read uploaded document");
        }
        return toResponse(records.save(record));
    }

    public WorkspaceResponse update(WorkspaceModule module, Long id, WorkspaceRequest request) {
        validateRequest(module, request);
        WorkspaceRecord record = records.findByIdAndModule(id, module).orElseThrow(() -> notFound(id));
        record.update(request.title().trim(), normalizeDescription(request.description()),
                request.status().trim().toUpperCase(), findEmployee(request.employeeId()),
                request.dueDate(), request.score(), request.visibleToEmployees());
        return toResponse(record);
    }

    public WorkspaceResponse updateStatus(WorkspaceModule module, Long id, String status,
                                          String userEmail, boolean admin) {
        WorkspaceRecord record = records.findByIdAndModule(id, module).orElseThrow(() -> notFound(id));
        String requestedStatus = status.trim().toUpperCase();
        if (!allowedStatuses(module).contains(requestedStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Status is not valid for the " + module.name().toLowerCase() + " module");
        }
        if (!admin) {
            if (!Set.of(WorkspaceModule.ONBOARDING, WorkspaceModule.TASKS,
                    WorkspaceModule.TRAINING, WorkspaceModule.PERFORMANCE)
                    .contains(module)) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "You cannot update this workspace record");
            }
            Employee owner = employees.findByEmailIgnoreCase(userEmail).orElseThrow(() ->
                    new org.springframework.security.access.AccessDeniedException(
                            "No employee profile is linked to this account"));
            if (record.getEmployee() == null || !record.getEmployee().getId().equals(owner.getId())) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "You can only update your own assigned records");
            }
            Set<String> employeeStatuses = switch (module) {
                case TRAINING -> Set.of("ENROLLED", "COMPLETED");
                case PERFORMANCE -> Set.of("ACKNOWLEDGED");
                default -> Set.of("IN_PROGRESS", "COMPLETED");
            };
            if (!employeeStatuses.contains(requestedStatus)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status transition");
            }
        }
        record.updateStatus(requestedStatus);
        return toResponse(record);
    }

    public WorkspaceResponse enrollTraining(Long id, String userEmail) {
        WorkspaceRecord course = records.findByIdAndModule(id, WorkspaceModule.TRAINING)
                .orElseThrow(() -> notFound(id));
        if (!course.isVisibleToEmployees()
                || !Set.of("OPEN", "PUBLISHED").contains(course.getStatus().toUpperCase())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This training course is not open for enrollment");
        }
        Employee employee = employees.findByEmailIgnoreCase(userEmail).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.CONFLICT,
                        "No employee profile is linked to this account. Ask an administrator to add your employee profile."));
        if (records.existsByModuleAndEmployee_IdAndTitleIgnoreCase(
                WorkspaceModule.TRAINING, employee.getId(), course.getTitle())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You are already enrolled in this course");
        }
        WorkspaceRecord enrollment = new WorkspaceRecord(WorkspaceModule.TRAINING, course.getTitle(),
                course.getDescription(), "ENROLLED", employee, course.getDueDate(), null, false);
        return toResponse(records.save(enrollment));
    }

    public void delete(WorkspaceModule module, Long id) {
        WorkspaceRecord record = records.findByIdAndModule(id, module).orElseThrow(() -> notFound(id));
        records.delete(record);
    }

    @Transactional(readOnly = true)
    public WorkspaceRecord getDocument(Long id, String userEmail, boolean admin) {
        WorkspaceRecord record = records.findByIdAndModule(id, WorkspaceModule.DOCUMENTS)
                .orElseThrow(() -> notFound(id));
        if (record.getFileContent() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document file was not found");
        }
        if (!admin && !canView(record, userEmail)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "You do not have access to this document");
        }
        return record;
    }

    private void validateRequest(WorkspaceModule module, WorkspaceRequest request) {
        String status = request.status().trim().toUpperCase();
        if (!allowedStatuses(module).contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Status is not valid for the " + module.name().toLowerCase() + " module");
        }
        if (module == WorkspaceModule.PERFORMANCE
                && (request.score() == null || request.employeeId() == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Performance reviews require an employee and a score from 0 to 100");
        }
        if (module == WorkspaceModule.ONBOARDING && request.employeeId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Onboarding items require an employee");
        }
        if (module == WorkspaceModule.TASKS && request.employeeId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tasks require an assigned employee");
        }
        if (module == WorkspaceModule.SETTINGS && request.visibleToEmployees()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Settings cannot be visible to employees");
        }
    }

    private Set<String> allowedStatuses(WorkspaceModule module) {
        return switch (module) {
            case ONBOARDING, TASKS -> Set.of("TO_DO", "IN_PROGRESS", "COMPLETED");
            case PERFORMANCE -> Set.of("DRAFT", "PUBLISHED", "ACKNOWLEDGED");
            case TRAINING -> Set.of("OPEN", "PUBLISHED", "ENROLLED", "IN_PROGRESS", "COMPLETED", "CLOSED");
            case DOCUMENTS -> Set.of("DRAFT", "PUBLISHED", "ARCHIVED");
            case ANNOUNCEMENTS -> Set.of("DRAFT", "PUBLISHED", "ARCHIVED");
            case SETTINGS -> Set.of("ACTIVE", "INACTIVE");
        };
    }

    private void validateModuleAccess(WorkspaceModule module, boolean admin) {
        if (!admin && module == WorkspaceModule.SETTINGS) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Settings are available to administrators only");
        }
    }

    private boolean canView(WorkspaceRecord record, String userEmail) {
        if (record.isVisibleToEmployees()
                && Set.of("PUBLISHED", "OPEN").contains(record.getStatus().toUpperCase())) {
            return true;
        }
        return record.getEmployee() != null && record.getEmployee().getEmail().equalsIgnoreCase(userEmail);
    }

    private Employee findEmployee(Long employeeId) {
        if (employeeId == null) return null;
        return employees.findById(employeeId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found with ID: " + employeeId));
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a document file to upload");
        }
        if (file.getSize() > MAX_DOCUMENT_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Document files are limited to 10 MB");
        }
        if (!DOCUMENT_TYPES.contains(file.getContentType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Supported document types are PDF, PNG, JPEG, TXT, CSV, DOCX, and XLSX");
        }
    }

    private String safeFileName(String originalName) {
        if (originalName == null || originalName.isBlank()) return "document";
        String name = originalName.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\r\\n\\p{Cntrl}]", "_").trim();
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizeDescription(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private WorkspaceResponse toResponse(WorkspaceRecord record) {
        Employee employee = record.getEmployee();
        String employeeName = employee == null ? null
                : employee.getFirstName() + " " + employee.getLastName();
        return new WorkspaceResponse(record.getId(), record.getModule(), record.getTitle(),
                record.getDescription(), record.getStatus(), employee == null ? null : employee.getId(),
                employeeName, record.getDueDate(), record.getScore(), record.isVisibleToEmployees(),
                record.getFileName(), record.getContentType(), record.getFileSize(),
                record.getCreatedAt(), record.getUpdatedAt());
    }

    private ResponseStatusException notFound(Long id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace record not found with ID: " + id);
    }
}
