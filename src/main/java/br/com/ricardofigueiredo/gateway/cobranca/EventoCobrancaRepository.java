package br.com.ricardofigueiredo.gateway.cobranca;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventoCobrancaRepository extends JpaRepository<EventoCobranca, Long> {

    List<EventoCobranca> findByCobrancaOrderByCriadoEmAsc(Cobranca cobranca);
}
