package br.com.ricardofigueiredo.gateway.webhook;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AssinaturaDeWebhookTest {

    private static final String SEGREDO = "whsec_2f8a1c7b4e6d9a0f3b5c8e1d7a4f6b2c";
    private static final String CORPO = "{\"evento\":\"cobranca.capturada\",\"dados\":{\"codigo\":\"cob_1\"}}";
    private static final Instant AGORA = Instant.parse("2026-09-03T12:00:00Z");

    @Test
    @DisplayName("o cabecalho sai com o instante e a assinatura")
    void formatoDoCabecalho() {
        String cabecalho = AssinaturaDeWebhook.gerar(SEGREDO, CORPO, AGORA);

        assertThat(cabecalho).startsWith("t=" + AGORA.getEpochSecond() + ",v1=");
        assertThat(cabecalho).matches("t=\\d+,v1=[0-9a-f]{64}");
    }

    @Test
    @DisplayName("a assinatura emitida confere do outro lado")
    void assinaturaConfere() {
        String cabecalho = AssinaturaDeWebhook.gerar(SEGREDO, CORPO, AGORA);

        assertThat(AssinaturaDeWebhook.confere(SEGREDO, CORPO, cabecalho, AGORA)).isTrue();
    }

    @Test
    @DisplayName("mexer em um caractere do corpo derruba a conferencia")
    void corpoAlteradoNaoConfere() {
        String cabecalho = AssinaturaDeWebhook.gerar(SEGREDO, CORPO, AGORA);
        String adulterado = CORPO.replace("cob_1", "cob_2");

        assertThat(AssinaturaDeWebhook.confere(SEGREDO, adulterado, cabecalho, AGORA)).isFalse();
    }

    @Test
    @DisplayName("segredo diferente nao confere")
    void segredoErradoNaoConfere() {
        String cabecalho = AssinaturaDeWebhook.gerar(SEGREDO, CORPO, AGORA);

        assertThat(AssinaturaDeWebhook.confere("whsec_outro", CORPO, cabecalho, AGORA)).isFalse();
    }

    @Test
    @DisplayName("assinatura velha e recusada, que e a defesa contra reapresentacao")
    void assinaturaForaDaJanelaNaoConfere() {
        String cabecalho = AssinaturaDeWebhook.gerar(SEGREDO, CORPO, AGORA);
        Instant seisMinutosDepois = AGORA.plusSeconds(360);

        assertThat(AssinaturaDeWebhook.confere(SEGREDO, CORPO, cabecalho, seisMinutosDepois)).isFalse();
    }

    @Test
    @DisplayName("dentro da janela de tolerancia a assinatura continua valendo")
    void assinaturaDentroDaJanelaConfere() {
        String cabecalho = AssinaturaDeWebhook.gerar(SEGREDO, CORPO, AGORA);

        assertThat(AssinaturaDeWebhook.confere(SEGREDO, CORPO, cabecalho, AGORA.plusSeconds(240))).isTrue();
    }

    @Test
    @DisplayName("cabecalho ausente ou malformado nao confere")
    void cabecalhoInvalidoNaoConfere() {
        assertThat(AssinaturaDeWebhook.confere(SEGREDO, CORPO, null, AGORA)).isFalse();
        assertThat(AssinaturaDeWebhook.confere(SEGREDO, CORPO, "", AGORA)).isFalse();
        assertThat(AssinaturaDeWebhook.confere(SEGREDO, CORPO, "v1=abc", AGORA)).isFalse();
        assertThat(AssinaturaDeWebhook.confere(SEGREDO, CORPO, "t=123", AGORA)).isFalse();
        assertThat(AssinaturaDeWebhook.confere(SEGREDO, CORPO, "t=ontem,v1=abc", AGORA)).isFalse();
        assertThat(AssinaturaDeWebhook.confere(SEGREDO, CORPO, "solto", AGORA)).isFalse();
    }
}
