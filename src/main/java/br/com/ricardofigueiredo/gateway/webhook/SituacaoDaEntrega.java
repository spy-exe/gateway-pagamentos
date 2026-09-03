package br.com.ricardofigueiredo.gateway.webhook;

public enum SituacaoDaEntrega {

    /** Esperando a proxima janela de tentativa. */
    PENDENTE,

    /** O outro lado respondeu 2xx. */
    ENTREGUE,

    /** Acabaram as tentativas. So sai daqui por reenvio manual. */
    FALHOU
}
