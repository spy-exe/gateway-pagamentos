package br.com.ricardofigueiredo.gateway.seguranca;

import br.com.ricardofigueiredo.gateway.usuario.Usuario;
import br.com.ricardofigueiredo.gateway.usuario.UsuarioRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Caminhos de borda da autenticacao, dificeis de alcancar pelo teste de fluxo. */
class SegurancaUnitarioTest {

    private static final String SEGREDO = "segredo-de-teste-com-mais-de-trinta-e-dois-bytes-01234";

    private final JwtService jwtService = new JwtService(SEGREDO, 120);

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("token adulterado, expirado ou de outra chave nao devolve e-mail")
    void tokenInvalidoNaoAbre() {
        String valido = jwtService.gerarToken("loja@exemplo.com", Instant.now().plusSeconds(60));

        assertThat(jwtService.emailDoToken(valido)).contains("loja@exemplo.com");
        assertThat(jwtService.emailDoToken(valido + "x")).isEmpty();
        assertThat(jwtService.emailDoToken("nao e um token")).isEmpty();
        assertThat(jwtService.emailDoToken("")).isEmpty();

        String expirado = jwtService.gerarToken("loja@exemplo.com", Instant.now().minusSeconds(60));
        assertThat(jwtService.emailDoToken(expirado)).isEmpty();

        String deOutraChave = new JwtService("outro-segredo-com-mais-de-trinta-e-dois-bytes-0123456", 120)
                .gerarToken("loja@exemplo.com", Instant.now().plusSeconds(60));
        assertThat(jwtService.emailDoToken(deOutraChave)).isEmpty();
    }

    @Test
    @DisplayName("o servico de detalhes recusa e-mail que nao existe")
    void usuarioInexistente() {
        UsuarioRepository repositorio = mock(UsuarioRepository.class);
        when(repositorio.findByEmail("ninguem@exemplo.com")).thenReturn(Optional.empty());

        DetalhesDoUsuarioService servico = new DetalhesDoUsuarioService(repositorio);

        assertThatThrownBy(() -> servico.loadUserByUsername("ninguem@exemplo.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    @DisplayName("requisicao sem cabecalho segue adiante sem autenticar")
    void semCabecalhoNaoAutentica() throws Exception {
        FilterChain cadeia = mock(FilterChain.class);
        filtro(mock(DetalhesDoUsuarioService.class))
                .doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), cadeia);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(cadeia).doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));
    }

    @Test
    @DisplayName("cabecalho sem o prefixo Bearer e ignorado")
    void cabecalhoSemPrefixoNaoAutentica() throws Exception {
        MockHttpServletRequest requisicao = new MockHttpServletRequest();
        requisicao.addHeader(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpzZW5oYQ==");

        filtro(mock(DetalhesDoUsuarioService.class))
                .doFilter(requisicao, new MockHttpServletResponse(), mock(FilterChain.class));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("token valido coloca o usuario no contexto")
    void tokenValidoAutentica() throws Exception {
        Usuario usuario = new Usuario("loja@exemplo.com", "hash", "Loja", "loja@exemplo.com", "NITEROI");
        DetalhesDoUsuarioService detalhes = mock(DetalhesDoUsuarioService.class);
        when(detalhes.loadUserByUsername("loja@exemplo.com")).thenReturn(new UsuarioAutenticado(usuario));

        MockHttpServletRequest requisicao = new MockHttpServletRequest();
        requisicao.addHeader(HttpHeaders.AUTHORIZATION,
                "Bearer " + jwtService.gerarToken("loja@exemplo.com", Instant.now().plusSeconds(60)));

        filtro(detalhes).doFilter(requisicao, new MockHttpServletResponse(), mock(FilterChain.class));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
                .isEqualTo("loja@exemplo.com");
    }

    @Test
    @DisplayName("token de usuario que foi removido limpa o contexto em vez de estourar")
    void usuarioRemovidoLimpaOContexto() throws Exception {
        DetalhesDoUsuarioService detalhes = mock(DetalhesDoUsuarioService.class);
        when(detalhes.loadUserByUsername("sumiu@exemplo.com"))
                .thenThrow(new UsernameNotFoundException("sumiu"));

        MockHttpServletRequest requisicao = new MockHttpServletRequest();
        requisicao.addHeader(HttpHeaders.AUTHORIZATION,
                "Bearer " + jwtService.gerarToken("sumiu@exemplo.com", Instant.now().plusSeconds(60)));

        filtro(detalhes).doFilter(requisicao, new MockHttpServletResponse(), mock(FilterChain.class));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("contexto ja autenticado nao e sobrescrito")
    void contextoJaAutenticadoNaoMuda() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("outro", null, java.util.List.of()));

        MockHttpServletRequest requisicao = new MockHttpServletRequest();
        requisicao.addHeader(HttpHeaders.AUTHORIZATION,
                "Bearer " + jwtService.gerarToken("loja@exemplo.com", Instant.now().plusSeconds(60)));

        filtro(mock(DetalhesDoUsuarioService.class))
                .doFilter(requisicao, new MockHttpServletResponse(), mock(FilterChain.class));

        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("outro");
    }

    @Test
    @DisplayName("a resposta de 401 sai em JSON no formato de problema")
    void respostaDeNaoAutorizado() throws Exception {
        MockHttpServletResponse resposta = new MockHttpServletResponse();

        new RespostaNaoAutorizado(new ObjectMapper())
                .commence(new MockHttpServletRequest(), resposta, null);

        assertThat(resposta.getStatus()).isEqualTo(401);
        assertThat(resposta.getContentType()).isEqualTo("application/problem+json");
        assertThat(resposta.getContentAsString()).contains("Nao autenticado");
    }

    private FiltroAutenticacaoJwt filtro(DetalhesDoUsuarioService detalhes) {
        return new FiltroAutenticacaoJwt(jwtService, detalhes);
    }
}
