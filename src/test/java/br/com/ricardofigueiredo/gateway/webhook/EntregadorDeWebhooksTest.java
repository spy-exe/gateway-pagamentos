package br.com.ricardofigueiredo.gateway.webhook;

import br.com.ricardofigueiredo.gateway.usuario.Usuario;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EntregadorDeWebhooksTest {

    private final EntregaWebhookRepository repositorio = mock(EntregaWebhookRepository.class);
    private final HttpClient cliente = mock(HttpClient.class);

    @AfterEach
    void limparInterrupcao() {
        Thread.interrupted();
    }

    @Test
    @DisplayName("resposta 2xx conclui a entrega e manda os cabecalhos de rastreio")
    void entregaAceita() throws Exception {
        HttpResponse<Void> resposta = respostaCom(204);
        when(cliente.send(any(HttpRequest.class), qualquerTratador())).thenReturn(resposta);
        EntregaWebhook entrega = novaEntrega();

        entregador().entregar(entrega);

        assertThat(entrega.getSituacao()).isEqualTo(SituacaoDaEntrega.ENTREGUE);
        assertThat(entrega.getUltimoCodigoHttp()).isEqualTo(204);

        ArgumentCaptor<HttpRequest> captura = ArgumentCaptor.forClass(HttpRequest.class);
        verify(cliente).send(captura.capture(), qualquerTratador());
        HttpRequest requisicao = captura.getValue();
        assertThat(requisicao.timeout()).contains(Duration.ofSeconds(3));
        assertThat(requisicao.headers().firstValue("Aval-Evento"))
                .contains("cobranca.capturada");
        assertThat(requisicao.headers().firstValue("Aval-Entrega"))
                .contains(entrega.getCodigo());
        assertThat(requisicao.headers().firstValue(AssinaturaDeWebhook.CABECALHO)).isPresent();
    }

    @Test
    @DisplayName("resposta fora de 2xx volta para a fila com o codigo HTTP")
    void entregaRecusada() throws Exception {
        HttpResponse<Void> resposta = respostaCom(503);
        when(cliente.send(any(HttpRequest.class), qualquerTratador())).thenReturn(resposta);
        EntregaWebhook entrega = novaEntrega();

        entregador().entregar(entrega);

        assertThat(entrega.getSituacao()).isEqualTo(SituacaoDaEntrega.PENDENTE);
        assertThat(entrega.getUltimoCodigoHttp()).isEqualTo(503);
        assertThat(entrega.getUltimaFalha()).contains("503");
    }

    @Test
    @DisplayName("falha de rede fica registrada sem inventar codigo HTTP")
    void falhaDeRede() throws Exception {
        when(cliente.send(any(HttpRequest.class), qualquerTratador()))
                .thenThrow(new IOException("conexao recusada"));
        EntregaWebhook entrega = novaEntrega();

        entregador().entregar(entrega);

        assertThat(entrega.getSituacao()).isEqualTo(SituacaoDaEntrega.PENDENTE);
        assertThat(entrega.getUltimoCodigoHttp()).isNull();
        assertThat(entrega.getUltimaFalha()).contains("IOException", "conexao recusada");
    }

    @Test
    @DisplayName("interrupcao preserva o sinal da thread e devolve a entrega para a fila")
    void interrupcaoPreservaAThread() throws Exception {
        when(cliente.send(any(HttpRequest.class), qualquerTratador()))
                .thenThrow(new InterruptedException("parando"));
        EntregaWebhook entrega = novaEntrega();

        entregador().entregar(entrega);

        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        assertThat(entrega.getSituacao()).isEqualTo(SituacaoDaEntrega.PENDENTE);
        assertThat(entrega.getUltimaFalha()).isEqualTo("entrega interrompida");
    }

    private EntregadorDeWebhooks entregador() {
        return new EntregadorDeWebhooks(repositorio, 3, cliente);
    }

    private EntregaWebhook novaEntrega() {
        Usuario usuario = new Usuario("loja@exemplo.com", "hash", "Loja", "loja@exemplo.com", "NITEROI");
        EndpointWebhook endpoint = new EndpointWebhook(
                usuario, "https://93.184.216.34/eventos", "producao");
        return new EntregaWebhook(endpoint, "cobranca.capturada", "{\"codigo\":\"cob_1\"}");
    }

    @SuppressWarnings("unchecked")
    private HttpResponse.BodyHandler<Void> qualquerTratador() {
        return any(HttpResponse.BodyHandler.class);
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<Void> respostaCom(int codigo) {
        HttpResponse<Void> resposta = mock(HttpResponse.class);
        when(resposta.statusCode()).thenReturn(codigo);
        return resposta;
    }
}
