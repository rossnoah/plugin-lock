package dev.noah.pluginlock.core.doctor;

public record DoctorCheck(DoctorStatus status, String check, String message) {
    public static DoctorCheck ok(String check, String message) {
        return new DoctorCheck(DoctorStatus.OK, check, message);
    }

    public static DoctorCheck warning(String check, String message) {
        return new DoctorCheck(DoctorStatus.WARNING, check, message);
    }

    public static DoctorCheck error(String check, String message) {
        return new DoctorCheck(DoctorStatus.ERROR, check, message);
    }
}
