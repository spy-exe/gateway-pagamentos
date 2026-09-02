package br.com.ricardofigueiredo.gateway.cobranca;

public enum MetodoPagamento {

    CARTAO_CREDITO(true),
    CARTAO_DEBITO(true),
    PIX(false);

    private final boolean exigeCartao;

    MetodoPagamento(boolean exigeCartao) {
        this.exigeCartao = exigeCartao;
    }

    public boolean exigeCartao() {
        return exigeCartao;
    }
}
