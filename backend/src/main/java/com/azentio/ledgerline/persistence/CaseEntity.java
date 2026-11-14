package com.azentio.ledgerline.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(name = "fcu_case")
public class CaseEntity {

    @Id
    @Column(name = "case_id")
    private String caseId;
    @Column(name = "loan_file_id", nullable = false)
    private Long loanFileId;
    @Column(name = "file_id", nullable = false)
    private String fileId;
    @Column(nullable = false)
    private int score;
    @Column(nullable = false)
    private String band;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CaseStatus status;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected CaseEntity() {
    }

    public CaseEntity(String caseId, Long loanFileId, String fileId, int score, String band, Instant createdAt) {
        this.caseId = caseId;
        this.loanFileId = loanFileId;
        this.fileId = fileId;
        this.score = score;
        this.band = band;
        this.status = CaseStatus.OPEN;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public void moveTo(CaseStatus next, Instant at) {
        this.status = next;
        this.updatedAt = at;
    }

    public String getCaseId() { return caseId; }
    public Long getLoanFileId() { return loanFileId; }
    public String getFileId() { return fileId; }
    public int getScore() { return score; }
    public String getBand() { return band; }
    public CaseStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
