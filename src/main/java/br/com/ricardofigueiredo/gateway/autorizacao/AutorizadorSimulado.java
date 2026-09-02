package br.com.ricardofigueiredo.gateway.autorizacao;

import br.com.ricardofigueiredo.gateway.cobranca.MetodoPagamento;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.YearMonth;
import java.util.Locale;

/**
 * Emissor simulado. Nao existe adquirente do outro lado: a decisao e tomada
 * por regras fixas e deterministicas, para que o mesmo cartao com o mesmo valor
 * produza sempre a mesma resposta e o aplicativo cliente consiga testar tanto
 * o caminho feliz quanto cada tipo de recusa.
 *
 * Regras, na ordem em que sao aplicadas:
 * 1. valor acima do limite por transacao devolve LIMITE_EXCEDIDO;
 * 2. Pix e sempre aprovado, porque nao ha analise de credito envolvida;
 * 3. bandeira nao reconhecida devolve BANDEIRA_NAO_SUPORTADA;
 * 4. cartao com validade anterior ao mes corrente devolve CARTAO_EXPIRADO;
 * 5. cartao terminado em 0000 devolve CARTAO_BLOQUEADO;
 * 6. cartao terminado em 0001 devolve SALDO_INSUFICIENTE;
 * 7. qualquer outro caso e aprovado.
 */
@Component
public class AutorizadorSimulado {

    private static final String FINAL_BLOQUEADO = "0000";
    private static final String FINAL_SEM_SALDO = "0001";

    private final long limitePorTransacaoEmCentavos;
    private final Clock relogio;

    public AutorizadorSimulado(
            @Value("${gateway.autorizacao.limite-por-transacao-em-centavos}") long limitePorTransacaoEmCentavos,
            Clock relogio) {
        this.limitePorTransacaoEmCentavos = limitePorTransacaoEmCentavos;
        this.relogio = relogio;
    }

    public ResultadoAutorizacao autorizar(MetodoPagamento metodo, long valorEmCentavos,
                                          CartaoTokenizado cartao) {
        if (valorEmCentavos > limitePorTransacaoEmCentavos) {
            return ResultadoAutorizacao.recusada(MotivoRecusa.LIMITE_EXCEDIDO);
        }
        if (metodo == MetodoPagamento.PIX) {
            return ResultadoAutorizacao.aprovada(gerarCodigo("PIX", valorEmCentavos));
        }
        if (cartao.getBandeira() == Bandeira.DESCONHECIDA) {
            return ResultadoAutorizacao.recusada(MotivoRecusa.BANDEIRA_NAO_SUPORTADA);
        }
        if (cartao.venceuAte(YearMonth.now(relogio))) {
            return ResultadoAutorizacao.recusada(MotivoRecusa.CARTAO_EXPIRADO);
        }
        if (FINAL_BLOQUEADO.equals(cartao.getUltimosQuatro())) {
            return ResultadoAutorizacao.recusada(MotivoRecusa.CARTAO_BLOQUEADO);
        }
        if (FINAL_SEM_SALDO.equals(cartao.getUltimosQuatro())) {
            return ResultadoAutorizacao.recusada(MotivoRecusa.SALDO_INSUFICIENTE);
        }
        return ResultadoAutorizacao.aprovada(gerarCodigo(cartao.getBin() + cartao.getUltimosQuatro(), valorEmCentavos));
    }

    private String gerarCodigo(String origem, long valorEmCentavos) {
        int semente = (origem + ":" + valorEmCentavos).hashCode();
        return String.format(Locale.ROOT, "%06X", Math.abs(semente) % 0xFFFFFF);
    }
}
