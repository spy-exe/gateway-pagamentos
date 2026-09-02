package br.com.ricardofigueiredo.gateway.autorizacao;

public enum MotivoRecusa {

    SALDO_INSUFICIENTE("Saldo ou limite insuficiente no cartao"),
    CARTAO_BLOQUEADO("Cartao bloqueado pelo emissor"),
    CARTAO_EXPIRADO("Cartao vencido"),
    BANDEIRA_NAO_SUPORTADA("Bandeira nao aceita pelo gateway"),
    LIMITE_EXCEDIDO("Valor acima do limite permitido por transacao");

    private final String descricao;

    MotivoRecusa(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
