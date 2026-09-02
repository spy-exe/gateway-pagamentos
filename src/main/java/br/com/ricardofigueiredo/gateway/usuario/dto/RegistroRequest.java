package br.com.ricardofigueiredo.gateway.usuario.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegistroRequest(

        @NotBlank(message = "informe o e-mail")
        @Email(message = "e-mail em formato invalido")
        @Size(max = 160, message = "e-mail pode ter no maximo 160 caracteres")
        String email,

        @NotBlank(message = "informe a senha")
        @Size(min = 8, max = 64, message = "a senha deve ter entre 8 e 64 caracteres")
        String senha,

        @NotBlank(message = "informe o nome do estabelecimento")
        @Size(max = 120, message = "nome pode ter no maximo 120 caracteres")
        String nomeEstabelecimento) {
}
