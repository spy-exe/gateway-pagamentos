package br.com.ricardofigueiredo.gateway.comum;

import br.com.ricardofigueiredo.gateway.comum.excecao.ConflitoException;
import br.com.ricardofigueiredo.gateway.comum.excecao.RecursoNaoEncontradoException;
import br.com.ricardofigueiredo.gateway.comum.excecao.RegraDeNegocioException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Concentra a traducao de excecoes para respostas HTTP no formato RFC 7807.
 * Nenhum controller precisa montar corpo de erro na mao.
 */
@RestControllerAdvice
public class ManipuladorDeExcecoes {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail tratarValidacao(MethodArgumentNotValidException excecao) {
        Map<String, String> campos = new LinkedHashMap<>();
        for (FieldError erro : excecao.getBindingResult().getFieldErrors()) {
            campos.putIfAbsent(erro.getField(), erro.getDefaultMessage());
        }
        excecao.getBindingResult().getGlobalErrors()
                .forEach(erro -> campos.putIfAbsent(erro.getObjectName(), erro.getDefaultMessage()));

        ProblemDetail problema = montar(HttpStatus.BAD_REQUEST, "Requisicao invalida",
                "Um ou mais campos nao passaram na validacao.");
        problema.setProperty("campos", campos);
        return problema;
    }

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ProblemDetail tratarNaoEncontrado(RecursoNaoEncontradoException excecao) {
        return montar(HttpStatus.NOT_FOUND, "Recurso nao encontrado", excecao.getMessage());
    }

    @ExceptionHandler(RegraDeNegocioException.class)
    public ProblemDetail tratarRegraDeNegocio(RegraDeNegocioException excecao) {
        return montar(HttpStatus.UNPROCESSABLE_ENTITY, "Operacao nao permitida", excecao.getMessage());
    }

    @ExceptionHandler(ConflitoException.class)
    public ProblemDetail tratarConflito(ConflitoException excecao) {
        return montar(HttpStatus.CONFLICT, "Conflito", excecao.getMessage());
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail tratarCredenciais(BadCredentialsException excecao) {
        return montar(HttpStatus.UNAUTHORIZED, "Credenciais invalidas",
                "E-mail ou senha nao conferem.");
    }

    private ProblemDetail montar(HttpStatus status, String titulo, String detalhe) {
        ProblemDetail problema = ProblemDetail.forStatus(status);
        problema.setTitle(titulo);
        problema.setDetail(detalhe);
        return problema;
    }
}
