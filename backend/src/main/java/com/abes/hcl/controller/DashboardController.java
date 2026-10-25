package com.abes.hcl.controller;

import com.abes.hcl.dto.DashboardSummary;
import com.abes.hcl.service.DashboardService;
import com.abes.hcl.service.EmployeeAccessService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;
    private final EmployeeAccessService employeeAccess;

    public DashboardController(DashboardService dashboardService, EmployeeAccessService employeeAccess) {
        this.dashboardService = dashboardService;
        this.employeeAccess = employeeAccess;
    }

    @GetMapping("/summary")
    public DashboardSummary summary(Authentication authentication) {
        return dashboardService.getSummary(authentication.getName(), employeeAccess.isAdmin(authentication));
    }
}
