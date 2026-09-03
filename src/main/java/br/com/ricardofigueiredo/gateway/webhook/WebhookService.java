package br.com.ricardofigueiredo.gateway.webhook;

import br.com.ricardofigueiredo.gateway.comum.excecao.RecursoNaoEncontradoException;
import br.com.ricardofigueiredo.gateway.comum.excecao.RegraDeNegocioException;
import br.com.ricardofigueiredo.gateway.usuario.Usuario;
import br.com.ricardofigueiredo.gateway.webhook.dto.CriarEndpointRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class WebhookService {

    /** Poucos endpoints por conta ja atendem, e o limite evita fila infinita. */
    private static final int LIMITE_POR_ESTABELECIMENTO = 5;

    private final EndpointWebhookRepository endpointRepository;
    private final EntregaWebhookRepository entregaRepository;

    public WebhookService(EndpointWebhookRepository endpointRepository,
                          EntregaWebhookRepository entregaRepository) {
        this.endpointRepository = endpointRepository;
        this.entregaRepository = entregaRepository;
    }

    @Transactional
    public EndpointWebhook cadastrar(Usuario usuario, CriarEndpointRequest requisicao) {
        List<EndpointWebhook> existentes = endpointRepository.findByUsuarioOrderByCriadoEmDesc(usuario);

        if (existentes.size() >= LIMITE_POR_ESTABELECIMENTO) {
            throw new RegraDeNegocioException(
                    "Cada estabelecimento pode manter no maximo " + LIMITE_POR_ESTABELECIMENTO + " endpoints.");
        }

        String url = requisicao.url().trim();
        DestinoDeWebhook.exigirFormaValida(url);

        return endpointRepository.save(new EndpointWebhook(usuario, url, requisicao.descricao()));
    }

    @Transactional(readOnly = true)
    public List<EndpointWebhook> listar(Usuario usuario) {
        return endpointRepository.findByUsuarioOrderByCriadoEmDesc(usuario);
    }

    @Transactional
    public EndpointWebhook alternar(Usuario usuario, String codigo, boolean ativo) {
        EndpointWebhook endpoint = buscar(usuario, codigo);
        if (ativo) {
            endpoint.ativar();
        } else {
            endpoint.desativar();
        }
        return endpoint;
    }

    @Transactional
    public void remover(Usuario usuario, String codigo) {
        EndpointWebhook endpoint = buscar(usuario, codigo);
        entregaRepository.deleteAll(
                entregaRepository.findByEndpointOrderByCriadoEmDesc(endpoint, Pageable.unpaged()).getContent());
        endpointRepository.delete(endpoint);
    }

    @Transactional(readOnly = true)
    public Page<EntregaWebhook> entregas(Usuario usuario, String codigo, Pageable paginacao) {
        return entregaRepository.findByEndpointOrderByCriadoEmDesc(buscar(usuario, codigo), paginacao);
    }

    @Transactional
    public EntregaWebhook reenviar(Usuario usuario, String codigoDaEntrega) {
        EntregaWebhook entrega = entregaRepository.findByCodigo(codigoDaEntrega)
                .filter(candidata -> candidata.getEndpoint().getUsuario().getId().equals(usuario.getId()))
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Nenhuma entrega encontrada com o codigo " + codigoDaEntrega + "."));

        entrega.reenfileirar();
        return entrega;
    }

    private EndpointWebhook buscar(Usuario usuario, String codigo) {
        return endpointRepository.findByCodigoAndUsuario(codigo, usuario)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Nenhum endpoint encontrado com o codigo " + codigo + "."));
    }
}
