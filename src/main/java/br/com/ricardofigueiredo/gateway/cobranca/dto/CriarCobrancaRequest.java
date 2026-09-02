package br.com.ricardofigueiredo.gateway.cobranca.dto;

import br.com.ricardofigueiredo.gateway.cobranca.MetodoPagamento;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
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

        @Valid
        DadosCartaoRequest cartao) {

    public boolean capturaAutomaticaOuPadrao() {
        return capturaAutomatica == null || capturaAutomatica;
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
