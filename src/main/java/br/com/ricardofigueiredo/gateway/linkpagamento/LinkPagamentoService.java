package br.com.ricardofigueiredo.gateway.linkpagamento;

import br.com.ricardofigueiredo.gateway.cobranca.CobrancaService;
import br.com.ricardofigueiredo.gateway.cobranca.MetodoPagamento;
import br.com.ricardofigueiredo.gateway.cobranca.StatusCobranca;
import br.com.ricardofigueiredo.gateway.cobranca.dto.CriarCobrancaRequest;
import br.com.ricardofigueiredo.gateway.comum.excecao.RecursoNaoEncontradoException;
import br.com.ricardofigueiredo.gateway.comum.excecao.RegraDeNegocioException;
import br.com.ricardofigueiredo.gateway.linkpagamento.dto.CheckoutLinkPagamentoResponse;
import br.com.ricardofigueiredo.gateway.linkpagamento.dto.CriarLinkPagamentoRequest;
import br.com.ricardofigueiredo.gateway.linkpagamento.dto.FinalizarLinkPagamentoRequest;
import br.com.ricardofigueiredo.gateway.usuario.Usuario;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class LinkPagamentoService {

    private final LinkPagamentoRepository linkRepository;
    private final CobrancaService cobrancaService;

    public LinkPagamentoService(LinkPagamentoRepository linkRepository, CobrancaService cobrancaService) {
        this.linkRepository = linkRepository;
        this.cobrancaService = cobrancaService;
    }

    @Transactional
    public LinkPagamento criar(Usuario usuario, CriarLinkPagamentoRequest requisicao) {
        LinkPagamento link = new LinkPagamento(
                usuario,
                requisicao.descricao(),
                requisicao.valorEmCentavos(),
                requisicao.metodo(),
                requisicao.parcelasMaximasOuUma(),
                requisicao.limiteDeUsos(),
                requisicao.expiraEm());
        return linkRepository.save(link);
    }

    @Transactional(readOnly = true)
    public List<LinkPagamento> listar(Usuario usuario) {
        return linkRepository.findByUsuarioOrderByCriadoEmDesc(usuario);
    }

    @Transactional
    public LinkPagamento definirAtivo(Usuario usuario, String codigo, boolean ativo) {
        LinkPagamento link = buscarDoUsuario(usuario, codigo);
        link.definirAtivo(ativo);
        return link;
    }

    @Transactional(readOnly = true)
    public CheckoutLinkPagamentoResponse abrirCheckout(String codigo) {
        LinkPagamento link = linkRepository.findByCodigo(codigo)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Link de pagamento nao encontrado."));
        return CheckoutLinkPagamentoResponse.de(link, Instant.now());
    }

    @Transactional
    public CobrancaService.ResultadoDaCriacao finalizar(String codigo,
                                                        FinalizarLinkPagamentoRequest requisicao,
                                                        String chaveIdempotencia) {
        LinkPagamento link = linkRepository.buscarParaFinalizacao(codigo)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Link de pagamento nao encontrado."));
        link.exigirDisponivel(Instant.now());

        int parcelas = requisicao.parcelasOuUma();
        if (parcelas > link.getParcelasMaximas()) {
            throw new RegraDeNegocioException(
                    "Este link permite parcelamento em ate " + link.getParcelasMaximas() + " vezes.");
        }

        if (link.getMetodo().exigeCartao() != (requisicao.cartao() != null)) {
            throw new RegraDeNegocioException(link.getMetodo().exigeCartao()
                    ? "Informe os dados do cartao para concluir o pagamento."
                    : "Este link Pix nao recebe dados de cartao.");
        }

        CriarCobrancaRequest cobranca = new CriarCobrancaRequest(
                link.getValorEmCentavos(), link.getDescricao(), link.getMetodo(), true,
                link.getMetodo() == MetodoPagamento.CARTAO_CREDITO ? parcelas : 1,
                requisicao.cartao());

        String chaveDoLink = chaveIdempotencia == null || chaveIdempotencia.isBlank()
                ? null
                : codigo + ":" + chaveIdempotencia.trim();
        CobrancaService.ResultadoDaCriacao resultado =
                cobrancaService.criar(link.getUsuario(), cobranca, chaveDoLink, link);

        if (!resultado.recuperadaPorIdempotencia()
                && resultado.cobranca().getStatus() != StatusCobranca.RECUSADA) {
            link.registrarUso();
        }
        return resultado;
    }

    private LinkPagamento buscarDoUsuario(Usuario usuario, String codigo) {
        return linkRepository.findByCodigoAndUsuario(codigo, usuario)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Link de pagamento nao encontrado."));
    }
}
