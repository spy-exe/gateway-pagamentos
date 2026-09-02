package br.com.ricardofigueiredo.gateway.cobranca.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DadosCartaoRequest(

        @NotBlank(message = "informe o numero do cartao")
        @Pattern(regexp = "[0-9\\s.-]{13,25}", message = "o numero do cartao deve conter apenas digitos")
        String numero,

        @NotNull(message = "informe o mes de validade")
        @Min(value = 1, message = "mes de validade deve estar entre 1 e 12")
        @Max(value = 12, message = "mes de validade deve estar entre 1 e 12")
        Integer validadeMes,

        @NotNull(message = "informe o ano de validade")
        @Min(value = 2000, message = "ano de validade invalido")
        @Max(value = 2099, message = "ano de validade invalido")
        Integer validadeAno,

        @NotBlank(message = "informe o nome impresso no cartao")
        @Size(max = 120, message = "nome do portador pode ter no maximo 120 caracteres")
        String nomePortador) {
}
