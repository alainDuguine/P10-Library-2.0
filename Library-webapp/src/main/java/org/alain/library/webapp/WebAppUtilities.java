package org.alain.library.webapp;

import lombok.Getter;

import javax.servlet.http.HttpSession;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

@Getter
public class WebAppUtilities {

    public static final String EMAIL_FIELD = "email";
    public static final String PASSWORD_FIELD = "password";
    public static final String REDIRECT_LOGIN = "redirect:/login";
    public static final String CONNEXION_FAILED = "Connexion failed";
    public static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private WebAppUtilities() {
    }

    public static String getEncodedAuthorization(HttpSession session){
        String email = (String) session.getAttribute(EMAIL_FIELD);
        String password = (String) session.getAttribute(PASSWORD_FIELD);
        return "Basic " + Base64.getEncoder().encodeToString((email + ":" + password).getBytes());
    }
}
