package br.com.ricardofigueiredo.gateway.autorizacao;

public record ResultadoAutorizacao(boolean aprovada, MotivoRecusa motivo, String codigoAutorizacao) {

    public static ResultadoAutorizacao aprovada(String codigoAutorizacao) {
        return new ResultadoAutorizacao(true, null, codigoAutorizacao);
    }

    public static ResultadoAutorizacao recusada(MotivoRecusa motivo) {
        return new ResultadoAutorizacao(false, motivo, null);
    }
}
