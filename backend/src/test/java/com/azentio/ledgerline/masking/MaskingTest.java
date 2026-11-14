package com.azentio.ledgerline.masking;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MaskingTest {

    @Test
    void keepsOnlyLastFourCharacters() {
        assertThat(Masking.pan("ABCDE1234F")).isEqualTo("******234F");
        assertThat(Masking.account("001234564821")).isEqualTo("XXXXXXXX4821");
    }

    @Test
    void shortValuesAreStillMasked() {
        assertThat(Masking.account("4821")).isEqualTo("XXXX4821");
    }
}
