package br.com.ricardofigueiredo.gateway.cobranca.dto;

import java.time.LocalDate;

/** Uma barra do grafico de volume: quanto entrou e quantas transacoes houve. */
public record DiaDoMovimento(LocalDate dia, long capturadoEmCentavos, long transacoes, long recusadas) {
}
