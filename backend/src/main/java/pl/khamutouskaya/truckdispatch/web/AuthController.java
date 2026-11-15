package pl.khamutouskaya.truckdispatch.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.khamutouskaya.truckdispatch.dto.LoginRequest;
import pl.khamutouskaya.truckdispatch.dto.LoginResponse;
import pl.khamutouskaya.truckdispatch.service.AuthService;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Login: username + password → JWT (valid 8 h)")
public class AuthController {

    private final AuthService authService;

    // 200, nie 201: logowanie nie tworzy zasobu (token nie jest zapisywany w bazie)
    @PostMapping("/login")
    @Operation(summary = "Log in and get a JWT access token (401 on wrong username or password)")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
