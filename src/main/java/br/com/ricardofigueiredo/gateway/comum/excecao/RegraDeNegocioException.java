package br.com.ricardofigueiredo.gateway.comum.excecao;

/**
 * Operacao valida do ponto de vista sintatico, porem proibida pelas regras do gateway.
 * Exemplo: capturar uma cobranca que ja foi cancelada.
 */
public class RegraDeNegocioException extends RuntimeException {

    public RegraDeNegocioException(String mensagem) {
        super(mensagem);
    }
}
