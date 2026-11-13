package pl.khamutouskaya.truckdispatch.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.khamutouskaya.truckdispatch.domain.AppUser;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    // Logowanie: konto po nazwie użytkownika
    Optional<AppUser> findByUsername(String username);
}
