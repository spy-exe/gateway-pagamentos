package br.com.ricardofigueiredo.gateway.webhook;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface EntregaWebhookRepository extends JpaRepository<EntregaWebhook, Long> {

    Page<EntregaWebhook> findByEndpointOrderByCriadoEmDesc(EndpointWebhook endpoint, Pageable paginacao);

    Optional<EntregaWebhook> findByCodigo(String codigo);

    /**
     * A rodada do entregador. O join carregado evita uma consulta por linha
     * so para descobrir a url e o segredo do endpoint.
     */
    @Query("""
            select e from EntregaWebhook e
            join fetch e.endpoint p
            where e.situacao = br.com.ricardofigueiredo.gateway.webhook.SituacaoDaEntrega.PENDENTE
              and e.proximaTentativaEm <= :agora
              and p.ativo = true
            order by e.proximaTentativaEm
            """)
    List<EntregaWebhook> pendentes(@Param("agora") Instant agora, Pageable limite);
}
