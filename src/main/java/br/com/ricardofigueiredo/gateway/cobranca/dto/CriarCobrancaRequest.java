package br.com.ricardofigueiredo.gateway.cobranca.dto;

import br.com.ricardofigueiredo.gateway.cobranca.MetodoPagamento;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CriarCobrancaRequest(

        @NotNull(message = "informe o valor em centavos")
        @Positive(message = "o valor deve ser maior que zero")
        Long valorEmCentavos,

        @NotBlank(message = "informe a descricao da cobranca")
        @Size(max = 140, message = "a descricao pode ter no maximo 140 caracteres")
        String descricao,

        @NotNull(message = "informe o metodo de pagamento")
        MetodoPagamento metodo,

        Boolean capturaAutomatica,

        @Min(value = 1, message = "o parcelamento vai de 1 a 12 vezes")
        @Max(value = 12, message = "o parcelamento vai de 1 a 12 vezes")
        Integer parcelas,

        @Valid
        DadosCartaoRequest cartao) {

    public boolean capturaAutomaticaOuPadrao() {
        return capturaAutomatica == null || capturaAutomatica;
    }

    public int parcelasOuUma() {
        return parcelas == null ? 1 : parcelas;
    }

    @JsonIgnore
    @AssertTrue(message = "cartao e obrigatorio para pagamentos com cartao e nao deve ser enviado para Pix")
    public boolean isCartaoCoerenteComOMetodo() {
        if (metodo == null) {
            return true;
        }
        return metodo.exigeCartao() == (cartao != null);
    }
}
