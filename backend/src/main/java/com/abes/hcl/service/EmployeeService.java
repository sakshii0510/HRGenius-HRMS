
package com.abes.hcl.service;

import com.abes.hcl.entity.Employee;
import com.abes.hcl.repository.EmployeeRepository;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@Service
public class EmployeeService {

    private final EmployeeRepository employeeRepository;

    public EmployeeService(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    public Employee createEmployee(Employee employee) {
        return employeeRepository.save(employee);
    }

    public List<Employee> getAllEmployees() {
        return employeeRepository.findAll();
    }

    public Optional<Employee> getEmployeeByEmail(String email) {
        return employeeRepository.findByEmailIgnoreCase(email);
    }

    public Employee getEmployeeById(Long id) {
        return employeeRepository.findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found with ID: " + id));
    }

    public List<Employee> search(String search, String department, String designation) {
        return employeeRepository.search(normalize(search), normalize(department), normalize(designation));
    }

    public List<Employee> searchOwn(String email, String search, String department, String designation) {
        return employeeRepository.searchOwn(email, normalize(search), normalize(department), normalize(designation));
    }

    public Employee updateEmployee(Long id, Employee updatedEmployee) {
        Employee employee = getEmployeeById(id);

        employee.setFirstName(updatedEmployee.getFirstName());
        employee.setLastName(updatedEmployee.getLastName());
        employee.setEmail(updatedEmployee.getEmail());
        employee.setDepartment(updatedEmployee.getDepartment());
        employee.setDesignation(updatedEmployee.getDesignation());
        employee.setSalary(updatedEmployee.getSalary());
        if (updatedEmployee.getStatus() != null) {
            employee.setStatus(updatedEmployee.getStatus());
        }

        return employeeRepository.save(employee);
    }

    public void deleteEmployee(Long id) {
        Employee employee = getEmployeeById(id);
        employeeRepository.delete(employee);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
