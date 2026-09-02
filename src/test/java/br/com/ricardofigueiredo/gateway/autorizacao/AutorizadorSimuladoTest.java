package br.com.ricardofigueiredo.gateway.autorizacao;

import br.com.ricardofigueiredo.gateway.cobranca.MetodoPagamento;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class AutorizadorSimuladoTest {

    private static final long LIMITE = 1_000_000L;
    private static final Clock RELOGIO_FIXO =
            Clock.fixed(Instant.parse("2026-06-15T12:00:00Z"), ZoneOffset.UTC);

    private AutorizadorSimulado autorizador;

    @BeforeEach
    void prepararAutorizador() {
        autorizador = new AutorizadorSimulado(LIMITE, RELOGIO_FIXO);
    }

    @Test
    @DisplayName("aprova cartao valido dentro do limite")
    void aprovaCartaoValido() {
        ResultadoAutorizacao resultado = autorizador.autorizar(
                MetodoPagamento.CARTAO_CREDITO, 15_000L, cartao("4111111111111111"));

        assertThat(resultado.aprovada()).isTrue();
        assertThat(resultado.motivo()).isNull();
        assertThat(resultado.codigoAutorizacao()).hasSize(6);
    }

    @Test
    @DisplayName("gera sempre o mesmo codigo de autorizacao para a mesma entrada")
    void codigoEDeterministico() {
        ResultadoAutorizacao primeira = autorizador.autorizar(
                MetodoPagamento.CARTAO_CREDITO, 15_000L, cartao("4111111111111111"));
        ResultadoAutorizacao segunda = autorizador.autorizar(
                MetodoPagamento.CARTAO_CREDITO, 15_000L, cartao("4111111111111111"));

        assertThat(primeira.codigoAutorizacao()).isEqualTo(segunda.codigoAutorizacao());
    }

    @Test
    @DisplayName("aprova Pix sem exigir cartao")
    void aprovaPix() {
        ResultadoAutorizacao resultado = autorizador.autorizar(MetodoPagamento.PIX, 25_000L, null);

        assertThat(resultado.aprovada()).isTrue();
    }

    @Test
    @DisplayName("recusa valor acima do limite por transacao, inclusive no Pix")
    void recusaAcimaDoLimite() {
        assertThat(autorizador.autorizar(MetodoPagamento.PIX, LIMITE + 1, null).motivo())
                .isEqualTo(MotivoRecusa.LIMITE_EXCEDIDO);
        assertThat(autorizador.autorizar(MetodoPagamento.CARTAO_CREDITO, LIMITE + 1, cartao("4111111111111111"))
                .motivo()).isEqualTo(MotivoRecusa.LIMITE_EXCEDIDO);
    }

    @Test
    @DisplayName("recusa cartao terminado em 0000 como bloqueado")
    void recusaCartaoBloqueado() {
        ResultadoAutorizacao resultado = autorizador.autorizar(
                MetodoPagamento.CARTAO_CREDITO, 10_000L, cartao("4111000000080000"));

        assertThat(resultado.aprovada()).isFalse();
        assertThat(resultado.motivo()).isEqualTo(MotivoRecusa.CARTAO_BLOQUEADO);
    }

    @Test
    @DisplayName("recusa cartao terminado em 0001 por saldo insuficiente")
    void recusaSaldoInsuficiente() {
        assertThat(autorizador.autorizar(MetodoPagamento.CARTAO_CREDITO, 10_000L, cartao("4111000000070001"))
                .motivo()).isEqualTo(MotivoRecusa.SALDO_INSUFICIENTE);
    }

    @Test
    @DisplayName("recusa cartao vencido usando o relogio injetado")
    void recusaCartaoVencido() {
        CartaoTokenizado vencido =
                CartaoTokenizado.tokenizar("4111111111111111", 5, 2026, "Ricardo Figueiredo");

        assertThat(autorizador.autorizar(MetodoPagamento.CARTAO_CREDITO, 10_000L, vencido).motivo())
                .isEqualTo(MotivoRecusa.CARTAO_EXPIRADO);
    }

    @Test
    @DisplayName("recusa bandeira que o gateway nao reconhece")
    void recusaBandeiraDesconhecida() {
        assertThat(autorizador.autorizar(MetodoPagamento.CARTAO_DEBITO, 10_000L, cartao("9999000000000004"))
                .motivo()).isEqualTo(MotivoRecusa.BANDEIRA_NAO_SUPORTADA);
    }

    private CartaoTokenizado cartao(String numero) {
        return CartaoTokenizado.tokenizar(numero, 12, 2030, "Ricardo Figueiredo");
    }
}
