package dev.noah.pluginlock.core.doctor;

import java.util.List;

public record DoctorReport(List<DoctorCheck> checks) {
    public DoctorReport {
        checks = List.copyOf(checks);
    }

    public boolean hasErrors() {
        return checks.stream().anyMatch(check -> check.status() == DoctorStatus.ERROR);
    }
}
