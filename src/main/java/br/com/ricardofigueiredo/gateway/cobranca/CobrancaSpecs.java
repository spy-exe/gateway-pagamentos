package br.com.ricardofigueiredo.gateway.cobranca;

import br.com.ricardofigueiredo.gateway.usuario.Usuario;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Filtros da listagem montados como Specification. A alternativa seria uma
 * derived query por combinacao de filtro, que multiplica a cada filtro novo.
 * Aqui cada criterio e independente e some sozinho quando nao foi informado.
 */
public final class CobrancaSpecs {

    private CobrancaSpecs() {
    }

    public static Specification<Cobranca> de(Usuario usuario, StatusCobranca status, MetodoPagamento metodo,
                                             Instant desde, Instant ate, String busca,
                                             Long valorMinimo, Long valorMaximo) {
        List<Specification<Cobranca>> criterios = new ArrayList<>();
        criterios.add(doUsuario(usuario));

        if (status != null) {
            criterios.add((raiz, consulta, cb) -> cb.equal(raiz.get("status"), status));
        }
        if (metodo != null) {
            criterios.add((raiz, consulta, cb) -> cb.equal(raiz.get("metodo"), metodo));
        }
        if (desde != null) {
            criterios.add((raiz, consulta, cb) -> cb.greaterThanOrEqualTo(raiz.get("criadoEm"), desde));
        }
        if (ate != null) {
            criterios.add((raiz, consulta, cb) -> cb.lessThanOrEqualTo(raiz.get("criadoEm"), ate));
        }
        if (valorMinimo != null) {
            criterios.add((raiz, consulta, cb) -> cb.greaterThanOrEqualTo(
                    raiz.get("valorEmCentavos"), valorMinimo));
        }
        if (valorMaximo != null) {
            criterios.add((raiz, consulta, cb) -> cb.lessThanOrEqualTo(
                    raiz.get("valorEmCentavos"), valorMaximo));
        }
        if (busca != null && !busca.isBlank()) {
            String alvo = "%" + busca.trim().toLowerCase(Locale.ROOT) + "%";
            criterios.add((raiz, consulta, cb) -> cb.or(
                    cb.like(cb.lower(raiz.get("descricao")), alvo),
                    cb.like(cb.lower(raiz.get("codigo")), alvo),
                    cb.like(cb.lower(raiz.get("codigoAutorizacao")), alvo),
                    cb.like(cb.lower(raiz.get("chaveIdempotencia")), alvo),
                    cb.like(cb.lower(raiz.get("ultimosQuatro")), alvo)));
        }

        return Specification.allOf(criterios);
    }

    public static Specification<Cobranca> de(Usuario usuario, StatusCobranca status, MetodoPagamento metodo,
                                             Instant desde, Instant ate, String busca) {
        return de(usuario, status, metodo, desde, ate, busca, null, null);
    }

    public static Specification<Cobranca> doUsuario(Usuario usuario) {
        return (raiz, consulta, cb) -> cb.equal(raiz.get("usuario"), usuario);
    }
}
