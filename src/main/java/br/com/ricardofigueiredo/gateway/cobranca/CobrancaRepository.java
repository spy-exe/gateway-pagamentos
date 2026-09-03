package br.com.ricardofigueiredo.gateway.cobranca;

import br.com.ricardofigueiredo.gateway.usuario.Usuario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CobrancaRepository extends JpaRepository<Cobranca, Long>, JpaSpecificationExecutor<Cobranca> {

    @EntityGraph(attributePaths = "linkPagamento")
    Optional<Cobranca> findByCodigoAndUsuario(String codigo, Usuario usuario);

    @EntityGraph(attributePaths = "linkPagamento")
    Optional<Cobranca> findByUsuarioAndChaveIdempotencia(Usuario usuario, String chaveIdempotencia);

    @Override
    @EntityGraph(attributePaths = "linkPagamento")
    Page<Cobranca> findAll(Specification<Cobranca> specification, Pageable pageable);

    /**
     * Uma varredura no banco em vez de trazer as linhas e somar em memoria. Com
     * a cobranca em milhares de linhas a diferenca deixa de ser detalhe.
     */
    @Query("""
            select
                coalesce(sum(case when c.status in :liquidadas then c.valorEmCentavos else 0L end), 0L),
                coalesce(sum(c.valorEstornadoEmCentavos), 0L),
                coalesce(sum(case when c.status = :autorizada then c.valorEmCentavos else 0L end), 0L),
                coalesce(sum(case when c.status = :recusada then 1L else 0L end), 0L),
                count(c)
            from Cobranca c
            where c.usuario = :usuario and c.criadoEm >= :desde
            """)
    Object[] resumir(@Param("usuario") Usuario usuario,
                     @Param("liquidadas") Collection<StatusCobranca> liquidadas,
                     @Param("autorizada") StatusCobranca autorizada,
                     @Param("recusada") StatusCobranca recusada,
                     @Param("desde") Instant desde);

    /**
     * Volume por dia, agrupado no banco. O corte de dia sai em UTC, que e o
     * fuso em que a coluna e gravada.
     */
    @Query(value = """
            select cast(date_trunc('day', c.criado_em) as date) as dia,
                   coalesce(sum(case when c.status in ('CAPTURADA','PARCIALMENTE_ESTORNADA','ESTORNADA')
                                     then c.valor_em_centavos else 0 end), 0) as capturado,
                   count(*) as transacoes,
                   coalesce(sum(case when c.status = 'RECUSADA' then 1 else 0 end), 0) as recusadas
            from cobranca c
            where c.usuario_id = :usuarioId and c.criado_em >= :desde
            group by 1
            order by 1
            """, nativeQuery = true)
    List<Object[]> movimentoPorDia(@Param("usuarioId") Long usuarioId, @Param("desde") Instant desde);

    @Query("""
            select c.bandeira, count(c), coalesce(sum(c.valorEmCentavos), 0L)
            from Cobranca c
            where c.usuario = :usuario and c.criadoEm >= :desde and c.bandeira is not null
            group by c.bandeira
            order by count(c) desc
            """)
    List<Object[]> mixDeBandeiras(@Param("usuario") Usuario usuario, @Param("desde") Instant desde);
}
