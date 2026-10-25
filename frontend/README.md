# HRGenius

Responsive Angular 21 HR dashboard with JWT-backed authentication and employee APIs.

## Run locally (PowerShell)

Start the Spring Boot backend first. In a backend PowerShell window, generate a private 256-bit JWT signing key for this run and start Spring Boot:

```powershell
Set-Location "C:\Users\HP\Downloads\HRGenius - HRMS\backend"
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$secretBytes = New-Object byte[] 32
$rng.GetBytes($secretBytes)
$env:JWT_SECRET_BASE64 = [Convert]::ToBase64String($secretBytes)
$rng.Dispose()
$env:JWT_EXPIRATION_MS = "3600000"
.\mvnw.cmd spring-boot:run
```

In a second PowerShell window, run Angular on the CORS-approved origin:

```powershell
Set-Location "C:\Users\HP\Downloads\HRGenius - HRMS\frontend"
npm install
npm start
```

Open `http://localhost:4200`. The backend uses the existing MySQL settings in `backend/src/main/resources/application.properties`. On startup, Hibernate creates or updates the separate `app_users` authentication table; it does not delete employee records. Existing employee APIs remain at `http://localhost:8080/api/employees`.

## Authentication and access

- Sign up at `/signup` or sign in at `/login`. Passwords must be 12–64 characters and are BCrypt-hashed by Spring Security.
- JWTs expire after one hour by default and are stored for the current browser session. The Angular interceptor sends bearer tokens only to the local backend API.
- Logout clears the browser session; because access tokens are stateless, a copied token remains valid until its expiry. Keep token lifetime short, use HTTPS in production, and apply strong XSS protections.
- New accounts receive the `EMPLOYEE` role. Employees can only read their own matching employee record; `ADMIN` users can list and manage all employee records.
- To promote an account, first sign up, then use a privileged MySQL administrator account to run `UPDATE app_users SET role = 'ADMIN' WHERE email = 'your-account@example.com';`. Sign out and sign in again to receive a token with the updated role. Never expose an admin-promotion endpoint to public users.
- Forgot-password email delivery is intentionally disabled until an email provider and secure, expiring reset-token flow are configured.

Keep the JWT secret private. Generate and configure a stable secret using a secret manager for deployments; a new random secret on each local start invalidates previously issued tokens. Deploy behind HTTPS and do not commit signing keys or database credentials.

## Demo-only areas

Payroll and attendance summary values, finance chart, tasks, announcements, and Performance, Recruitment, Onboarding, Training, Leave, Documents, Analytics, and Settings are illustrative UI only. They do not read or write backend data.

## Build and test

```powershell
Set-Location "C:\Users\HP\Downloads\HRGenius - HRMS\frontend"
npm run build
npm test

Set-Location "C:\Users\HP\Downloads\HRGenius - HRMS\backend"
.\mvnw.cmd test
```

Backend tests use an isolated in-memory H2 database and a random test-only JWT key; they do not access the configured MySQL database.
