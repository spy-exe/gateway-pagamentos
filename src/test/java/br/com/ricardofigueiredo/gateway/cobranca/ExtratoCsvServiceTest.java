package br.com.ricardofigueiredo.gateway.cobranca;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExtratoCsvServiceTest {

    @Test
    @DisplayName("celula de CSV escapa separadores, aspas e quebras")
    void escapaConteudo() {
        assertThat(ExtratoCsvService.celula("Cafe; \"especial\"\nmoido"))
                .isEqualTo("\"Cafe; \"\"especial\"\"\nmoido\"");
        assertThat(ExtratoCsvService.celula(null)).isEmpty();
        assertThat(ExtratoCsvService.celula("")).isEmpty();
        assertThat(ExtratoCsvService.celula("Cafe \"especial\""))
                .isEqualTo("\"Cafe \"\"especial\"\"\"");
        assertThat(ExtratoCsvService.celula("Cafe\nmoido"))
                .isEqualTo("\"Cafe\nmoido\"");
        assertThat(ExtratoCsvService.celula("Cafe\rmoido"))
                .isEqualTo("\"Cafe\rmoido\"");
    }

    @Test
    @DisplayName("celula de CSV neutraliza formulas de planilha")
    void neutralizaFormula() {
        assertThat(ExtratoCsvService.celula("=IMPORTXML(A1)"))
                .isEqualTo("'=IMPORTXML(A1)");
        assertThat(ExtratoCsvService.celula("+5511999999999"))
                .isEqualTo("'+5511999999999");
    }

    @Test
    @DisplayName("extrato recusa valores negativos ou intervalo invertido antes de consultar")
    void validaIntervaloDeValores() {
        ExtratoCsvService servico = new ExtratoCsvService(null);
        ByteArrayOutputStream saida = new ByteArrayOutputStream();

        assertThatThrownBy(() -> servico.escrever(saida, null, null, null,
                null, null, null, -1L, null))
                .hasMessageContaining("negativos");
        assertThatThrownBy(() -> servico.escrever(saida, null, null, null,
                null, null, null, null, -1L))
                .hasMessageContaining("negativos");
        assertThatThrownBy(() -> servico.escrever(saida, null, null, null,
                null, null, null, 200L, 100L))
                .hasMessageContaining("minimo");
    }
}
