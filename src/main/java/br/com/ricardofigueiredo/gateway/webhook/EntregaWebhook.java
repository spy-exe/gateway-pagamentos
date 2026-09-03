package br.com.ricardofigueiredo.gateway.webhook;

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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Uma tentativa de avisar um endpoint sobre um evento.
 *
 * O intervalo entre tentativas cresce a cada falha, para nao martelar um
 * servidor que ja esta em apuros: um minuto, cinco, meia hora, duas horas,
 * seis horas. Depois disso a entrega para e fica registrada como falha, a
 * espera de reenvio manual.
 */
@Entity
@Table(name = "entrega_webhook")
public class EntregaWebhook {

    static final List<Duration> ESPERAS = List.of(
            Duration.ofMinutes(1),
            Duration.ofMinutes(5),
            Duration.ofMinutes(30),
            Duration.ofHours(2),
            Duration.ofHours(6));

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String codigo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "endpoint_id", nullable = false)
    private EndpointWebhook endpoint;

    @Column(nullable = false)
    private String evento;

    @Column(nullable = false, length = 4000)
    private String corpo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SituacaoDaEntrega situacao;

    @Column(nullable = false)
    private int tentativas;

    @Column(name = "ultimo_codigo_http")
    private Integer ultimoCodigoHttp;

    @Column(name = "ultima_falha", length = 300)
    private String ultimaFalha;

    @Column(name = "proxima_tentativa_em")
    private Instant proximaTentativaEm;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "concluido_em")
    private Instant concluidoEm;

    protected EntregaWebhook() {
    }

    public EntregaWebhook(EndpointWebhook endpoint, String evento, String corpo) {
        this.codigo = "evt_" + UUID.randomUUID().toString().replace("-", "");
        this.endpoint = endpoint;
        this.evento = evento;
        this.corpo = corpo;
        this.situacao = SituacaoDaEntrega.PENDENTE;
        this.tentativas = 0;
        this.criadoEm = Instant.now();
        this.proximaTentativaEm = this.criadoEm;
    }

    public void registrarSucesso(int codigoHttp) {
        this.tentativas += 1;
        this.ultimoCodigoHttp = codigoHttp;
        this.ultimaFalha = null;
        this.situacao = SituacaoDaEntrega.ENTREGUE;
        this.proximaTentativaEm = null;
        this.concluidoEm = Instant.now();
    }

    public void registrarFalha(Integer codigoHttp, String motivo) {
        this.tentativas += 1;
        this.ultimoCodigoHttp = codigoHttp;
        this.ultimaFalha = motivo == null ? null : motivo.substring(0, Math.min(motivo.length(), 300));

        if (this.tentativas > ESPERAS.size()) {
            this.situacao = SituacaoDaEntrega.FALHOU;
            this.proximaTentativaEm = null;
            this.concluidoEm = Instant.now();
            return;
        }

        this.situacao = SituacaoDaEntrega.PENDENTE;
        this.proximaTentativaEm = Instant.now().plus(ESPERAS.get(this.tentativas - 1));
    }

    /** Reenvio manual devolve a entrega para a fila, zerando a espera. */
    public void reenfileirar() {
        this.situacao = SituacaoDaEntrega.PENDENTE;
        this.proximaTentativaEm = Instant.now();
        this.concluidoEm = null;
    }

    public Long getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public EndpointWebhook getEndpoint() {
        return endpoint;
    }

    public String getEvento() {
        return evento;
    }

    public String getCorpo() {
        return corpo;
    }

    public SituacaoDaEntrega getSituacao() {
        return situacao;
    }

    public int getTentativas() {
        return tentativas;
    }

    public Integer getUltimoCodigoHttp() {
        return ultimoCodigoHttp;
    }

    public String getUltimaFalha() {
        return ultimaFalha;
    }

    public Instant getProximaTentativaEm() {
        return proximaTentativaEm;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getConcluidoEm() {
        return concluidoEm;
    }
}
