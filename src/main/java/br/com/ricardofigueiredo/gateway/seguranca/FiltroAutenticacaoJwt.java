package br.com.ricardofigueiredo.gateway.seguranca;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Le o cabecalho Authorization, valida o token e coloca o usuario no contexto
 * de seguranca. Requisicao sem token segue adiante e e barrada mais na frente
 * pelas regras do SecurityFilterChain.
 */
@Component
public class FiltroAutenticacaoJwt extends OncePerRequestFilter {

    private static final String PREFIXO = "Bearer ";

    private final JwtService jwtService;
    private final DetalhesDoUsuarioService detalhesDoUsuarioService;

    public FiltroAutenticacaoJwt(JwtService jwtService, DetalhesDoUsuarioService detalhesDoUsuarioService) {
        this.jwtService = jwtService;
        this.detalhesDoUsuarioService = detalhesDoUsuarioService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requisicao, HttpServletResponse resposta,
                                    FilterChain cadeia) throws ServletException, IOException {
        String cabecalho = requisicao.getHeader(HttpHeaders.AUTHORIZATION);

        if (cabecalho != null && cabecalho.startsWith(PREFIXO)
                && SecurityContextHolder.getContext().getAuthentication() == null) {

            String token = cabecalho.substring(PREFIXO.length()).trim();
            jwtService.emailDoToken(token).ifPresent(email -> autenticar(email, requisicao));
        }

        cadeia.doFilter(requisicao, resposta);
    }

    private void autenticar(String email, HttpServletRequest requisicao) {
        try {
            UserDetails usuario = detalhesDoUsuarioService.loadUserByUsername(email);
            var autenticacao = new UsernamePasswordAuthenticationToken(usuario, null, usuario.getAuthorities());
            autenticacao.setDetails(new WebAuthenticationDetailsSource().buildDetails(requisicao));
            SecurityContextHolder.getContext().setAuthentication(autenticacao);
        } catch (UsernameNotFoundException excecao) {
            SecurityContextHolder.clearContext();
        }
    }
}
