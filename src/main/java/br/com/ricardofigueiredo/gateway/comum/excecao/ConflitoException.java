package br.com.ricardofigueiredo.gateway.comum.excecao;

public class ConflitoException extends RuntimeException {

    public ConflitoException(String mensagem) {
        super(mensagem);
    }
}
