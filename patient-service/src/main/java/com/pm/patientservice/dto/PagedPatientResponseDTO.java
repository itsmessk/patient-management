package com.pm.patientservice.dto;

import java.util.List;

public class PagedPatientResponseDTO {
    private List<PatientResponseDTO> patients;

    private int page;
    private int totalPages;
    private int size;
    private int totalElements;

    public PagedPatientResponseDTO() {}

    public PagedPatientResponseDTO(List<PatientResponseDTO> patients,
                                   int page, int totalPages, int size, int totalElements) {
        this.patients = patients;
        this.page = page;
        this.totalPages = totalPages;
        this.size = size;
        this.totalElements = totalElements;
    }

    public List<PatientResponseDTO> getPatients() {
        return patients;
    }

    public void setPatients(List<PatientResponseDTO> patients) {
        this.patients = patients;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public int getTotalElements() {
        return totalElements;
    }

    public void setTotalElements(int totalElements) {
        this.totalElements = totalElements;
    }
}
