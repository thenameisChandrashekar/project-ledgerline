package com.azentio.ledgerline.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

/** Immutable audit row: Hibernate never issues UPDATEs for it. */
@Entity
@Immutable
@Table(name = "case_audit")
public class CaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "case_id", nullable = false)
    private String caseId;
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", nullable = false)
    private CaseStatus fromStatus;
    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private CaseStatus toStatus;
    @Column(nullable = false)
    private String actor;
    @Column(nullable = false)
    private String comment;
    @Column(name = "acted_at", nullable = false)
    private Instant at;

    protected CaseAuditEntity() {
    }

    public CaseAuditEntity(String caseId, CaseStatus fromStatus, CaseStatus toStatus, String actor, String comment,
                           Instant at) {
        this.caseId = caseId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actor = actor;
        this.comment = comment;
        this.at = at;
    }

    public Long getId() { return id; }
    public String getCaseId() { return caseId; }
    public CaseStatus getFromStatus() { return fromStatus; }
    public CaseStatus getToStatus() { return toStatus; }
    public String getActor() { return actor; }
    public String getComment() { return comment; }
    public Instant getAt() { return at; }
}
