package com.azentio.ledgerline.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "loan_file")
public class LoanFileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "file_id", nullable = false, unique = true)
    private String fileId;
    @Column(name = "payload_hash", nullable = false)
    private String payloadHash;
    @Column(nullable = false)
    private String payload;
    @Column(name = "applicant_name", nullable = false)
    private String applicantName;
    @Column(nullable = false)
    private String pan;
    @Column(name = "account_number", nullable = false)
    private String accountNumber;
    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;
    @Column(nullable = false)
    private int score;
    @Column(nullable = false)
    private String band;
    @Column(name = "degraded_signals")
    private String degradedSignals;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LoanFileEntity() {
    }

    public LoanFileEntity(String fileId, String payloadHash, String payload, String applicantName, String pan,
                          String accountNumber, Instant submittedAt, int score, String band, String degradedSignals,
                          Instant createdAt) {
        this.fileId = fileId;
        this.payloadHash = payloadHash;
        this.payload = payload;
        this.applicantName = applicantName;
        this.pan = pan;
        this.accountNumber = accountNumber;
        this.submittedAt = submittedAt;
        this.score = score;
        this.band = band;
        this.degradedSignals = degradedSignals;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getFileId() { return fileId; }
    public String getPayloadHash() { return payloadHash; }
    public String getPayload() { return payload; }
    public String getApplicantName() { return applicantName; }
    public String getPan() { return pan; }
    public String getAccountNumber() { return accountNumber; }
    public Instant getSubmittedAt() { return submittedAt; }
    public int getScore() { return score; }
    public String getBand() { return band; }
    public String getDegradedSignals() { return degradedSignals; }
    public Instant getCreatedAt() { return createdAt; }
}
