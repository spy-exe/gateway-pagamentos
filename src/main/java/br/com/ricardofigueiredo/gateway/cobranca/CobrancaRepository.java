package br.com.ricardofigueiredo.gateway.cobranca;

import br.com.ricardofigueiredo.gateway.usuario.Usuario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CobrancaRepository extends JpaRepository<Cobranca, Long> {

    Optional<Cobranca> findByCodigoAndUsuario(String codigo, Usuario usuario);

    Optional<Cobranca> findByUsuarioAndChaveIdempotencia(Usuario usuario, String chaveIdempotencia);

    Page<Cobranca> findByUsuario(Usuario usuario, Pageable paginacao);

    Page<Cobranca> findByUsuarioAndStatus(Usuario usuario, StatusCobranca status, Pageable paginacao);

    Page<Cobranca> findByUsuarioAndMetodo(Usuario usuario, MetodoPagamento metodo, Pageable paginacao);

    Page<Cobranca> findByUsuarioAndStatusAndMetodo(Usuario usuario, StatusCobranca status,
                                                   MetodoPagamento metodo, Pageable paginacao);
}
