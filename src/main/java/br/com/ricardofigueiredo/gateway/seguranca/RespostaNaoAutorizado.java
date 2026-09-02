package br.com.ricardofigueiredo.gateway.seguranca;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Sem isso o Spring Security devolveria uma pagina de erro do container.
 * Aqui a resposta de 401 sai no mesmo formato dos demais erros da API.
 */
@Component
public class RespostaNaoAutorizado implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RespostaNaoAutorizado(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest requisicao, HttpServletResponse resposta,
                         AuthenticationException excecao) throws IOException {
        ProblemDetail problema = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
        problema.setTitle("Nao autenticado");
        problema.setDetail("Envie um token valido no cabecalho Authorization: Bearer <token>.");

        resposta.setStatus(HttpStatus.UNAUTHORIZED.value());
        resposta.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(resposta.getOutputStream(), problema);
    }
}
