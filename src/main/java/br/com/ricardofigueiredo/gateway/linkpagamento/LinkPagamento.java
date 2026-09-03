package br.com.ricardofigueiredo.gateway.linkpagamento;

import br.com.ricardofigueiredo.gateway.cobranca.MetodoPagamento;
import br.com.ricardofigueiredo.gateway.comum.excecao.RegraDeNegocioException;
import br.com.ricardofigueiredo.gateway.usuario.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "link_pagamento")
public class LinkPagamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String codigo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false)
    private String descricao;

    @Column(name = "valor_em_centavos", nullable = false)
    private long valorEmCentavos;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MetodoPagamento metodo;

    @Column(name = "parcelas_maximas", nullable = false)
    private int parcelasMaximas;

    @Column(name = "limite_de_usos")
    private Integer limiteDeUsos;

    @Column(nullable = false)
    private int usos;

    @Column(nullable = false)
    private boolean ativo;

    @Column(name = "expira_em")
    private Instant expiraEm;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    @Version
    @Column(nullable = false)
    private long versao;

    protected LinkPagamento() {
    }

    public LinkPagamento(Usuario usuario, String descricao, long valorEmCentavos,
                         MetodoPagamento metodo, int parcelasMaximas,
                         Integer limiteDeUsos, Instant expiraEm) {
        if (metodo != MetodoPagamento.CARTAO_CREDITO && parcelasMaximas > 1) {
            throw new RegraDeNegocioException("Somente links de credito podem oferecer parcelamento.");
        }

        this.codigo = "link_" + UUID.randomUUID().toString().replace("-", "");
        this.usuario = usuario;
        this.descricao = descricao.trim();
        this.valorEmCentavos = valorEmCentavos;
        this.metodo = metodo;
        this.parcelasMaximas = parcelasMaximas;
        this.limiteDeUsos = limiteDeUsos;
        this.usos = 0;
        this.ativo = true;
        this.expiraEm = expiraEm;
        this.criadoEm = Instant.now();
        this.atualizadoEm = this.criadoEm;
    }

    public SituacaoLinkPagamento situacao(Instant agora) {
        if (!ativo) {
            return SituacaoLinkPagamento.PAUSADO;
        }
        if (expiraEm != null && !expiraEm.isAfter(agora)) {
            return SituacaoLinkPagamento.EXPIRADO;
        }
        if (limiteDeUsos != null && usos >= limiteDeUsos) {
            return SituacaoLinkPagamento.ESGOTADO;
        }
        return SituacaoLinkPagamento.ATIVO;
    }

    public void exigirDisponivel(Instant agora) {
        switch (situacao(agora)) {
            case PAUSADO -> throw new RegraDeNegocioException("Este link de pagamento esta pausado.");
            case EXPIRADO -> throw new RegraDeNegocioException("Este link de pagamento expirou.");
            case ESGOTADO -> throw new RegraDeNegocioException("Este link atingiu o limite de pagamentos.");
            case ATIVO -> {
            }
        }
    }

    public void registrarUso() {
        exigirDisponivel(Instant.now());
        this.usos += 1;
        this.atualizadoEm = Instant.now();
    }

    public void definirAtivo(boolean ativo) {
        this.ativo = ativo;
        this.atualizadoEm = Instant.now();
    }

    public String getCodigo() {
        return codigo;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public String getDescricao() {
        return descricao;
    }

    public long getValorEmCentavos() {
        return valorEmCentavos;
    }

    public MetodoPagamento getMetodo() {
        return metodo;
    }

    public int getParcelasMaximas() {
        return parcelasMaximas;
    }

    public Integer getLimiteDeUsos() {
        return limiteDeUsos;
    }

    public int getUsos() {
        return usos;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public Instant getExpiraEm() {
        return expiraEm;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }
}
