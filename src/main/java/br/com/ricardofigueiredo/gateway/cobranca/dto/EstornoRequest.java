package br.com.ricardofigueiredo.gateway.cobranca.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Valor nulo significa estorno total do saldo ainda disponivel.
 */
public record EstornoRequest(

        @Positive(message = "o valor do estorno deve ser maior que zero")
        Long valorEmCentavos,

        @Size(max = 140, message = "o motivo pode ter no maximo 140 caracteres")
        String motivo) {
}
