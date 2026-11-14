package com.azentio.ledgerline.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "rule_hit")
public class RuleHitEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "loan_file_id", nullable = false)
    private Long loanFileId;
    @Column(name = "rule_id", nullable = false)
    private String ruleId;
    @Column(nullable = false)
    private int points;
    @Column(nullable = false)
    private String reason;
    /** Evidence as JSON. Never contains a full PAN or account number. */
    @Column(nullable = false)
    private String evidence;

    protected RuleHitEntity() {
    }

    public RuleHitEntity(Long loanFileId, String ruleId, int points, String reason, String evidence) {
        this.loanFileId = loanFileId;
        this.ruleId = ruleId;
        this.points = points;
        this.reason = reason;
        this.evidence = evidence;
    }

    public Long getId() { return id; }
    public Long getLoanFileId() { return loanFileId; }
    public String getRuleId() { return ruleId; }
    public int getPoints() { return points; }
    public String getReason() { return reason; }
    public String getEvidence() { return evidence; }
}
