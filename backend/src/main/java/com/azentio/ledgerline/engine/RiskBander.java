package com.azentio.ledgerline.engine;

public class RiskBander {

    private final RulesConfig.Bands bands;

    public RiskBander(RulesConfig config) {
        this.bands = config.getBands();
    }

    public Band band(int score) {
        if (score >= bands.getHigh()) {
            return Band.HIGH;
        }
        return score >= bands.getRefer() ? Band.REFER : Band.LOW;
    }
}
