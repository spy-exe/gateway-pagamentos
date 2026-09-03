package br.com.ricardofigueiredo.gateway.cobranca;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "estorno")
public class Estorno {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String codigo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cobranca_id", nullable = false)
    private Cobranca cobranca;

    @Column(name = "valor_em_centavos", nullable = false)
    private long valorEmCentavos;

    private String motivo;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected Estorno() {
    }

    public Estorno(Cobranca cobranca, long valorEmCentavos, String motivo) {
        this.codigo = "est_" + UUID.randomUUID().toString().replace("-", "");
        this.cobranca = cobranca;
        this.valorEmCentavos = valorEmCentavos;
        this.motivo = motivo;
        this.criadoEm = Instant.now();
    }

    public String getCodigo() {
        return codigo;
    }

    public long getValorEmCentavos() {
        return valorEmCentavos;
    }

    public String getMotivo() {
        return motivo;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
