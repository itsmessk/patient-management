package com.pm.appointmentservice.service;


import com.pm.appointmentservice.dto.AppointmentResponseDTO;
import com.pm.appointmentservice.entity.CachedPatient;
import com.pm.appointmentservice.repository.AppointmentRepository;
import com.pm.appointmentservice.repository.CachedPatientRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AppointmentService {
    private final AppointmentRepository appointmentRepository;
    private final CachedPatientRepository cachedPatientRepository;

    public AppointmentService(AppointmentRepository appointmentRepository, CachedPatientRepository cachedPatientRepository){
        this.appointmentRepository = appointmentRepository;
        this.cachedPatientRepository = cachedPatientRepository;
    }

    public List<AppointmentResponseDTO> getAppointmentByDateRange(
            LocalDateTime from, LocalDateTime to
    ) {
        return appointmentRepository.findByStartTimeBetween(from, to).stream()
                .map(appointment ->  {

                    String name = cachedPatientRepository.findById(
                            appointment.getPatientId()
                    ).map(CachedPatient::getFullName)
                            .orElse("Unknown");

                    AppointmentResponseDTO appointmentResponseDTO =
                            new AppointmentResponseDTO();
                    appointmentResponseDTO.setId(appointment.getId());
                    appointmentResponseDTO.setPatientId(appointment.getPatientId());
                    appointmentResponseDTO.setStartTime(appointment.getStartTime());
                    appointmentResponseDTO.setEndTime(appointment.getEndTime());
                    appointmentResponseDTO.setReason(appointment.getReason());
                    appointmentResponseDTO.setVersion(appointment.getVersion());
                    appointmentResponseDTO.setPatientName(name);
                    return appointmentResponseDTO;

                }).toList();
    }
}
