package br.com.ricardofigueiredo.gateway.cobranca.dto;

import br.com.ricardofigueiredo.gateway.cobranca.EventoCobranca;
import br.com.ricardofigueiredo.gateway.cobranca.StatusCobranca;

import java.time.Instant;

public record EventoResponse(String tipo, StatusCobranca statusAnterior, StatusCobranca statusNovo,
                             String detalhe, Instant criadoEm) {

    public static EventoResponse de(EventoCobranca evento) {
        return new EventoResponse(evento.getTipo(), evento.getStatusAnterior(), evento.getStatusNovo(),
                evento.getDetalhe(), evento.getCriadoEm());
    }
}
