package br.com.ricardofigueiredo.gateway.linkpagamento.dto;

import br.com.ricardofigueiredo.gateway.cobranca.dto.DadosCartaoRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record FinalizarLinkPagamentoRequest(
        @Min(value = 1, message = "o parcelamento comeca em uma vez")
        @Max(value = 12, message = "o parcelamento vai ate doze vezes")
        Integer parcelas,

        @Valid
        DadosCartaoRequest cartao) {

    public int parcelasOuUma() {
        return parcelas == null ? 1 : parcelas;
    }
}
