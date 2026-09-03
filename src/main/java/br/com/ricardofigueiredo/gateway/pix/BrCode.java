package br.com.ricardofigueiredo.gateway.pix;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Monta o "copia e cola" do Pix no formato EMV QRCPS-MPM, que e o padrao que o
 * Banco Central adotou no Manual de Padroes para Iniciacao do Pix.
 *
 * O payload e uma sequencia de campos no formato ID + tamanho + valor, onde o
 * tamanho tem sempre dois digitos. Alguns campos guardam outros campos dentro
 * de si, como o 26, que carrega o dominio do arranjo e a chave do recebedor. O
 * ultimo campo e sempre o 63, com o CRC16 calculado sobre tudo o que veio
 * antes, incluindo o proprio "6304".
 *
 * O codigo gerado aqui e estrutural e valido, mas aponta para uma chave de
 * demonstracao: nenhum banco liquida nada a partir dele.
 */
public final class BrCode {

    private static final String DOMINIO_DO_ARRANJO = "br.gov.bcb.pix";
    private static final String INDICADOR_DE_FORMATO = "01";
    private static final String CODIGO_DO_PAIS = "BR";
    private static final String MOEDA_REAL = "986";
    private static final String CATEGORIA_NAO_INFORMADA = "0000";

    private BrCode() {
    }

    /**
     * @param chave        chave Pix do recebedor
     * @param nome         nome do recebedor, cortado em 25 caracteres pelo padrao
     * @param cidade       cidade do recebedor, cortada em 15 caracteres
     * @param valorEmCentavos valor da cobranca
     * @param identificador identificador da transacao, o txid, ate 25 caracteres
     */
    public static String gerar(String chave, String nome, String cidade, long valorEmCentavos,
                               String identificador) {
        if (chave == null || chave.isBlank()) {
            throw new IllegalArgumentException("a chave Pix do recebedor e obrigatoria");
        }

        String recebedor = higienizar(nome, 25);
        String municipio = higienizar(cidade, 15);
        String txid = txidValido(identificador);

        String contaDoRecebedor = campo("00", DOMINIO_DO_ARRANJO) + campo("01", chave);

        StringBuilder payload = new StringBuilder()
                .append(campo("00", INDICADOR_DE_FORMATO))
                .append(campo("26", contaDoRecebedor))
                .append(campo("52", CATEGORIA_NAO_INFORMADA))
                .append(campo("53", MOEDA_REAL))
                .append(campo("54", reais(valorEmCentavos)))
                .append(campo("58", CODIGO_DO_PAIS))
                .append(campo("59", recebedor))
                .append(campo("60", municipio))
                .append(campo("62", campo("05", txid)))
                .append("6304");

        return payload + crc16(payload.toString());
    }

    static String campo(String identificador, String valor) {
        return identificador + String.format(Locale.ROOT, "%02d", valor.length()) + valor;
    }

    static String reais(long valorEmCentavos) {
        return String.format(Locale.ROOT, "%d.%02d", valorEmCentavos / 100, valorEmCentavos % 100);
    }

    /**
     * CRC16 no arranjo CCITT-FALSE: polinomio 0x1021 e registrador iniciado em
     * 0xFFFF, que e o que o manual do Pix exige.
     */
    static String crc16(String entrada) {
        int registrador = 0xFFFF;

        for (byte octeto : entrada.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            registrador ^= (octeto & 0xFF) << 8;
            for (int bit = 0; bit < 8; bit++) {
                boolean sobrou = (registrador & 0x8000) != 0;
                registrador <<= 1;
                if (sobrou) {
                    registrador ^= 0x1021;
                }
                registrador &= 0xFFFF;
            }
        }

        return String.format(Locale.ROOT, "%04X", registrador);
    }

    /**
     * O padrao aceita apenas caracteres imprimiveis do ASCII, entao acento vira
     * a letra sem acento e o resto e descartado.
     */
    static String higienizar(String texto, int limite) {
        String semAcento = Normalizer.normalize(texto == null ? "" : texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^A-Za-z0-9 .-]", "")
                .replaceAll("\\s+", " ")
                .trim()
                .toUpperCase(Locale.ROOT);

        return semAcento.length() <= limite ? semAcento : semAcento.substring(0, limite).trim();
    }

    static String txidValido(String identificador) {
        String limpo = (identificador == null ? "" : identificador).replaceAll("[^A-Za-z0-9]", "");
        if (limpo.isEmpty()) {
            return "***";
        }
        return limpo.length() <= 25 ? limpo : limpo.substring(limpo.length() - 25);
    }
}
