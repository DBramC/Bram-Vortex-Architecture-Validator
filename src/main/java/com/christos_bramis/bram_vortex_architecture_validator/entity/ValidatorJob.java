package com.christos_bramis.bram_vortex_architecture_validator.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "validator_jobs") // Ο δικός του, ανεξάρτητος πίνακας
public class ValidatorJob {

    @Id
    private String id; // Το ID αυτού του Validator Job

    @Column(name = "analysis_job_id", nullable = false)
    private String analysisJobId; // Κρατάμε το ID της ανάλυσης για reference

    @Column(name = "user_id")
    private String userId;

    @Column(name = "status")
    private String status; // π.χ. GENERATING, COMPLETED, FAILED

    // ΕΔΩ ΕΙΝΑΙ Η ΜΑΓΕΙΑ: Η Postgres θα το κάνει BYTEA (BLOB)
    // ΠΡΟΣΟΧΗ: Το όνομα της στήλης πρέπει να είναι "master_zip" όπως στο SQL!
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "master_zip", columnDefinition = "bytea")
    private byte[] masterZip;

    // --- Getters & Setters ---
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getAnalysisJobId() { return analysisJobId; }
    public void setAnalysisJobId(String analysisJobId) { this.analysisJobId = analysisJobId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    // Αλλάξαμε τα ονόματα σε MasterZip για να ταιριάζουν με το Service σου!
    public byte[] getMasterZip() { return masterZip; }
    public void setMasterZip(byte[] masterZip) { this.masterZip = masterZip; }
}