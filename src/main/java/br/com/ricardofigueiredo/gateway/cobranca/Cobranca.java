package br.com.ricardofigueiredo.gateway.cobranca;

import br.com.ricardofigueiredo.gateway.autorizacao.Bandeira;
import br.com.ricardofigueiredo.gateway.autorizacao.CartaoTokenizado;
import br.com.ricardofigueiredo.gateway.autorizacao.MotivoRecusa;
import br.com.ricardofigueiredo.gateway.autorizacao.ResultadoAutorizacao;
import br.com.ricardofigueiredo.gateway.comum.excecao.RegraDeNegocioException;
import br.com.ricardofigueiredo.gateway.linkpagamento.LinkPagamento;
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

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Cobranca e a raiz do dominio. As regras de transicao de estado ficam aqui e
 * nao no service, para que nenhuma camada consiga deixar a cobranca em um
 * estado impossivel, como capturar algo que ja foi cancelado.
 */
@Entity
@Table(name = "cobranca")
public class Cobranca {

    private static final Set<StatusCobranca> ESTORNAVEIS =
            EnumSet.of(StatusCobranca.CAPTURADA, StatusCobranca.PARCIALMENTE_ESTORNADA);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String codigo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "valor_em_centavos", nullable = false)
    private long valorEmCentavos;

    @Column(name = "valor_estornado_em_centavos", nullable = false)
    private long valorEstornadoEmCentavos;

    @Column(nullable = false, length = 3)
    private String moeda;

    @Column(nullable = false)
    private String descricao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MetodoPagamento metodo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusCobranca status;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo_recusa")
    private MotivoRecusa motivoRecusa;

    @Column(name = "codigo_autorizacao")
    private String codigoAutorizacao;

    @Column(name = "captura_automatica", nullable = false)
    private boolean capturaAutomatica;

    @Enumerated(EnumType.STRING)
    private Bandeira bandeira;

    private String bin;

    @Column(name = "ultimos_quatro")
    private String ultimosQuatro;

    @Column(name = "nome_portador")
    private String nomePortador;

    @Column(name = "chave_idempotencia")
    private String chaveIdempotencia;

    @Column(nullable = false)
    private int parcelas;

    @Column(name = "pix_copia_e_cola", length = 512)
    private String pixCopiaECola;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "link_pagamento_id")
    private LinkPagamento linkPagamento;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected Cobranca() {
    }

    public Cobranca(Usuario usuario, long valorEmCentavos, String descricao, MetodoPagamento metodo,
                    boolean capturaAutomatica, CartaoTokenizado cartao, String chaveIdempotencia,
                    int parcelas, ResultadoAutorizacao resultado) {
        this(usuario, valorEmCentavos, descricao, metodo, capturaAutomatica, cartao,
                chaveIdempotencia, parcelas, resultado, null);
    }

    public Cobranca(Usuario usuario, long valorEmCentavos, String descricao, MetodoPagamento metodo,
                    boolean capturaAutomatica, CartaoTokenizado cartao, String chaveIdempotencia,
                    int parcelas, ResultadoAutorizacao resultado, LinkPagamento linkPagamento) {
        this.codigo = "cob_" + UUID.randomUUID().toString().replace("-", "");
        this.usuario = usuario;
        this.valorEmCentavos = valorEmCentavos;
        this.valorEstornadoEmCentavos = 0L;
        this.moeda = "BRL";
        this.descricao = descricao;
        this.metodo = metodo;
        this.capturaAutomatica = capturaAutomatica;
        this.chaveIdempotencia = chaveIdempotencia;
        this.parcelas = parcelas;
        this.linkPagamento = linkPagamento;
        this.criadoEm = Instant.now();
        this.atualizadoEm = this.criadoEm;

        if (cartao != null) {
            this.bandeira = cartao.getBandeira();
            this.bin = cartao.getBin();
            this.ultimosQuatro = cartao.getUltimosQuatro();
            this.nomePortador = cartao.getNomePortador();
        }

        if (resultado.aprovada()) {
            this.codigoAutorizacao = resultado.codigoAutorizacao();
            this.status = capturaAutomatica ? StatusCobranca.CAPTURADA : StatusCobranca.AUTORIZADA;
        } else {
            this.motivoRecusa = resultado.motivo();
            this.status = StatusCobranca.RECUSADA;
        }
    }

    public void capturar() {
        if (status != StatusCobranca.AUTORIZADA) {
            throw new RegraDeNegocioException(
                    "So e possivel capturar uma cobranca autorizada. Status atual: " + status + ".");
        }
        this.status = StatusCobranca.CAPTURADA;
        marcarAlteracao();
    }

    public void cancelar() {
        if (status != StatusCobranca.AUTORIZADA) {
            throw new RegraDeNegocioException(
                    "So e possivel cancelar uma cobranca ainda nao capturada. Status atual: " + status + ".");
        }
        this.status = StatusCobranca.CANCELADA;
        marcarAlteracao();
    }

    public void registrarEstorno(long valorDoEstornoEmCentavos) {
        if (!ESTORNAVEIS.contains(status)) {
            throw new RegraDeNegocioException(
                    "So e possivel estornar uma cobranca capturada. Status atual: " + status + ".");
        }
        if (valorDoEstornoEmCentavos <= 0) {
            throw new RegraDeNegocioException("O valor do estorno deve ser maior que zero.");
        }
        if (valorDoEstornoEmCentavos > saldoEstornavelEmCentavos()) {
            throw new RegraDeNegocioException("O valor do estorno excede o saldo disponivel de "
                    + saldoEstornavelEmCentavos() + " centavos.");
        }

        this.valorEstornadoEmCentavos += valorDoEstornoEmCentavos;
        this.status = saldoEstornavelEmCentavos() == 0
                ? StatusCobranca.ESTORNADA
                : StatusCobranca.PARCIALMENTE_ESTORNADA;
        marcarAlteracao();
    }

    /**
     * O troco da divisao vai para a primeira parcela, que e como as
     * adquirentes fazem: ninguem cobra fracao de centavo.
     */
    public long valorDaParcelaEmCentavos() {
        return valorEmCentavos / parcelas;
    }

    public long ajusteNaPrimeiraParcelaEmCentavos() {
        return valorEmCentavos % parcelas;
    }

    public void registrarPix(String copiaECola) {
        this.pixCopiaECola = copiaECola;
    }

    public long saldoEstornavelEmCentavos() {
        return valorEmCentavos - valorEstornadoEmCentavos;
    }

    private void marcarAlteracao() {
        this.atualizadoEm = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public long getValorEmCentavos() {
        return valorEmCentavos;
    }

    public long getValorEstornadoEmCentavos() {
        return valorEstornadoEmCentavos;
    }

    public String getMoeda() {
        return moeda;
    }

    public String getDescricao() {
        return descricao;
    }

    public MetodoPagamento getMetodo() {
        return metodo;
    }

    public StatusCobranca getStatus() {
        return status;
    }

    public MotivoRecusa getMotivoRecusa() {
        return motivoRecusa;
    }

    public String getCodigoAutorizacao() {
        return codigoAutorizacao;
    }

    public boolean isCapturaAutomatica() {
        return capturaAutomatica;
    }

    public Bandeira getBandeira() {
        return bandeira;
    }

    public String getBin() {
        return bin;
    }

    public String getUltimosQuatro() {
        return ultimosQuatro;
    }

    public String getNomePortador() {
        return nomePortador;
    }

    public String getChaveIdempotencia() {
        return chaveIdempotencia;
    }

    public int getParcelas() {
        return parcelas;
    }

    public String getPixCopiaECola() {
        return pixCopiaECola;
    }

    public String getCodigoDoLinkPagamento() {
        return linkPagamento == null ? null : linkPagamento.getCodigo();
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }
}
