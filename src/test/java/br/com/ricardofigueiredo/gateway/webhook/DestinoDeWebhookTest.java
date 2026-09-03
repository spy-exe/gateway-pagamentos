package br.com.ricardofigueiredo.gateway.webhook;

import br.com.ricardofigueiredo.gateway.comum.excecao.RegraDeNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DestinoDeWebhookTest {

    @ParameterizedTest(name = "{0} nao pode receber webhook")
    @ValueSource(strings = {
            "http://127.0.0.1/eventos",
            "http://localhost:8080/eventos",
            "http://10.10.1.253:8006/eventos",
            "http://192.168.0.10/eventos",
            "http://172.16.4.9/eventos",
            "http://169.254.169.254/latest/meta-data/",
            "http://0.0.0.0/eventos",
            "http://100.100.0.1/eventos",
            "http://[::1]/eventos"
    })
    @DisplayName("endereco interno ou reservado escrito na mao e recusado no cadastro")
    void enderecoInternoNaoPassa(String url) {
        assertThatThrownBy(() -> DestinoDeWebhook.exigirFormaValida(url))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @ParameterizedTest(name = "{0} tambem nao serve")
    @ValueSource(strings = {
            "ftp://exemplo.com/eventos",
            "file:///etc/passwd",
            "gopher://exemplo.com",
            "https://",
            "nao e uma url"
    })
    @DisplayName("esquema fora de http e https e recusado")
    void esquemaInvalidoNaoPassa(String url) {
        assertThatThrownBy(() -> DestinoDeWebhook.exigirFormaValida(url))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    @DisplayName("IP publico escrito na mao passa")
    void ipPublicoPassa() {
        assertThatCode(() -> DestinoDeWebhook.exigirFormaValida("https://93.184.216.34/eventos"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("nome de dominio passa no cadastro, porque a resolucao fica para a hora do envio")
    void nomeDeDominioPassaNoCadastro() {
        assertThatCode(() -> DestinoDeWebhook.exigirFormaValida("https://exemplo.com.br/eventos"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("host que nao resolve e recusado na hora do envio")
    void hostQueNaoResolveNaoEnvia() {
        assertThatThrownBy(() -> DestinoDeWebhook
                .exigirDestinoPublico("https://este-host-nao-existe-em-lugar-nenhum.invalid/eventos"))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("nao resolve");
    }

    @Test
    @DisplayName("no envio o endereco interno continua barrado")
    void envioParaEnderecoInternoNaoPassa() {
        assertThatThrownBy(() -> DestinoDeWebhook.exigirDestinoPublico("http://127.0.0.1/eventos"))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    @DisplayName("as faixas reservadas conferidas na mao batem")
    void faixasReservadas() throws Exception {
        assertThat(DestinoDeWebhook.reservado(InetAddress.getByName("198.18.0.1"))).isTrue();
        assertThat(DestinoDeWebhook.reservado(InetAddress.getByName("198.19.5.5"))).isTrue();
        assertThat(DestinoDeWebhook.reservado(InetAddress.getByName("192.0.2.1"))).isTrue();
        assertThat(DestinoDeWebhook.reservado(InetAddress.getByName("240.0.0.1"))).isTrue();
        assertThat(DestinoDeWebhook.reservado(InetAddress.getByName("fc00::1"))).isTrue();
        assertThat(DestinoDeWebhook.reservado(InetAddress.getByName("8.8.8.8"))).isFalse();
        assertThat(DestinoDeWebhook.reservado(InetAddress.getByName("2001:4860:4860::8888"))).isFalse();
    }
}
