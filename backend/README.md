# HRGenius backend

Java 21 / Spring Boot backend using the existing `hrgenius_db` MySQL connection. Hibernate remains configured with `ddl-auto=update`; the five HR modules add new tables and do not drop the existing `employees` or `app_users` tables. Relationships to employee records use restrictive foreign keys; a referenced employee, job opening, or candidate cannot be deleted while dependent records exist.

## Start locally (PowerShell)

Set a private random JWT key before starting Spring Boot. The database URL, username, password, and port remain configured in `src/main/resources/application.properties`.

```powershell
Set-Location "C:\Users\HP\Downloads\HRGenius - HRMS\backend"
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$secretBytes = New-Object byte[] 32
$rng.GetBytes($secretBytes)
$env:JWT_SECRET_BASE64 = [Convert]::ToBase64String($secretBytes)
$rng.Dispose()
.\mvnw.cmd spring-boot:run
```

New employee accounts use the `EMPLOYEE` role. An administrator can promote a signed-up account using an authorized MySQL connection:

```sql
UPDATE app_users SET role = 'ADMIN' WHERE email = 'admin@example.com';
```

Sign out and sign in again after changing the role. Keep the signing key stable and private between restarts; use a secret manager for deployed environments.

## Authorization

All endpoints require a valid bearer JWT except `POST /api/auth/signup` and `POST /api/auth/login`. CORS allows the existing Angular origin `http://localhost:4200`.

- `EMPLOYEE` accounts can access only their own employee-linked attendance, leave, and payroll records. They can browse open jobs, apply using their signed-in account, view their own applications, and withdraw an application still in `APPLIED`.
- `ADMIN` accounts can manage employee, attendance, leave review, payroll, and recruitment records, including candidates and application statuses.
- Public sign-up cannot request or assign `ADMIN`.

## API endpoints

All request and response bodies are JSON. Enum filters use the enum names shown in the database (for example `PRESENT`, `PENDING`, `OPEN`). Date filters use `YYYY-MM-DD`; payroll period filters and request fields use `YYYY-MM`.

### Employees

| Method | Path | Access | Notes |
| --- | --- | --- | --- |
| GET | `/api/employees?search=&department=&designation=` | ADMIN, EMPLOYEE (own record only) | Search and exact department/designation filters |
| GET | `/api/employees/{id}` | ADMIN, owning EMPLOYEE | Get employee |
| POST | `/api/employees` | ADMIN | Create |
| PUT | `/api/employees/{id}` | ADMIN | Update |
| DELETE | `/api/employees/{id}` | ADMIN | Delete if no dependent HR records exist |

### Attendance

| Method | Path | Access | Notes |
| --- | --- | --- | --- |
| GET | `/api/attendance?employeeId=&fromDate=&toDate=&status=` | ADMIN; EMPLOYEE (own records) | Filter |
| GET | `/api/attendance/{id}` | ADMIN, owning EMPLOYEE | Get |
| POST | `/api/attendance` | ADMIN, EMPLOYEE (own employee ID) | Create; one record per employee per date |
| PUT | `/api/attendance/{id}` | ADMIN | Update |
| DELETE | `/api/attendance/{id}` | ADMIN | Delete |

Attendance request fields: `employeeId`, `attendanceDate`, `checkIn`, `checkOut`, `status`. Times use ISO local time such as `09:00:00`; absent/on-leave records cannot include check-in/out. Check-out must follow check-in.

### Leave management

| Method | Path | Access | Notes |
| --- | --- | --- | --- |
| GET | `/api/leave-applications?employeeId=&status=&fromDate=&toDate=` | ADMIN; EMPLOYEE (own applications) | Filter by overlapping leave dates |
| GET | `/api/leave-applications/{id}` | ADMIN, owning EMPLOYEE | Get |
| POST | `/api/leave-applications` | ADMIN, EMPLOYEE (own employee ID) | Apply; status starts `PENDING` |
| PUT | `/api/leave-applications/{id}` | owning EMPLOYEE | Update a pending application |
| DELETE | `/api/leave-applications/{id}` | owning EMPLOYEE | Delete a pending application |
| POST | `/api/leave-applications/{id}/review` | ADMIN | Approve/reject pending application with `status` and optional `reviewComment` |

Leave request fields: `employeeId`, `leaveType`, `startDate`, `endDate`, `reason`. Types: `ANNUAL`, `SICK`, `PERSONAL`, `MATERNITY`, `PATERNITY`, `UNPAID`, `OTHER`.

### Payroll

| Method | Path | Access | Notes |
| --- | --- | --- | --- |
| GET | `/api/payroll?employeeId=&fromPeriod=&toPeriod=` | ADMIN; EMPLOYEE (own records) | Filter by salary period |
| GET | `/api/payroll/{id}` | ADMIN, owning EMPLOYEE | Get |
| POST | `/api/payroll` | ADMIN | Create |
| PUT | `/api/payroll/{id}` | ADMIN | Update |
| DELETE | `/api/payroll/{id}` | ADMIN | Delete |

