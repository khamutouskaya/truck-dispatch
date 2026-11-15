package pl.khamutouskaya.truckdispatch.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.khamutouskaya.truckdispatch.domain.AppUser;
import pl.khamutouskaya.truckdispatch.dto.LoginRequest;
import pl.khamutouskaya.truckdispatch.dto.LoginResponse;
import pl.khamutouskaya.truckdispatch.exception.AuthenticationFailedException;
import pl.khamutouskaya.truckdispatch.repository.AppUserRepository;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    // Skrót losowego hasła: sprawdzany, gdy loginu nie ma, żeby odpowiedź trwała tyle samo co przy złym haśle
    // (inaczej po czasie odpowiedzi dałoby się odgadnąć, które loginy istnieją)
    private static final String DUMMY_HASH = "$2y$10$idIofZFF0ycxGe9feKhklu1dv09ZFXSjqsUs5vFGToi2S4jExx0RG";

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    public LoginResponse login(LoginRequest request) {
        Optional<AppUser> user = appUserRepository.findByUsername(request.username());
        String hash = user.map(AppUser::getPasswordHash).orElse(DUMMY_HASH);
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);
        if (user.isEmpty() || !passwordMatches) {
            throw new AuthenticationFailedException();
        }

        AppUser account = user.get();
        TokenService.IssuedToken token = tokenService.issue(account);
        Long driverId = account.getDriver() != null ? account.getDriver().getId() : null;
        return new LoginResponse(token.value(), "Bearer", token.expiresAt(), account.getUsername(),
                account.getRole(), driverId);
    }
}
