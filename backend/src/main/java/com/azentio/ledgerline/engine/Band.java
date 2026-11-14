package com.azentio.ledgerline.engine;

public enum Band {
    LOW, REFER, HIGH;

    public boolean opensCase() {
        return this != LOW;
    }
}
