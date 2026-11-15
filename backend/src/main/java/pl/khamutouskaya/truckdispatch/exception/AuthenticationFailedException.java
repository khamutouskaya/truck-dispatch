package pl.khamutouskaya.truckdispatch.exception;

// Błędny login lub hasło → 401 Unauthorized
public class AuthenticationFailedException extends RuntimeException {

    public AuthenticationFailedException() {
        // Jeden komunikat dla „brak użytkownika” i „złe hasło” — nie zdradzamy, które konta istnieją
        super("Invalid username or password");
    }
}
