package com.pm.patientservice.service;


import com.pm.patientservice.dto.PagedPatientResponseDTO;
import com.pm.patientservice.dto.PatientRequestDTO;
import com.pm.patientservice.dto.PatientResponseDTO;
import com.pm.patientservice.exception.EmailAlreadyExistsException;
import com.pm.patientservice.exception.PatientNotFoundException;
import com.pm.patientservice.grpc.BillingServiceGrpcClient;
import com.pm.patientservice.kafka.KafkaProducer;
import com.pm.patientservice.mapper.PatientMapper;
import com.pm.patientservice.model.Patient;
import com.pm.patientservice.repository.PatientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class PatientService {

    private static final Logger log = LoggerFactory.getLogger(PatientService.class);
    private final BillingServiceGrpcClient billingServiceGrpcClient;


    private final PatientRepository patientRepository;

    private final KafkaProducer kafkaProducer;


    public PatientService(PatientRepository patientRepository,
                          BillingServiceGrpcClient billingServiceGrpcClient,
                          KafkaProducer kafkaProducer){
        this.patientRepository = patientRepository;
        this.billingServiceGrpcClient = billingServiceGrpcClient;
        this.kafkaProducer = kafkaProducer;
    }

    @Cacheable(
            value = "patients",
            key = "#page + '-' + #size + '-' + #sort + '-' + #sortField",
            condition = "#searchValue == ''"
    )
    public PagedPatientResponseDTO getPatients(int page, int size, String sort, String sortField,
    String searchValue) {

        log.info("redis: cache miss - fetching from db");

        try { //simulate a delay to check if this is working
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        Pageable pageable = PageRequest.of(page -1,
                size,
                sort.equalsIgnoreCase("desc") ?
                        Sort.by(sortField).descending() :
                        Sort.by(sortField).ascending());

        Page<Patient> patientPage;
        if(searchValue == null || searchValue.isBlank()){
            patientPage = patientRepository.findAll(pageable);
        }
        else {
            patientPage = patientRepository.findByNameContainingIgnoreCase(searchValue, pageable);
        }

        //List<Patient> patients = patientRepository.findAll();

//        List<PatientResponseDTO> patientRepsonseDTo = patients.stream()
//                .map(patient -> PatientMapper.toDTO(patient))
//                .toList();
//        List<PatientResponseDTO> patientResponseDTOs = patients.stream()
//                .map(PatientMapper::toDTO)
//                .toList();


        List<PatientResponseDTO> patientResponseDTOs = patientPage.getContent()
                .stream()
                .map(PatientMapper::toDTO)
                .toList();



        return new PagedPatientResponseDTO(
                patientResponseDTOs,
                patientPage.getNumber() + 1,
                patientPage.getTotalPages(),
                patientPage.getSize(),
                (int)patientPage.getTotalElements()
        );
    }


    public PatientResponseDTO createPatient(PatientRequestDTO patientRequestDTO){
        if(patientRepository.existsByEmail(
                patientRequestDTO.getEmail()
        )){
            throw new EmailAlreadyExistsException("A patient with this email" +
                    "already exists" + patientRequestDTO.getEmail());
        }

        Patient patient = PatientMapper.toModel(patientRequestDTO);
        Patient savePatient = patientRepository.save(patient);

        billingServiceGrpcClient.createBillingAccount(patient.getId().toString(), patient.getName(), patient.getEmail());
        kafkaProducer.sendEvent(patient);
        return PatientMapper.toDTO(savePatient);
    }

    public PatientResponseDTO updatePatient(UUID id, PatientRequestDTO patientRequestDTO){
        Patient patient = patientRepository.findById(id).orElseThrow(() -> new PatientNotFoundException("Patient not found with ID: ", id));

        if(patientRepository.existsByEmailAndIdNot(patientRequestDTO.getEmail(), id)){
            //&& !patientRequestDTO.getEmail().equals(patient.getEmail())){
            throw new EmailAlreadyExistsException("A patient with this" +
                    " email already exists in the system: "
            + patientRequestDTO.getEmail());
        }

        patient.setEmail(patientRequestDTO.getEmail());
        patient.setName(patientRequestDTO.getName());
        patient.setAddress(patientRequestDTO.getAddress());
        patient.setDateOfBirth(LocalDate.parse(patientRequestDTO.getDateOfBirth()));

        Patient updatedPatient = patientRepository.save(patient);

        return PatientMapper.toDTO(updatedPatient);
    }

    public void deletePatient(UUID id){
        patientRepository.deleteById(id);
    }



}
