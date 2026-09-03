package br.com.ricardofigueiredo.gateway.seguranca;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;

/**
 * Emissao e leitura dos tokens JWT. A assinatura usa HMAC-SHA256 com o segredo
 * definido em gateway.jwt.segredo, que em producao vem de variavel de ambiente.
 */
@Service
public class JwtService {

    private final SecretKey chave;
    private final Duration validade;

    public JwtService(@Value("${gateway.jwt.segredo}") String segredo,
                      @Value("${gateway.jwt.validade-minutos}") long validadeMinutos) {
        this.chave = Keys.hmacShaKeyFor(segredo.getBytes(StandardCharsets.UTF_8));
        this.validade = Duration.ofMinutes(validadeMinutos);
    }

    public Instant expiracaoAPartirDeAgora() {
        return Instant.now().plus(validade);
    }

    public String gerarToken(String email, Instant expiraEm) {
        return Jwts.builder()
                .subject(email)
                .issuer("gateway-pagamentos")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(expiraEm))
                .signWith(chave)
                .compact();
    }

    /**
     * Devolve o e-mail do dono do token, ou vazio se o token estiver expirado,
     * adulterado ou assinado com outra chave.
     */
    public Optional<String> emailDoToken(String token) {
        if (!formaCompactaCanonica(token)) {
            return Optional.empty();
        }

        try {
            String email = Jwts.parser()
                    .verifyWith(chave)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
            return Optional.ofNullable(email);
        } catch (JwtException | IllegalArgumentException excecao) {
            return Optional.empty();
        }
    }

    /**
     * Um JWT assinado tem exatamente tres trechos Base64 URL sem padding. A
     * conferencia evita representacoes alternativas do mesmo conjunto de
     * bytes, que alguns decodificadores toleram mesmo com lixo no fim.
     */
    private boolean formaCompactaCanonica(String token) {
        if (token == null) {
            return false;
        }

        String[] trechos = token.split("\\.", -1);
        if (trechos.length != 3) {
            return false;
        }

        try {
            Base64.Decoder decodificador = Base64.getUrlDecoder();
            Base64.Encoder codificador = Base64.getUrlEncoder().withoutPadding();

            for (String trecho : trechos) {
                if (trecho.isBlank()
                        || !codificador.encodeToString(decodificador.decode(trecho)).equals(trecho)) {
                    return false;
                }
            }
            return true;
        } catch (IllegalArgumentException excecao) {
            return false;
        }
    }
}
