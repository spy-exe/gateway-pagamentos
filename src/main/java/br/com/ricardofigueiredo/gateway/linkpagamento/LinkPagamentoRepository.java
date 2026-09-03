package br.com.ricardofigueiredo.gateway.linkpagamento;

import br.com.ricardofigueiredo.gateway.usuario.Usuario;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LinkPagamentoRepository extends JpaRepository<LinkPagamento, Long> {

    List<LinkPagamento> findByUsuarioOrderByCriadoEmDesc(Usuario usuario);

    Optional<LinkPagamento> findByCodigo(String codigo);

    Optional<LinkPagamento> findByCodigoAndUsuario(String codigo, Usuario usuario);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from LinkPagamento l where l.codigo = :codigo")
    Optional<LinkPagamento> buscarParaFinalizacao(@Param("codigo") String codigo);
}
