package com.christos_bramis.bram_vortex_architecture_validator.controller;

import com.christos_bramis.bram_vortex_architecture_validator.repository.ValidatorJobRepository;
import com.christos_bramis.bram_vortex_architecture_validator.service.ValidatorService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/validator")
public class Validator {

    private final ValidatorService validatorService;
    private final ValidatorJobRepository validatorJobRepository;

    public Validator(ValidatorService validatorService, ValidatorJobRepository validatorJobRepository) {
        this.validatorService = validatorService;
        this.validatorJobRepository = validatorJobRepository;
    }

    /**
     * Endpoint που δέχεται το Webhook από τον Repo Analyzer.
     * Πλέον το userId έρχεται από το επικυρωμένο JWT Token.
     */
    @PostMapping("/validate/{analysisJobId}")
    public ResponseEntity<String> startValidation(
            @PathVariable String analysisJobId,
            Authentication auth) {

        String userId = (String) auth.getPrincipal();
        String token = (String) auth.getCredentials();
        System.out.println("🚀 [VALIDATOR CONTROLLER] Webhook received for Job: " + analysisJobId + " from User: " + userId);

        try {
            String validatorJobId = UUID.randomUUID().toString();

            // Ξεκινάμε την παραγωγή (Async) χρησιμοποιώντας το userId από το Token
            validatorService.startArchitectureValidation(validatorJobId, analysisJobId, userId, token);

            return ResponseEntity.ok(validatorJobId);
        } catch (Exception e) {
            System.err.println("❌ [CONTROLLER ERROR]: " + e.getMessage());
            return ResponseEntity.internalServerError().body("Error starting validation: " + e.getMessage());
        }
    }

    @GetMapping("/download/by-analysis/{analysisJobId}")
    public ResponseEntity<byte[]> downloadValidatorZipByAnalysisId(
            @PathVariable String analysisJobId,
            Authentication auth) {

        String userId = auth.getName();
        System.out.println("📦 [VALIDATOR] Download request for Analysis Job: " + analysisJobId + " by User: " + userId);

        return validatorJobRepository.findByAnalysisJobId(analysisJobId)
                .map(job -> {
                    if (!job.getUserId().equals(userId)) {
                        System.err.println("🚫 [SECURITY] Unauthorized access attempt by user: " + userId);
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).<byte[]>build();
                    }

                    // ΕΔΩ Η ΔΙΟΡΘΩΣΗ: Προσθήκη ελέγχου length == 0 και σωστή χρήση του getMasterZip()
                    if (!"COMPLETED".equals(job.getStatus()) || job.getMasterZip() == null || job.getMasterZip().length == 0) {
                        return ResponseEntity.status(HttpStatus.ACCEPTED).<byte[]>build(); // Επιστρέφει 202
                    }

                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.parseMediaType("application/zip"));
                    // Αλλαγή στο όνομα του αρχείου σε "master" zip
                    headers.setContentDispositionFormData("attachment", "vortex-master-" + analysisJobId + ".zip");
                    headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");

                    return new ResponseEntity<>(job.getMasterZip(), headers, HttpStatus.OK);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}