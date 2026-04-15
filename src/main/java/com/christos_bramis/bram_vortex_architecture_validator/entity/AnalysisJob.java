package com.christos_bramis.bram_vortex_architecture_validator.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "analysis_jobs")
public class AnalysisJob {

    @Id
    @Column(name = "job_id", insertable = false, updatable = false)
    private String jobId;

    // Εδώ είναι το μόνο πεδίο που μας νοιάζει να διαβάσουμε!
    @Column(name = "blueprint_json", columnDefinition = "jsonb", insertable = false, updatable = false)
    private String blueprintJson;

    // 👈 ΠΡΟΣΘΗΚΗ: Χρειαζόμαστε το Raw Zip για να το ελέγξουμε!
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "master_zip", columnDefinition = "bytea", insertable = false, updatable = false)
    private byte[] masterZip;

    // Βάζουμε ΜΟΝΟ Getters. Καθόλου Setters για να είναι 100% Read-Only!
    public String getJobId() { return jobId; }

    public String getBlueprintJson() { return blueprintJson; }

    public byte[] getMasterZip() { return masterZip; }
}