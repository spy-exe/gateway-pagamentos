package br.com.ricardofigueiredo.gateway.cobranca;

/**
 * Estados possiveis de uma cobranca. As transicoes validas estao implementadas
 * na propria entidade Cobranca:
 *
 * AUTORIZADA -> CAPTURADA | CANCELADA
 * CAPTURADA -> PARCIALMENTE_ESTORNADA | ESTORNADA
 * PARCIALMENTE_ESTORNADA -> PARCIALMENTE_ESTORNADA | ESTORNADA
 * RECUSADA, CANCELADA e ESTORNADA sao estados finais.
 */
public enum StatusCobranca {

    AUTORIZADA,
    CAPTURADA,
    RECUSADA,
    CANCELADA,
    PARCIALMENTE_ESTORNADA,
    ESTORNADA
}
