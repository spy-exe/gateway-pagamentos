package br.com.ricardofigueiredo.gateway.usuario;

import br.com.ricardofigueiredo.gateway.comum.excecao.ConflitoException;
import br.com.ricardofigueiredo.gateway.seguranca.JwtService;
import br.com.ricardofigueiredo.gateway.usuario.dto.LoginRequest;
import br.com.ricardofigueiredo.gateway.usuario.dto.RegistroRequest;
import br.com.ricardofigueiredo.gateway.usuario.dto.TokenResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

@Service
public class AutenticacaoService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder codificadorDeSenha;
    private final AuthenticationManager gerenciadorDeAutenticacao;
    private final JwtService jwtService;

    public AutenticacaoService(UsuarioRepository usuarioRepository,
                               PasswordEncoder codificadorDeSenha,
                               AuthenticationManager gerenciadorDeAutenticacao,
                               JwtService jwtService) {
        this.usuarioRepository = usuarioRepository;
        this.codificadorDeSenha = codificadorDeSenha;
        this.gerenciadorDeAutenticacao = gerenciadorDeAutenticacao;
        this.jwtService = jwtService;
    }

    @Transactional
    public Usuario registrar(RegistroRequest requisicao) {
        String email = normalizar(requisicao.email());

        if (usuarioRepository.existsByEmail(email)) {
            throw new ConflitoException("Ja existe um cadastro com este e-mail.");
        }

        Usuario usuario = new Usuario(
                email,
                codificadorDeSenha.encode(requisicao.senha()),
                requisicao.nomeEstabelecimento().trim(),
                vazioVira(requisicao.chavePix(), email),
                vazioVira(requisicao.cidade(), "SAO PAULO"));

        return usuarioRepository.save(usuario);
    }

    public TokenResponse autenticar(LoginRequest requisicao) {
        gerenciadorDeAutenticacao.authenticate(
                new UsernamePasswordAuthenticationToken(normalizar(requisicao.email()), requisicao.senha()));

        Instant expiraEm = jwtService.expiracaoAPartirDeAgora();
        return TokenResponse.bearer(jwtService.gerarToken(normalizar(requisicao.email()), expiraEm), expiraEm);
    }

    private String normalizar(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /** Sem chave Pix informada o proprio e-mail vira a chave, que e o caso mais comum. */
    private String vazioVira(String valor, String padrao) {
        return valor == null || valor.isBlank() ? padrao : valor.trim();
    }
}
