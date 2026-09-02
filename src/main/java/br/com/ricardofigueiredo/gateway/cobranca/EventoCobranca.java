package br.com.ricardofigueiredo.gateway.cobranca;

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

/**
 * Trilha de auditoria da cobranca. Cada mudanca de estado vira uma linha aqui,
 * o que permite ao aplicativo montar a linha do tempo da transacao sem
 * precisar deduzir nada a partir do status atual.
 */
@Entity
@Table(name = "evento_cobranca")
public class EventoCobranca {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cobranca_id", nullable = false)
    private Cobranca cobranca;

    @Column(nullable = false)
    private String tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_anterior")
    private StatusCobranca statusAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_novo", nullable = false)
    private StatusCobranca statusNovo;

    private String detalhe;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected EventoCobranca() {
    }

    public EventoCobranca(Cobranca cobranca, String tipo, StatusCobranca statusAnterior, String detalhe) {
        this.cobranca = cobranca;
        this.tipo = tipo;
        this.statusAnterior = statusAnterior;
        this.statusNovo = cobranca.getStatus();
        this.detalhe = detalhe;
        this.criadoEm = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getTipo() {
        return tipo;
    }

    public StatusCobranca getStatusAnterior() {
        return statusAnterior;
    }

    public StatusCobranca getStatusNovo() {
        return statusNovo;
    }

    public String getDetalhe() {
        return detalhe;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
