package br.com.ricardofigueiredo.gateway.webhook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CriarEndpointRequest(

        @NotBlank(message = "informe a URL que vai receber os eventos")
        @Pattern(regexp = "https?://.+", message = "a URL precisa comecar com http ou https")
        @Size(max = 300, message = "a URL pode ter no maximo 300 caracteres")
        String url,

        @Size(max = 120, message = "a descricao pode ter no maximo 120 caracteres")
        String descricao) {
}
