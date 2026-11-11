package dev.mosiur.payledger.payments.common.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Writes a Problem Details body from servlet filters, which run outside Spring MVC's exception handling. */
@Component
public class ProblemResponseWriter {

    private final JsonMapper jsonMapper;

    public ProblemResponseWriter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public void write(HttpServletRequest request, HttpServletResponse response, ProblemType type, String detail)
            throws IOException {
        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("type", type.type().toString());
        problem.put("title", type.title());
        problem.put("status", type.status().value());
        problem.put("detail", detail);
        problem.put("instance", request.getRequestURI());
        response.setStatus(type.status().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getOutputStream().write(jsonMapper.writeValueAsBytes(problem));
    }
}
