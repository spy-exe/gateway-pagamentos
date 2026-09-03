package br.com.ricardofigueiredo.gateway.usuario.dto;

import br.com.ricardofigueiredo.gateway.usuario.Usuario;

import java.time.Instant;

public record UsuarioResponse(Long id, String email, String nomeEstabelecimento, String chavePix,
                              String cidade, Instant criadoEm) {

    public static UsuarioResponse de(Usuario usuario) {
        return new UsuarioResponse(usuario.getId(), usuario.getEmail(), usuario.getNomeEstabelecimento(),
                usuario.getChavePix(), usuario.getCidade(), usuario.getCriadoEm());
    }
}
