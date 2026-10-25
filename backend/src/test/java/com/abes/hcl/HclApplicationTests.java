package com.abes.hcl;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.mock.web.MockMultipartFile;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:hrgenius-test;DB_CLOSE_DELAY=-1",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class HclApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@DynamicPropertySource
	static void jwtSecret(DynamicPropertyRegistry registry) {
		byte[] randomSecret = new byte[32];
		new SecureRandom().nextBytes(randomSecret);
		String secret = Base64.getEncoder().encodeToString(randomSecret);
		registry.add("security.jwt.secret", () -> secret);
	}

	@Test
	void contextLoads() {
	}

	@Test
	void signupLoginAndRoleProtectionWork() throws Exception {
		String email = UUID.randomUUID() + "@example.com";
		String password = "test-password-12";
		String signupJson = """
				{"name":"Test Employee","email":"%s","password":"%s"}
				""".formatted(email, password);

		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.get("/api/employees"))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.get("/api/employees")
						.header("Authorization", "Bearer invalid-token"))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());

		MvcResult signup = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.post("/api/auth/signup")
						.contentType("application/json")
						.content(signupJson))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
						.jsonPath("$.user.role").value("EMPLOYEE"))
				.andReturn();
		String employeeToken = extractToken(signup);
		String storedPassword = jdbcTemplate.queryForObject(
				"SELECT password FROM app_users WHERE email = ?", String.class, email);
		org.junit.jupiter.api.Assertions.assertNotEquals(password, storedPassword);
		org.junit.jupiter.api.Assertions.assertTrue(storedPassword.startsWith("$2"));

		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.get("/api/employees")
						.header("Authorization", "Bearer " + employeeToken))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$").isEmpty());

		String employeeJson = """
				{"firstName":"Blocked","lastName":"Write","email":"blocked@example.com","department":"HR","designation":"Employee","salary":0}
				""";
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.post("/api/employees")
						.header("Authorization", "Bearer " + employeeToken)
						.contentType("application/json")
						.content(employeeJson))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());

		String loginJson = """
				{"email":"%s","password":"%s"}
				""".formatted(email, password);
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.post("/api/auth/login")
						.contentType("application/json")
						.content(loginJson))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
		jdbcTemplate.update("UPDATE app_users SET role = 'ADMIN' WHERE email = ?", email);
		MvcResult adminLogin = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.post("/api/auth/login")
						.contentType("application/json")
						.content(loginJson))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
						.jsonPath("$.user.role").value("ADMIN"))
				.andReturn();
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.post("/api/employees")
						.header("Authorization", "Bearer " + extractToken(adminLogin))
						.contentType("application/json")
						.content(employeeJson.replace("blocked@example.com", UUID.randomUUID() + "@example.com")))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated());

		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.post("/api/auth/signup")
						.contentType("application/json")
						.content(signupJson))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict());
	}

	@Test
	void hrModulesPersistValidateAndEnforceEmployeeAccess() throws Exception {
		String suffix = UUID.randomUUID().toString();
		String adminEmail = "admin-" + suffix + "@example.com";
		String employeeEmail = "employee-" + suffix + "@example.com";
		String password = "secure-test-password";
		String adminToken = signupAndPromoteToAdmin(adminEmail, password, "HR Administrator");
		String employeeToken = signupAndGetToken(employeeEmail, password, "Test Employee");

		String employeeJson = """
				{"firstName":"Test","lastName":"Employee","email":"%s","department":"Engineering","designation":"Developer","salary":90000}
				""".formatted(employeeEmail);
		MvcResult employeeResult = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.post("/api/employees")
						.header("Authorization", "Bearer " + adminToken)
						.contentType("application/json")
						.content(employeeJson))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
						.jsonPath("$.status").value("ACTIVE"))
				.andReturn();
		long employeeId = Long.parseLong(extractJsonNumber(employeeResult, "id"));
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.put("/api/employees/" + employeeId)
						.header("Authorization", "Bearer " + adminToken)
						.contentType("application/json")
						.content(employeeJson.replace("\"salary\":90000", "\"salary\":90000,\"status\":\"INACTIVE\"")))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
						.jsonPath("$.status").value("INACTIVE"));

		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.get("/api/employees")
						.header("Authorization", "Bearer " + employeeToken))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
						.jsonPath("$[0].email").value(employeeEmail));
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.get("/api/employees")
						.header("Authorization", "Bearer " + adminToken)
						.param("department", "Engineering")
						.param("search", "Test Employee"))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
						.jsonPath("$.length()").value(1));

		String invalidAttendance = """
				{"employeeId":%d,"attendanceDate":"2026-10-24","checkIn":"17:00:00","checkOut":"09:00:00","status":"PRESENT"}
				""".formatted(employeeId);
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.post("/api/attendance")
						.header("Authorization", "Bearer " + adminToken)
						.contentType("application/json")
						.content(invalidAttendance))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());

		String attendanceJson = """
				{"employeeId":%d,"attendanceDate":"2026-10-24","checkIn":"09:00:00","checkOut":"17:00:00","status":"PRESENT"}
				""".formatted(employeeId);
		MvcResult attendanceResult = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.post("/api/attendance")
						.header("Authorization", "Bearer " + employeeToken)
						.contentType("application/json")
						.content(attendanceJson))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
						.jsonPath("$.employeeId").value(employeeId))
				.andReturn();
		String attendanceId = extractJsonNumber(attendanceResult, "id");
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.get("/api/attendance")
						.header("Authorization", "Bearer " + employeeToken)
						.param("status", "PRESENT")
						.param("fromDate", "2026-10-01")
						.param("toDate", "2026-10-31"))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
						.jsonPath("$.length()").value(1));
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.put("/api/attendance/" + attendanceId)
						.header("Authorization", "Bearer " + adminToken)
						.contentType("application/json")
						.content(attendanceJson.replace("PRESENT", "LATE")))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
						.jsonPath("$.status").value("LATE"));

		String leaveJson = """
				{"employeeId":%d,"leaveType":"ANNUAL","startDate":"2026-11-04","endDate":"2026-11-06","reason":"Family event"}
				""".formatted(employeeId);
		MvcResult leaveResult = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.post("/api/leave-applications")
						.header("Authorization", "Bearer " + employeeToken)
						.contentType("application/json")
						.content(leaveJson))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
						.jsonPath("$.status").value("PENDING"))
				.andReturn();
		String leaveId = extractJsonNumber(leaveResult, "id");
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.put("/api/leave-applications/" + leaveId)
						.header("Authorization", "Bearer " + employeeToken)
						.contentType("application/json")
						.content(leaveJson.replace("2026-11-04", "2026-11-05")))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.post("/api/leave-applications/" + leaveId + "/review")
						.header("Authorization", "Bearer " + adminToken)
						.contentType("application/json")
						.content("""
								{"status":"APPROVED","reviewComment":"Approved"}
								"""))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
						.jsonPath("$.status").value("APPROVED"));
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.post("/api/leave-applications/" + leaveId + "/review")
						.header("Authorization", "Bearer " + adminToken)
						.contentType("application/json")
						.content("""
								{"status":"REJECTED"}
								"""))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
						.status().isConflict());

		String payrollJson = """
				{"employeeId":%d,"salaryPeriod":"2026-10","basicSalary":1000.00,"allowances":200.00,"deductions":50.00}
				""".formatted(employeeId);
		MvcResult payrollResult = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.post("/api/payroll")
						.header("Authorization", "Bearer " + adminToken)
						.contentType("application/json")
						.content(payrollJson))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
						.jsonPath("$.netSalary").value(1150.0))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
						.jsonPath("$.paymentStatus").value("PENDING"))
				.andReturn();
		String payrollId = extractJsonNumber(payrollResult, "id");
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.put("/api/payroll/" + payrollId)
						.header("Authorization", "Bearer " + adminToken)
						.contentType("application/json")
						.content(payrollJson.replace("200.00", "300.00")
								.replace("\"deductions\":50.00", "\"deductions\":50.00,\"paymentStatus\":\"PAID\"")))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
						.jsonPath("$.netSalary").value(1250.0))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
						.jsonPath("$.paymentStatus").value("PAID"));
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.get("/api/payroll")
						.header("Authorization", "Bearer " + employeeToken)
						.param("fromPeriod", "2026-10")
						.param("toPeriod", "2026-10"))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
						.jsonPath("$.length()").value(1));
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.post("/api/payroll")
						.header("Authorization", "Bearer " + employeeToken)
						.contentType("application/json")
						.content(payrollJson))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());

		String jobJson = """
				{"title":"Software Engineer","department":"Engineering","location":"Remote","employmentType":"FULL_TIME","description":"Build useful software.","salaryMin":80000,"salaryMax":120000,"status":"OPEN"}
				""";
		MvcResult jobResult = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.post("/api/recruitment/jobs")
						.header("Authorization", "Bearer " + adminToken)
						.contentType("application/json")
						.content(jobJson))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated())
				.andReturn();
		String jobId = extractJsonNumber(jobResult, "id");
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.put("/api/recruitment/jobs/" + jobId)
						.header("Authorization", "Bearer " + adminToken)
						.contentType("application/json")
						.content(jobJson.replace("Software Engineer", "Senior Software Engineer")))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
						.jsonPath("$.title").value("Senior Software Engineer"));
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.get("/api/recruitment/jobs")
						.header("Authorization", "Bearer " + employeeToken)
						.param("search", "engineer"))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
						.jsonPath("$.length()").value(1));
		MvcResult applicationResult = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.post("/api/recruitment/jobs/" + jobId + "/applications")
						.header("Authorization", "Bearer " + employeeToken)
						.contentType("application/json")
						.content("""
								{"phone":"+1 555 123 4567","coverLetter":"I am interested in this role."}
								"""))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
						.jsonPath("$.status").value("APPLIED"))
				.andReturn();
		String applicationId = extractJsonNumber(applicationResult, "id");
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.put("/api/recruitment/applications/" + applicationId + "/status")
						.header("Authorization", "Bearer " + adminToken)
						.contentType("application/json")
						.content("""
								{"status":"SCREENING"}
								"""))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
						.jsonPath("$.status").value("SCREENING"));
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.get("/api/recruitment/applications")
						.header("Authorization", "Bearer " + employeeToken))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
						.jsonPath("$.length()").value(1));
	}

	@Test
	void workspaceModulesAreDatabaseBackedAndEnforceVisibility() throws Exception {
		String suffix = UUID.randomUUID().toString();
		String adminEmail = "workspace-admin-" + suffix + "@example.com";
		String employeeEmail = "workspace-employee-" + suffix + "@example.com";
		String password = "secure-test-password";
		String adminToken = signupAndPromoteToAdmin(adminEmail, password, "Workspace Administrator");
		String employeeToken = signupAndGetToken(employeeEmail, password, "Workspace Employee");
		String employeeJson = """
				{"firstName":"Workspace","lastName":"Employee","email":"%s","department":"People","designation":"Associate","salary":50000}
				""".formatted(employeeEmail);
		MvcResult employeeResult = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.post("/api/employees")
						.header("Authorization", "Bearer " + adminToken)
						.contentType("application/json")
						.content(employeeJson))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated())
				.andReturn();
		String employeeId = extractJsonNumber(employeeResult, "id");

		String onboardingJson = """
				{"title":"Complete HR orientation","description":"Review the employee handbook.","status":"TO_DO","employeeId":%s,"dueDate":"2026-11-15","visibleToEmployees":false}
				""".formatted(employeeId);
		MvcResult onboardingResult = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.post("/api/workspace/ONBOARDING")
						.header("Authorization", "Bearer " + adminToken)
						.contentType("application/json")
						.content(onboardingJson))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated())
				.andReturn();
		String onboardingId = extractJsonNumber(onboardingResult, "id");
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.get("/api/workspace/ONBOARDING")
						.header("Authorization", "Bearer " + employeeToken))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
						.jsonPath("$[0].title").value("Complete HR orientation"));
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.put("/api/workspace/ONBOARDING/" + onboardingId + "/status")
						.header("Authorization", "Bearer " + employeeToken)
						.contentType("application/json")
						.content("""
								{"status":"COMPLETED"}
								"""))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
						.jsonPath("$.status").value("COMPLETED"));

		String announcementJson = """
				{"title":"Office holiday schedule","description":"The office closes Friday.","status":"PUBLISHED","visibleToEmployees":true}
				""";
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.post("/api/workspace/ANNOUNCEMENTS")
						.header("Authorization", "Bearer " + adminToken)
						.contentType("application/json")
						.content(announcementJson))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated());
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.get("/api/workspace/ANNOUNCEMENTS")
						.header("Authorization", "Bearer " + employeeToken))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
						.jsonPath("$[0].title").value("Office holiday schedule"));
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.get("/api/workspace/SETTINGS")
						.header("Authorization", "Bearer " + employeeToken))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());

		String trainingJson = """
				{"title":"Workplace safety","description":"Annual safety refresher.","status":"OPEN","visibleToEmployees":true}
				""";
		MvcResult trainingResult = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.post("/api/workspace/TRAINING")
						.header("Authorization", "Bearer " + adminToken)
						.contentType("application/json")
						.content(trainingJson))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated())
				.andReturn();
		String trainingId = extractJsonNumber(trainingResult, "id");
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.post("/api/workspace/TRAINING/" + trainingId + "/enroll")
						.header("Authorization", "Bearer " + employeeToken))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
						.jsonPath("$.status").value("ENROLLED"));

		MockMultipartFile documentMetadata = new MockMultipartFile(
				"metadata", "metadata.json", "application/json",
				("""
						{"title":"Employee handbook","description":"Current handbook","status":"PUBLISHED","employeeId":%s,"visibleToEmployees":false}
						""").formatted(employeeId).getBytes(java.nio.charset.StandardCharsets.UTF_8));
		MockMultipartFile documentFile = new MockMultipartFile(
				"file", "handbook.pdf", "application/pdf", new byte[] {37, 80, 68, 70, 45});
		MvcResult documentResult = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.multipart("/api/workspace/DOCUMENTS/upload")
						.file(documentMetadata)
						.file(documentFile)
						.header("Authorization", "Bearer " + adminToken))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated())
				.andReturn();
		String documentId = extractJsonNumber(documentResult, "id");
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.get("/api/workspace/DOCUMENTS/" + documentId + "/file")
						.header("Authorization", "Bearer " + employeeToken))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
						.header().string("Content-Disposition", org.hamcrest.Matchers.containsString("handbook.pdf")));

		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.get("/api/dashboard/summary")
						.header("Authorization", "Bearer " + adminToken))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
						.jsonPath("$.employeeCount").value(1))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
						.jsonPath("$.payrollHistory.length()").value(8));
	}

	private String signupAndGetToken(String email, String password, String name) throws Exception {
		MvcResult result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.post("/api/auth/signup")
						.contentType("application/json")
						.content("""
								{"name":"%s","email":"%s","password":"%s"}
								""".formatted(name, email, password)))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated())
				.andReturn();
		return extractToken(result);
	}

	private String signupAndPromoteToAdmin(String email, String password, String name) throws Exception {
		signupAndGetToken(email, password, name);
		jdbcTemplate.update("UPDATE app_users SET role = 'ADMIN' WHERE email = ?", email);
		MvcResult result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.post("/api/auth/login")
						.contentType("application/json")
						.content("""
								{"email":"%s","password":"%s"}
								""".formatted(email, password)))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
				.andReturn();
		return extractToken(result);
	}

	private String extractToken(MvcResult result) throws Exception {
		Matcher matcher = Pattern.compile("\"accessToken\":\"([^\"]+)\"")
				.matcher(result.getResponse().getContentAsString());
		if (!matcher.find()) {
			throw new AssertionError("Authentication response did not include an access token");
		}
		return matcher.group(1);
	}

	private String extractJsonNumber(MvcResult result, String field) throws Exception {
		Matcher matcher = Pattern.compile("\"" + Pattern.quote(field) + "\":(\\d+)")
				.matcher(result.getResponse().getContentAsString());
		if (!matcher.find()) {
			throw new AssertionError("Response did not include " + field);
		}
		return matcher.group(1);
	}
}
