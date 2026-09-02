package br.com.ricardofigueiredo.gateway.cobranca;

import br.com.ricardofigueiredo.gateway.autorizacao.CartaoTokenizado;
import br.com.ricardofigueiredo.gateway.autorizacao.MotivoRecusa;
import br.com.ricardofigueiredo.gateway.autorizacao.ResultadoAutorizacao;
import br.com.ricardofigueiredo.gateway.comum.excecao.RegraDeNegocioException;
import br.com.ricardofigueiredo.gateway.usuario.Usuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CobrancaTest {

    private static final long VALOR = 50_000L;

    @Test
    @DisplayName("cobranca aprovada com captura automatica ja nasce capturada")
    void nasceCapturada() {
        Cobranca cobranca = aprovada(true);

        assertThat(cobranca.getStatus()).isEqualTo(StatusCobranca.CAPTURADA);
        assertThat(cobranca.getCodigoAutorizacao()).isEqualTo("A1B2C3");
        assertThat(cobranca.getCodigo()).startsWith("cob_");
    }

    @Test
    @DisplayName("cobranca aprovada sem captura automatica fica aguardando captura")
    void nasceAutorizada() {
        assertThat(aprovada(false).getStatus()).isEqualTo(StatusCobranca.AUTORIZADA);
    }

    @Test
    @DisplayName("cobranca recusada guarda o motivo e nao gera codigo de autorizacao")
    void nasceRecusada() {
        Cobranca cobranca = recusada();

        assertThat(cobranca.getStatus()).isEqualTo(StatusCobranca.RECUSADA);
        assertThat(cobranca.getMotivoRecusa()).isEqualTo(MotivoRecusa.SALDO_INSUFICIENTE);
        assertThat(cobranca.getCodigoAutorizacao()).isNull();
    }

    @Test
    @DisplayName("nao permite capturar duas vezes")
    void naoCapturaDuasVezes() {
        Cobranca cobranca = aprovada(false);
        cobranca.capturar();

        assertThatThrownBy(cobranca::capturar)
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("CAPTURADA");
    }

    @Test
    @DisplayName("nao permite cancelar depois da captura")
    void naoCancelaDepoisDaCaptura() {
        Cobranca cobranca = aprovada(true);

        assertThatThrownBy(cobranca::cancelar).isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    @DisplayName("nao permite capturar cobranca recusada")
    void naoCapturaRecusada() {
        assertThatThrownBy(() -> recusada().capturar()).isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    @DisplayName("estorno parcial reduz o saldo e muda o status")
    void estornoParcial() {
        Cobranca cobranca = aprovada(true);
        cobranca.registrarEstorno(20_000L);

        assertThat(cobranca.getStatus()).isEqualTo(StatusCobranca.PARCIALMENTE_ESTORNADA);
        assertThat(cobranca.getValorEstornadoEmCentavos()).isEqualTo(20_000L);
        assertThat(cobranca.saldoEstornavelEmCentavos()).isEqualTo(30_000L);
    }

    @Test
    @DisplayName("estornos parciais somados ate o total encerram a cobranca")
    void estornosSucessivosFecham() {
        Cobranca cobranca = aprovada(true);
        cobranca.registrarEstorno(20_000L);
        cobranca.registrarEstorno(30_000L);

        assertThat(cobranca.getStatus()).isEqualTo(StatusCobranca.ESTORNADA);
        assertThat(cobranca.saldoEstornavelEmCentavos()).isZero();
    }

    @Test
    @DisplayName("nao permite estornar mais do que o saldo disponivel")
    void naoEstornaAlemDoSaldo() {
        Cobranca cobranca = aprovada(true);
        cobranca.registrarEstorno(30_000L);

        assertThatThrownBy(() -> cobranca.registrarEstorno(20_001L))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("excede o saldo");
    }

    @Test
    @DisplayName("nao permite estornar cobranca que ainda nao foi capturada")
    void naoEstornaSemCaptura() {
        assertThatThrownBy(() -> aprovada(false).registrarEstorno(1_000L))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("capturada");
    }

    @Test
    @DisplayName("nao permite estorno de valor zero ou negativo")
    void naoEstornaValorInvalido() {
        Cobranca cobranca = aprovada(true);

        assertThatThrownBy(() -> cobranca.registrarEstorno(0L)).isInstanceOf(RegraDeNegocioException.class);
        assertThatThrownBy(() -> cobranca.registrarEstorno(-100L)).isInstanceOf(RegraDeNegocioException.class);
    }

    private Cobranca aprovada(boolean capturaAutomatica) {
        return new Cobranca(usuario(), VALOR, "Pedido 1042", MetodoPagamento.CARTAO_CREDITO,
                capturaAutomatica, cartao(), null, ResultadoAutorizacao.aprovada("A1B2C3"));
    }

    private Cobranca recusada() {
        return new Cobranca(usuario(), VALOR, "Pedido 1043", MetodoPagamento.CARTAO_CREDITO,
                true, cartao(), null, ResultadoAutorizacao.recusada(MotivoRecusa.SALDO_INSUFICIENTE));
    }

    private Usuario usuario() {
        return new Usuario("loja@exemplo.com", "hash", "Loja de Teste");
    }

    private CartaoTokenizado cartao() {
        return CartaoTokenizado.tokenizar("4111111111111111", 12, 2030, "Ricardo Figueiredo");
    }
}
