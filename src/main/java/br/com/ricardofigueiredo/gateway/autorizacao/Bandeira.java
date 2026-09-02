package br.com.ricardofigueiredo.gateway.autorizacao;

import java.util.List;

public enum Bandeira {

    VISA,
    MASTERCARD,
    ELO,
    AMEX,
    DESCONHECIDA;

    private static final List<String> PREFIXOS_ELO = List.of(
            "401178", "401179", "431274", "438935", "451416", "457393",
            "504175", "506699", "509", "627780", "636297", "636368", "650", "6516", "6550");

    /**
     * A ordem importa: varios prefixos de Elo cabem dentro das faixas de Visa e
     * Mastercard, entao a bandeira nacional precisa ser testada primeiro.
     */
    public static Bandeira identificar(String numero) {
        if (numero == null || numero.length() < 4) {
            return DESCONHECIDA;
        }
        if (PREFIXOS_ELO.stream().anyMatch(numero::startsWith)) {
            return ELO;
        }
        if (numero.startsWith("34") || numero.startsWith("37")) {
            return AMEX;
        }
        if (numero.startsWith("4")) {
            return VISA;
        }
        int doisPrimeiros = Integer.parseInt(numero.substring(0, 2));
        int quatroPrimeiros = Integer.parseInt(numero.substring(0, 4));
        if ((doisPrimeiros >= 51 && doisPrimeiros <= 55)
                || (quatroPrimeiros >= 2221 && quatroPrimeiros <= 2720)) {
            return MASTERCARD;
        }
        return DESCONHECIDA;
    }
}
