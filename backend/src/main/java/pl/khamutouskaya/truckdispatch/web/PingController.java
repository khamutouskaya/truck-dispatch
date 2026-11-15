package pl.khamutouskaya.truckdispatch.web;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PingController {

    @SecurityRequirements // bez tokenu (puste wymaganie zastępuje globalne z OpenApiConfig)
    @GetMapping("/api/ping")
    public String ping() {
        return "pong";
    }
}