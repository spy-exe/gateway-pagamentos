package br.com.ricardofigueiredo.gateway.pix;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BrCodeTest {

    @Test
    @DisplayName("o CRC16 bate com o valor de referencia do arranjo CCITT-FALSE")
    void crcConfereComOValorDeReferencia() {
        // 0x29B1 sobre "123456789" e o valor de conferencia publicado do CRC-16/CCITT-FALSE
        assertThat(BrCode.crc16("123456789")).isEqualTo("29B1");
    }

    @Test
    @DisplayName("o payload segue a ordem e os identificadores do padrao")
    void seguirOPadrao() {
        String codigo = BrCode.gerar("loja@exemplo.com", "Mercearia Sao Jorge", "Rio de Janeiro",
                30000, "cob_abc123");

        assertThat(codigo).startsWith("000201");
        assertThat(codigo).contains("0014br.gov.bcb.pix");
        assertThat(codigo).contains("5303986");
        assertThat(codigo).contains("5406300.00");
        assertThat(codigo).contains("5802BR");
        assertThat(codigo).contains("MERCEARIA SAO JORGE");
        assertThat(codigo).contains("RIO DE JANEIRO");
        assertThat(codigo).matches(".*6304[0-9A-F]{4}$");
    }

    @Test
    @DisplayName("o CRC no fim do payload confere com o CRC do proprio payload")
    void crcFechaSobreOProprioPayload() {
        String codigo = BrCode.gerar("11999998888", "Padaria do Ricardo", "Niteroi", 4990, "cob_xyz");

        String semCrc = codigo.substring(0, codigo.length() - 4);
        String crcNoFim = codigo.substring(codigo.length() - 4);

        assertThat(BrCode.crc16(semCrc)).isEqualTo(crcNoFim);
    }

    @Test
    @DisplayName("cada campo declara o proprio tamanho em dois digitos")
    void campoDeclaraOTamanho() {
        assertThat(BrCode.campo("00", "01")).isEqualTo("00" + "02" + "01");
        assertThat(BrCode.campo("59", "AVAL")).isEqualTo("5904AVAL");
    }

    @Test
    @DisplayName("o valor sai com duas casas e ponto, nunca com virgula")
    void valorEmFormatoDoPadrao() {
        assertThat(BrCode.reais(30000)).isEqualTo("300.00");
        assertThat(BrCode.reais(5)).isEqualTo("0.05");
        assertThat(BrCode.reais(199)).isEqualTo("1.99");
        assertThat(BrCode.reais(0)).isEqualTo("0.00");
    }

    @Test
    @DisplayName("acento e simbolo somem, porque o padrao aceita apenas ASCII imprimivel")
    void higienizaOTexto() {
        assertThat(BrCode.higienizar("Açaí & Cia. Ltda", 25)).isEqualTo("ACAI CIA. LTDA");
        assertThat(BrCode.higienizar("São Gonçalo do Amarante", 15)).isEqualTo("SAO GONCALO DO");
        assertThat(BrCode.higienizar(null, 10)).isEmpty();
    }

    @Test
    @DisplayName("sem chave do recebedor o codigo nao e gerado")
    void chaveObrigatoria() {
        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> BrCode.gerar(null, "Loja", "Niteroi", 1000, "cob_1"))
                .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> BrCode.gerar("  ", "Loja", "Niteroi", 1000, "cob_1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("o txid fica em 25 caracteres alfanumericos, ou vira o coringa")
    void txidRespeitaOLimite() {
        assertThat(BrCode.txidValido("cob_abc123")).isEqualTo("cobabc123");
        assertThat(BrCode.txidValido("")).isEqualTo("***");
        assertThat(BrCode.txidValido("---")).isEqualTo("***");
        assertThat(BrCode.txidValido("cob_" + "f".repeat(40))).hasSize(25);
    }
}
