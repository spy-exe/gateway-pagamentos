package br.com.ricardofigueiredo.gateway.cobranca.dto;

/**
 * Fechamento do periodo. Os totais sao somados pelo banco, e nao pela pagina
 * carregada no navegador, senao o numero mudaria conforme o tamanho da pagina.
 */
public record ResumoResponse(
        long capturadoEmCentavos,
        long estornadoEmCentavos,
        long liquidoEmCentavos,
        long autorizadoEmCentavos,
        long recusadas,
        long total,
        double taxaDeAprovacao) {

    public static ResumoResponse de(Long capturado, Long estornado, Long autorizado, Long recusadas, Long total) {
        long capturadoSeguro = capturado == null ? 0L : capturado;
        long estornadoSeguro = estornado == null ? 0L : estornado;
        long autorizadoSeguro = autorizado == null ? 0L : autorizado;
        long recusadasSeguro = recusadas == null ? 0L : recusadas;
        long totalSeguro = total == null ? 0L : total;

        double taxa = totalSeguro == 0 ? 0.0
                : Math.round(((totalSeguro - recusadasSeguro) * 10000.0) / totalSeguro) / 100.0;

        return new ResumoResponse(capturadoSeguro, estornadoSeguro, capturadoSeguro - estornadoSeguro,
                autorizadoSeguro, recusadasSeguro, totalSeguro, taxa);
    }
}
