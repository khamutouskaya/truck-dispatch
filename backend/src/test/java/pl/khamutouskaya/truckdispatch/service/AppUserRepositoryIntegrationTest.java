package pl.khamutouskaya.truckdispatch.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import pl.khamutouskaya.truckdispatch.domain.AppUser;
import pl.khamutouskaya.truckdispatch.domain.Role;
import pl.khamutouskaya.truckdispatch.repository.AppUserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Test na prawdziwej bazie (migracja V9): konta demonstracyjne i ograniczenie rola ↔ kierowca.
@SpringBootTest
@Transactional
class AppUserRepositoryIntegrationTest {

    @Autowired
    private AppUserRepository appUserRepository;

    @Test
    void dispatcherHasNoDriver() {
        AppUser user = appUserRepository.findByUsername("dyspozytor").orElseThrow();

        assertThat(user.getRole()).isEqualTo(Role.DISPATCHER);
        assertThat(user.getDriver()).isNull();
        assertThat(user.getPasswordHash()).startsWith("$2y$10$");
    }

    @Test
    void driverAccountPointsToItsDriver() {
        AppUser user = appUserRepository.findByUsername("jan.kowalski").orElseThrow();

        assertThat(user.getRole()).isEqualTo(Role.DRIVER);
        assertThat(user.getDriver().getFirstName()).isEqualTo("Jan");
        assertThat(user.getDriver().getLastName()).isEqualTo("Kowalski");
    }

    @Test
    void driverRoleWithoutDriverIsRejected() {
        AppUser user = new AppUser();
        user.setUsername("bez.kierowcy");
        user.setPasswordHash("$2y$10$x");
        user.setRole(Role.DRIVER);

        assertThatThrownBy(() -> appUserRepository.saveAndFlush(user))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
