package pl.khamutouskaya.truckdispatch.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Zły klucz lub czas ważności zatrzymuje start aplikacji, zamiast dawać słabe tokeny
class JwtPropertiesTest {

    private static final String KEY_32_BYTES = "0123456789abcdef0123456789abcdef";

    @Test
    void acceptsKeyOf32Bytes() {
        assertThat(new JwtProperties(KEY_32_BYTES, Duration.ofHours(8)).ttl()).isEqualTo(Duration.ofHours(8));
    }

    @Test
    void rejectsKeyShorterThan256Bits() {
        assertThatThrownBy(() -> new JwtProperties(KEY_32_BYTES.substring(1), Duration.ofHours(8)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 32 bytes");
        assertThatThrownBy(() -> new JwtProperties(null, Duration.ofHours(8)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonPositiveTtl() {
        assertThatThrownBy(() -> new JwtProperties(KEY_32_BYTES, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JwtProperties(KEY_32_BYTES, Duration.ofMinutes(-5)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
