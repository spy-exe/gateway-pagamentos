package br.com.ricardofigueiredo.gateway.autorizacao;

import br.com.ricardofigueiredo.gateway.comum.excecao.RegraDeNegocioException;

import java.time.YearMonth;
import java.util.Objects;

/**
 * Representacao do cartao depois da tokenizacao. O numero completo existe
 * apenas dentro do metodo de fabrica: o que sai daqui e o que pode ser gravado
 * em banco e aparecer em log, ou seja, os seis primeiros digitos, os quatro
 * ultimos e a bandeira.
 */
public final class CartaoTokenizado {

    private final String bin;
    private final String ultimosQuatro;
    private final Bandeira bandeira;
    private final YearMonth validade;
    private final String nomePortador;

    private CartaoTokenizado(String bin, String ultimosQuatro, Bandeira bandeira,
                             YearMonth validade, String nomePortador) {
        this.bin = bin;
        this.ultimosQuatro = ultimosQuatro;
        this.bandeira = bandeira;
        this.validade = validade;
        this.nomePortador = nomePortador;
    }

    public static CartaoTokenizado tokenizar(String numero, int validadeMes, int validadeAno,
                                             String nomePortador) {
        String digitos = Objects.requireNonNull(numero, "numero do cartao e obrigatorio")
                .replaceAll("[\\s.-]", "");

        if (!digitos.matches("\\d{13,19}")) {
            throw new RegraDeNegocioException("O numero do cartao deve conter de 13 a 19 digitos.");
        }
        if (!passaNoLuhn(digitos)) {
            throw new RegraDeNegocioException("O numero do cartao nao passou na validacao de digito verificador.");
        }
        if (validadeMes < 1 || validadeMes > 12) {
            throw new RegraDeNegocioException("Mes de validade invalido.");
        }

        return new CartaoTokenizado(
                digitos.substring(0, 6),
                digitos.substring(digitos.length() - 4),
                Bandeira.identificar(digitos),
                YearMonth.of(validadeAno, validadeMes),
                nomePortador == null ? null : nomePortador.trim());
    }

    /**
     * Algoritmo de Luhn: dobra os digitos em posicao par a partir da direita,
     * subtrai nove dos resultados maiores que nove e exige soma multipla de dez.
     */
    static boolean passaNoLuhn(String digitos) {
        int soma = 0;
        boolean dobrar = false;

        for (int posicao = digitos.length() - 1; posicao >= 0; posicao--) {
            int digito = digitos.charAt(posicao) - '0';
            if (dobrar) {
                digito *= 2;
                if (digito > 9) {
                    digito -= 9;
                }
            }
            soma += digito;
            dobrar = !dobrar;
        }

        return soma % 10 == 0;
    }

    public boolean venceuAte(YearMonth referencia) {
        return validade.isBefore(referencia);
    }

    public String getBin() {
        return bin;
    }

    public String getUltimosQuatro() {
        return ultimosQuatro;
    }

    public Bandeira getBandeira() {
        return bandeira;
    }

    public String getNomePortador() {
        return nomePortador;
    }

    @Override
    public String toString() {
        return bandeira + " ****" + ultimosQuatro;
    }
}
