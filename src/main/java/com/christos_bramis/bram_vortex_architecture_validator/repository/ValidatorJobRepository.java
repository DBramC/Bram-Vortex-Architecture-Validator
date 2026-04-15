package com.christos_bramis.bram_vortex_architecture_validator.repository;

import com.christos_bramis.bram_vortex_architecture_validator.entity.ValidatorJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ValidatorJobRepository extends JpaRepository<ValidatorJob, String> {
    // Μπορεί να χρειαστείς να βρεις το Terraform Job με βάση το Analysis Job
    Optional<ValidatorJob> findByAnalysisJobId(String analysisJobId);
}