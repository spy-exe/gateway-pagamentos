package br.com.ricardofigueiredo.gateway.cobranca;

import br.com.ricardofigueiredo.gateway.comum.excecao.RegraDeNegocioException;
import br.com.ricardofigueiredo.gateway.usuario.Usuario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Service
public class ExtratoCsvService {

    private static final int TAMANHO_DO_LOTE = 500;
    private final CobrancaRepository cobrancaRepository;

    public ExtratoCsvService(CobrancaRepository cobrancaRepository) {
        this.cobrancaRepository = cobrancaRepository;
    }

    @Transactional(readOnly = true)
    public void escrever(OutputStream saida, Usuario usuario, StatusCobranca status,
                         MetodoPagamento metodo, Instant desde, Instant ate, String busca,
                         Long valorMinimo, Long valorMaximo) throws IOException {
        validarValores(valorMinimo, valorMaximo);
        Writer escritor = new OutputStreamWriter(saida, StandardCharsets.UTF_8);
        escritor.write('\uFEFF');
        escritor.write("codigo;criadoEm;descricao;metodo;status;parcelas;valor;estornado;"
                + "bandeira;ultimosQuatro;codigoAutorizacao;origem\n");

        int numeroDaPagina = 0;
        Page<Cobranca> pagina;
        do {
            pagina = cobrancaRepository.findAll(
                    CobrancaSpecs.de(usuario, status, metodo, desde, ate, busca, valorMinimo, valorMaximo),
                    PageRequest.of(numeroDaPagina, TAMANHO_DO_LOTE, Sort.by(Sort.Direction.DESC, "criadoEm")));
            for (Cobranca cobranca : pagina.getContent()) {
                escreverLinha(escritor, cobranca);
            }
            escritor.flush();
            numeroDaPagina += 1;
        } while (pagina.hasNext());
    }

    private void escreverLinha(Writer escritor, Cobranca cobranca) throws IOException {
        String[] colunas = {
                cobranca.getCodigo(),
                cobranca.getCriadoEm().toString(),
                cobranca.getDescricao(),
                cobranca.getMetodo().name(),
                cobranca.getStatus().name(),
                String.valueOf(cobranca.getParcelas()),
                reais(cobranca.getValorEmCentavos()),
                reais(cobranca.getValorEstornadoEmCentavos()),
                cobranca.getBandeira() == null ? "" : cobranca.getBandeira().name(),
                cobranca.getUltimosQuatro(),
                cobranca.getCodigoAutorizacao(),
                cobranca.getCodigoDoLinkPagamento() == null ? "API" : cobranca.getCodigoDoLinkPagamento()
        };

        for (int indice = 0; indice < colunas.length; indice++) {
            if (indice > 0) {
                escritor.write(';');
            }
            escritor.write(celula(colunas[indice]));
        }
        escritor.write('\n');
    }

    static String celula(String valor) {
        if (valor == null) {
            return "";
        }

        String seguro = valor;
        if (!seguro.isEmpty() && "=+-@".indexOf(seguro.charAt(0)) >= 0) {
            seguro = "'" + seguro;
        }
        if (seguro.indexOf(';') >= 0 || seguro.indexOf('"') >= 0
                || seguro.indexOf('\n') >= 0 || seguro.indexOf('\r') >= 0) {
            return "\"" + seguro.replace("\"", "\"\"") + "\"";
        }
        return seguro;
    }

    private String reais(long centavos) {
        return "%d,%02d".formatted(centavos / 100, Math.abs(centavos % 100));
    }

    private void validarValores(Long minimo, Long maximo) {
        if ((minimo != null && minimo < 0) || (maximo != null && maximo < 0)) {
            throw new RegraDeNegocioException("Os valores do filtro nao podem ser negativos.");
        }
        if (minimo != null && maximo != null && minimo > maximo) {
            throw new RegraDeNegocioException("O valor minimo nao pode ser maior que o maximo.");
        }
    }
}
