package dev.mosiur.payledger.payments.common.security;

import dev.mosiur.payledger.payments.common.error.ProblemResponseWriter;
import dev.mosiur.payledger.payments.common.error.ProblemType;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** Keeps the standard WWW-Authenticate header and adds the same Problem Details body as every other error. */
@Component
public class ProblemSecurityHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final AuthenticationEntryPoint bearerEntryPoint = new BearerTokenAuthenticationEntryPoint();
    private final AccessDeniedHandler bearerAccessDenied = new BearerTokenAccessDeniedHandler();
    private final ProblemResponseWriter problemWriter;

    public ProblemSecurityHandler(ProblemResponseWriter problemWriter) {
        this.problemWriter = problemWriter;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException, ServletException {
        bearerEntryPoint.commence(request, response, exception);
        problemWriter.write(request, response, ProblemType.UNAUTHENTICATED,
                "Send a valid bearer access token in the Authorization header.");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
            throws IOException, ServletException {
        bearerAccessDenied.handle(request, response, exception);
        problemWriter.write(request, response, ProblemType.FORBIDDEN,
                "Your role does not allow this operation.");
    }
}