Payroll fields: `employeeId`, `salaryPeriod` (`YYYY-MM`), `basicSalary`, `allowances`, `deductions`. The backend calculates `netSalary = basicSalary + allowances - deductions`; deductions cannot exceed gross pay.

### Recruitment

| Method | Path | Access | Notes |
| --- | --- | --- | --- |
| GET | `/api/recruitment/jobs?status=&search=` | ADMIN; EMPLOYEE (open jobs only) | Search title, department, location |
| GET | `/api/recruitment/jobs/{id}` | ADMIN; EMPLOYEE (open job only) | Get |
| POST | `/api/recruitment/jobs` | ADMIN | Create opening |
| PUT | `/api/recruitment/jobs/{id}` | ADMIN | Update opening |
| DELETE | `/api/recruitment/jobs/{id}` | ADMIN | Delete if there are no applications |
| GET | `/api/recruitment/candidates?search=` | ADMIN | Search candidate name/email |
| GET | `/api/recruitment/candidates/{id}` | ADMIN | Get candidate |
| POST | `/api/recruitment/candidates` | ADMIN | Create candidate |
| PUT | `/api/recruitment/candidates/{id}` | ADMIN | Update candidate |
| DELETE | `/api/recruitment/candidates/{id}` | ADMIN | Delete if there are no applications |
| POST | `/api/recruitment/jobs/{jobId}/applications` | EMPLOYEE | Apply to an open job; candidate identity is taken from the authenticated account |
| GET | `/api/recruitment/applications?jobId=&status=&search=` | ADMIN; EMPLOYEE (own applications) | Search/filter |
| GET | `/api/recruitment/applications/{id}` | ADMIN, owning EMPLOYEE | Get |
| PUT | `/api/recruitment/applications/{id}/status` | ADMIN | Advance/reject application status |
| DELETE | `/api/recruitment/applications/{id}` | owning EMPLOYEE | Withdraw while status is `APPLIED` |

Application status transitions are enforced: `APPLIED` → `SCREENING` or `REJECTED`; `SCREENING` → `INTERVIEW`, `OFFERED`, or `REJECTED`; `INTERVIEW` → `OFFERED` or `REJECTED`. Terminal statuses cannot be changed.

### Dashboard and remaining workspace modules

All workspace APIs below require JWT authentication. Creating, editing, and deleting workspace records is restricted to `ADMIN`; employees can view published shared records and their own assignments. Employee status updates are limited to their own onboarding items, assigned tasks, training enrollment, and published performance reviews.

| Method | Path | Access | Notes |
| --- | --- | --- | --- |
| GET | `/api/dashboard/summary` | ADMIN, EMPLOYEE | Live metrics and eight monthly payroll totals, derived from persisted records |
| GET | `/api/workspace/{module}?employeeId=&status=&search=` | ADMIN; EMPLOYEE (published/shared or own records) | Modules: `ONBOARDING`, `PERFORMANCE`, `TRAINING`, `DOCUMENTS`, `ANNOUNCEMENTS`, `TASKS`, `SETTINGS` |
| GET | `/api/workspace/{module}/{id}` | ADMIN, owning EMPLOYEE or employee-visible published record | View a record |
| POST | `/api/workspace/{module}` | ADMIN | Create a record |
| PUT | `/api/workspace/{module}/{id}` | ADMIN | Update a record |
| PUT | `/api/workspace/{module}/{id}/status` | ADMIN; owning EMPLOYEE for allowed workflows | Update checklist/task/training/review status |
| DELETE | `/api/workspace/{module}/{id}` | ADMIN | Delete a record |
| POST | `/api/workspace/TRAINING/{id}/enroll` | EMPLOYEE | Enroll in an open, published course |
| POST | `/api/workspace/DOCUMENTS/upload` | ADMIN | Multipart fields: `metadata` (JSON `WorkspaceRequest`) and `file`; maximum 10 MB |
| GET | `/api/workspace/DOCUMENTS/{id}/file` | ADMIN, owning EMPLOYEE or employee-visible published document | Download stored file as an attachment |

Workspace request fields are `title`, `description`, `status`, optional `employeeId`, `dueDate`, optional `score` (0–100), and `visibleToEmployees`. Status vocabulary is validated per module. Performance records require an assigned employee and score; onboarding items and tasks require an assigned employee. Document upload accepts PDF, PNG, JPEG, TXT, CSV, DOCX, and XLSX; file bytes are stored with document metadata in MySQL. The workspace records use the `workspace_records` table. Dashboard values are aggregated from persisted HR records; no demo records are seeded.

## Build and tests

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
```

Tests use isolated in-memory H2 with a random test-only JWT key and do not connect to the configured MySQL database.
