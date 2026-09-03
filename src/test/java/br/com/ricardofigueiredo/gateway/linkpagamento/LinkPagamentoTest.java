package br.com.ricardofigueiredo.gateway.linkpagamento;

import br.com.ricardofigueiredo.gateway.cobranca.MetodoPagamento;
import br.com.ricardofigueiredo.gateway.comum.excecao.RegraDeNegocioException;
import br.com.ricardofigueiredo.gateway.usuario.Usuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LinkPagamentoTest {

    @Test
    @DisplayName("um link novo nasce ativo e conta cada uso")
    void nasceAtivo() {
        LinkPagamento link = link(MetodoPagamento.PIX, 1, 2, null);

        link.registrarUso();

        assertThat(link.getCodigo()).startsWith("link_");
        assertThat(link.getUsos()).isEqualTo(1);
        assertThat(link.situacao(Instant.now())).isEqualTo(SituacaoLinkPagamento.ATIVO);
    }

    @Test
    @DisplayName("limite, pausa e validade fecham o checkout com motivos diferentes")
    void situacoesDeFechamento() {
        LinkPagamento esgotado = link(MetodoPagamento.PIX, 1, 1, null);
        esgotado.registrarUso();
        assertThat(esgotado.situacao(Instant.now())).isEqualTo(SituacaoLinkPagamento.ESGOTADO);
        assertThatThrownBy(esgotado::registrarUso).hasMessageContaining("limite");

        LinkPagamento pausado = link(MetodoPagamento.PIX, 1, null, null);
        pausado.definirAtivo(false);
        assertThat(pausado.situacao(Instant.now())).isEqualTo(SituacaoLinkPagamento.PAUSADO);
        assertThatThrownBy(() -> pausado.exigirDisponivel(Instant.now())).hasMessageContaining("pausado");

        LinkPagamento expirado = link(MetodoPagamento.PIX, 1, null, Instant.now().minusSeconds(1));
        assertThat(expirado.situacao(Instant.now())).isEqualTo(SituacaoLinkPagamento.EXPIRADO);
        assertThatThrownBy(() -> expirado.exigirDisponivel(Instant.now())).hasMessageContaining("expirou");
    }

    @Test
    @DisplayName("parcelamento so pode ser oferecido no credito")
    void parcelamentoSoNoCredito() {
        assertThatThrownBy(() -> link(MetodoPagamento.PIX, 3, null, null))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("credito");
    }

    private LinkPagamento link(MetodoPagamento metodo, int parcelas, Integer limite, Instant validade) {
        Usuario usuario = new Usuario("loja@exemplo.com", "hash", "Loja", "loja@exemplo.com", "NITEROI");
        return new LinkPagamento(usuario, "Cesta especial", 15900, metodo, parcelas, limite, validade);
    }
}
