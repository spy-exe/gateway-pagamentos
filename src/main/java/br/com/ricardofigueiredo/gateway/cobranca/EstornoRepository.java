package br.com.ricardofigueiredo.gateway.cobranca;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EstornoRepository extends JpaRepository<Estorno, Long> {

    List<Estorno> findByCobrancaOrderByCriadoEmAsc(Cobranca cobranca);
}
