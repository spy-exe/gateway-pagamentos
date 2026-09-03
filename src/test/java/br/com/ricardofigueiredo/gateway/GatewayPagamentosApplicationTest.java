package br.com.ricardofigueiredo.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class GatewayPagamentosApplicationTest {

    @Test
    @DisplayName("a aplicacao sobe pelo metodo main, com porta sorteada pelo sistema")
    void sobePeloMain() {
        assertThatCode(() -> GatewayPagamentosApplication.main(new String[]{
                "--server.port=0",
                "--gateway.webhook.intervalo-ms=3600000"
        })).doesNotThrowAnyException();
    }
}
