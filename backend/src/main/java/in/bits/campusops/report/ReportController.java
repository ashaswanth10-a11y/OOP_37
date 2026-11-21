package in.bits.campusops.report;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('TECHNICIAN','SYSTEM_ADMIN')")
    public ReportService.Dashboard dashboard() {
        return reports.dashboard();
    }
}
