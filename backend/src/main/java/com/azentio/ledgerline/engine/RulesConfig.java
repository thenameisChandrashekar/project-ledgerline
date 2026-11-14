package com.azentio.ledgerline.engine;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * All rule points, tolerances and band thresholds. Bound from application.yml
 * (prefix {@code ledgerline.rules}); defaults here mirror the spec so unit tests can
 * construct it without Spring.
 */
public class RulesConfig {

    private BalanceBreak balanceBreak = new BalanceBreak();
    private SalaryCreditMissing salaryCreditMissing = new SalaryCreditMissing();
    private DeclaredIncome declaredIncome = new DeclaredIncome();
    private DocMetadata docMetadata = new DocMetadata();
    private NameMismatch nameMismatch = new NameMismatch();
    private DocumentReused documentReused = new DocumentReused();
    private Bands bands = new Bands();

    public static class BalanceBreak {
        private int points = 50;
        private BigDecimal tolerance = new BigDecimal("0.01");
        public int getPoints() { return points; }
        public void setPoints(int points) { this.points = points; }
        public BigDecimal getTolerance() { return tolerance; }
        public void setTolerance(BigDecimal tolerance) { this.tolerance = tolerance; }
    }

    public static class SalaryCreditMissing {
        private int points = 40;
        /** Allowed relative difference between credit and slip net pay (0.02 = 2%). */
        private BigDecimal amountTolerance = new BigDecimal("0.02");
        /** Days into the following month a salary credit may still land. */
        private int graceDays = 7;
        public int getPoints() { return points; }
        public void setPoints(int points) { this.points = points; }
        public BigDecimal getAmountTolerance() { return amountTolerance; }
        public void setAmountTolerance(BigDecimal amountTolerance) { this.amountTolerance = amountTolerance; }
        public int getGraceDays() { return graceDays; }
        public void setGraceDays(int graceDays) { this.graceDays = graceDays; }
    }

    public static class DeclaredIncome {
        private int points = 25;
        private BigDecimal maxRatio = new BigDecimal("1.2");
        public int getPoints() { return points; }
        public void setPoints(int points) { this.points = points; }
        public BigDecimal getMaxRatio() { return maxRatio; }
        public void setMaxRatio(BigDecimal maxRatio) { this.maxRatio = maxRatio; }
    }

    public static class DocMetadata {
        private int points = 30;
        private int maxModifiedAfterCreatedHours = 24;
        private List<String> editingTools = new ArrayList<>(List.of(
                "Acrobat Pro", "PDFelement", "Foxit PhantomPDF", "iLovePDF", "Sejda", "Smallpdf", "PDF-XChange Editor"));
        public int getPoints() { return points; }
        public void setPoints(int points) { this.points = points; }
        public int getMaxModifiedAfterCreatedHours() { return maxModifiedAfterCreatedHours; }
        public void setMaxModifiedAfterCreatedHours(int h) { this.maxModifiedAfterCreatedHours = h; }
        public List<String> getEditingTools() { return editingTools; }
        public void setEditingTools(List<String> editingTools) { this.editingTools = editingTools; }
    }

    public static class NameMismatch {
        private int points = 20;
        public int getPoints() { return points; }
        public void setPoints(int points) { this.points = points; }
    }

    public static class DocumentReused {
        private int points = 60;
        private int windowDays = 180;
        public int getPoints() { return points; }
        public void setPoints(int points) { this.points = points; }
        public int getWindowDays() { return windowDays; }
        public void setWindowDays(int windowDays) { this.windowDays = windowDays; }
    }

    public static class Bands {
        /** Score at or above which a file is REFER. */
        private int refer = 30;
        /** Score at or above which a file is HIGH. */
        private int high = 60;
        public int getRefer() { return refer; }
        public void setRefer(int refer) { this.refer = refer; }
        public int getHigh() { return high; }
        public void setHigh(int high) { this.high = high; }
    }

    public BalanceBreak getBalanceBreak() { return balanceBreak; }
    public void setBalanceBreak(BalanceBreak v) { this.balanceBreak = v; }
    public SalaryCreditMissing getSalaryCreditMissing() { return salaryCreditMissing; }
    public void setSalaryCreditMissing(SalaryCreditMissing v) { this.salaryCreditMissing = v; }
    public DeclaredIncome getDeclaredIncome() { return declaredIncome; }
    public void setDeclaredIncome(DeclaredIncome v) { this.declaredIncome = v; }
    public DocMetadata getDocMetadata() { return docMetadata; }
    public void setDocMetadata(DocMetadata v) { this.docMetadata = v; }
    public NameMismatch getNameMismatch() { return nameMismatch; }
    public void setNameMismatch(NameMismatch v) { this.nameMismatch = v; }
    public DocumentReused getDocumentReused() { return documentReused; }
    public void setDocumentReused(DocumentReused v) { this.documentReused = v; }
    public Bands getBands() { return bands; }
    public void setBands(Bands v) { this.bands = v; }
}
