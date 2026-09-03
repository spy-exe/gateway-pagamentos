package br.com.ricardofigueiredo.gateway.webhook;

import br.com.ricardofigueiredo.gateway.usuario.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EndpointWebhookRepository extends JpaRepository<EndpointWebhook, Long> {

    List<EndpointWebhook> findByUsuarioOrderByCriadoEmDesc(Usuario usuario);

    List<EndpointWebhook> findByUsuarioAndAtivoTrue(Usuario usuario);

    Optional<EndpointWebhook> findByCodigoAndUsuario(String codigo, Usuario usuario);
}
