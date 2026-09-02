package br.com.ricardofigueiredo.gateway.autorizacao;

import br.com.ricardofigueiredo.gateway.comum.excecao.RegraDeNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CartaoTokenizadoTest {

    @Test
    @DisplayName("guarda apenas bin e ultimos quatro digitos do cartao")
    void naoRetemONumeroCompleto() {
        CartaoTokenizado cartao = CartaoTokenizado.tokenizar("4111111111111111", 12, 2030, "Ricardo Figueiredo");

        assertThat(cartao.getBin()).isEqualTo("411111");
        assertThat(cartao.getUltimosQuatro()).isEqualTo("1111");
        assertThat(cartao.toString()).doesNotContain("4111111111111111");
    }

    @Test
    @DisplayName("aceita numero digitado com espacos e hifens")
    void aceitaSeparadores() {
        CartaoTokenizado cartao = CartaoTokenizado.tokenizar("4111 1111-1111 1111", 1, 2031, "Ricardo Figueiredo");

        assertThat(cartao.getUltimosQuatro()).isEqualTo("1111");
    }

    @Test
    @DisplayName("recusa numero que nao passa no digito verificador")
    void recusaLuhnInvalido() {
        assertThatThrownBy(() -> CartaoTokenizado.tokenizar("4111111111111112", 12, 2030, "Ricardo"))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("digito verificador");
    }

    @Test
    @DisplayName("recusa numero com quantidade de digitos fora da faixa")
    void recusaTamanhoInvalido() {
        assertThatThrownBy(() -> CartaoTokenizado.tokenizar("41111", 12, 2030, "Ricardo"))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("13 a 19 digitos");
    }

    @ParameterizedTest(name = "{0} deve ser identificado como {1}")
    @CsvSource({
            "4111111111111111, VISA",
            "5555555555554444, MASTERCARD",
            "378282246310005,  AMEX",
            "5099990000000003, ELO",
            "9999000000000004, DESCONHECIDA"
    })
    @DisplayName("identifica a bandeira pelo prefixo do numero")
    void identificaBandeira(String numero, Bandeira esperada) {
        assertThat(CartaoTokenizado.tokenizar(numero, 6, 2030, "Ricardo").getBandeira()).isEqualTo(esperada);
    }

    @Test
    @DisplayName("considera vencido o cartao com validade anterior ao mes de referencia")
    void detectaVencimento() {
        CartaoTokenizado cartao = CartaoTokenizado.tokenizar("4111111111111111", 3, 2026, "Ricardo");

        assertThat(cartao.venceuAte(YearMonth.of(2026, 4))).isTrue();
        assertThat(cartao.venceuAte(YearMonth.of(2026, 3))).isFalse();
        assertThat(cartao.venceuAte(YearMonth.of(2026, 2))).isFalse();
    }
}
