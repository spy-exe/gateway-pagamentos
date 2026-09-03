package br.com.ricardofigueiredo.gateway.linkpagamento.dto;

import br.com.ricardofigueiredo.gateway.cobranca.MetodoPagamento;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CriarLinkPagamentoRequest(
        @NotBlank(message = "informe o que esta sendo vendido")
        @Size(max = 140, message = "a descricao pode ter no maximo 140 caracteres")
        String descricao,

        @NotNull(message = "informe o valor em centavos")
        @Positive(message = "o valor deve ser maior que zero")
        Long valorEmCentavos,

        @NotNull(message = "informe o metodo de pagamento")
        MetodoPagamento metodo,

        @Min(value = 1, message = "o parcelamento vai de 1 a 12 vezes")
        @Max(value = 12, message = "o parcelamento vai de 1 a 12 vezes")
        Integer parcelasMaximas,

        @Positive(message = "o limite de usos deve ser maior que zero")
        Integer limiteDeUsos,

        @Future(message = "a validade precisa estar no futuro")
        Instant expiraEm) {

    public int parcelasMaximasOuUma() {
        return parcelasMaximas == null ? 1 : parcelasMaximas;
    }
}
