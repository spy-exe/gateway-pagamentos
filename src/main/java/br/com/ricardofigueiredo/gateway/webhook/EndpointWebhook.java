package br.com.ricardofigueiredo.gateway.webhook;

import br.com.ricardofigueiredo.gateway.usuario.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Endereco que o estabelecimento cadastra para ser avisado do que acontece com
 * as cobrancas dele. O segredo e sorteado aqui e mostrado por inteiro uma vez
 * so, no cadastro: e com ele que o outro lado confere a assinatura.
 */
@Entity
@Table(name = "endpoint_webhook")
public class EndpointWebhook {

    private static final SecureRandom SORTEIO = new SecureRandom();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String codigo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false)
    private String url;

    @Column(nullable = false)
    private String segredo;

    private String descricao;

    @Column(nullable = false)
    private boolean ativo;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected EndpointWebhook() {
    }

    public EndpointWebhook(Usuario usuario, String url, String descricao) {
        this.codigo = "whk_" + UUID.randomUUID().toString().replace("-", "");
        this.usuario = usuario;
        this.url = url;
        this.descricao = descricao;
        this.segredo = sortearSegredo();
        this.ativo = true;
        this.criadoEm = Instant.now();
    }

    private static String sortearSegredo() {
        byte[] bytes = new byte[24];
        SORTEIO.nextBytes(bytes);
        return "whsec_" + HexFormat.of().formatHex(bytes);
    }

    public void desativar() {
        this.ativo = false;
    }

    public void ativar() {
        this.ativo = true;
    }

    public String getCodigo() {
        return codigo;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public String getUrl() {
        return url;
    }

    public String getSegredo() {
        return segredo;
    }

    public String getDescricao() {
        return descricao;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
