package br.com.ricardofigueiredo.gateway.cobranca;

import br.com.ricardofigueiredo.gateway.autorizacao.AutorizadorSimulado;
import br.com.ricardofigueiredo.gateway.autorizacao.CartaoTokenizado;
import br.com.ricardofigueiredo.gateway.autorizacao.ResultadoAutorizacao;
import br.com.ricardofigueiredo.gateway.cobranca.dto.CriarCobrancaRequest;
import br.com.ricardofigueiredo.gateway.cobranca.dto.DadosCartaoRequest;
import br.com.ricardofigueiredo.gateway.cobranca.dto.DiaDoMovimento;
import br.com.ricardofigueiredo.gateway.cobranca.dto.EstornoRequest;
import br.com.ricardofigueiredo.gateway.cobranca.dto.ResumoResponse;
import br.com.ricardofigueiredo.gateway.comum.excecao.ConflitoException;
import br.com.ricardofigueiredo.gateway.comum.excecao.RecursoNaoEncontradoException;
import br.com.ricardofigueiredo.gateway.comum.excecao.RegraDeNegocioException;
import br.com.ricardofigueiredo.gateway.pix.BrCode;
import br.com.ricardofigueiredo.gateway.usuario.Usuario;
import br.com.ricardofigueiredo.gateway.webhook.EmissorDeEventos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
public class CobrancaService {

    private static final Logger log = LoggerFactory.getLogger(CobrancaService.class);

    /** Abaixo disso a parcela nao paga o custo de processa-la, entao ninguem aceita. */
    private static final long PARCELA_MINIMA_EM_CENTAVOS = 500L;

    private static final Set<StatusCobranca> LIQUIDADAS = EnumSet.of(
            StatusCobranca.CAPTURADA, StatusCobranca.PARCIALMENTE_ESTORNADA, StatusCobranca.ESTORNADA);

    private final CobrancaRepository cobrancaRepository;
    private final EstornoRepository estornoRepository;
    private final EventoCobrancaRepository eventoRepository;
    private final AutorizadorSimulado autorizador;
    private final EmissorDeEventos emissor;

    public CobrancaService(CobrancaRepository cobrancaRepository,
                           EstornoRepository estornoRepository,
                           EventoCobrancaRepository eventoRepository,
                           AutorizadorSimulado autorizador,
                           EmissorDeEventos emissor) {
        this.cobrancaRepository = cobrancaRepository;
        this.estornoRepository = estornoRepository;
        this.eventoRepository = eventoRepository;
        this.autorizador = autorizador;
        this.emissor = emissor;
    }

    public record ResultadoDaCriacao(Cobranca cobranca, boolean recuperadaPorIdempotencia) {
    }

    @Transactional
    public ResultadoDaCriacao criar(Usuario usuario, CriarCobrancaRequest requisicao, String chaveIdempotencia) {
        String chave = normalizarChave(chaveIdempotencia);

        if (chave != null) {
            var existente = cobrancaRepository.findByUsuarioAndChaveIdempotencia(usuario, chave);
            if (existente.isPresent()) {
                log.info("cobranca {} devolvida pela chave de idempotencia", existente.get().getCodigo());
                return new ResultadoDaCriacao(existente.get(), true);
            }
        }

        int parcelas = validarParcelamento(requisicao);
        CartaoTokenizado cartao = requisicao.metodo().exigeCartao() ? tokenizar(requisicao.cartao()) : null;
        ResultadoAutorizacao resultado =
                autorizador.autorizar(requisicao.metodo(), requisicao.valorEmCentavos(), cartao);

        Cobranca cobranca = new Cobranca(
                usuario,
                requisicao.valorEmCentavos(),
                requisicao.descricao().trim(),
                requisicao.metodo(),
                requisicao.capturaAutomaticaOuPadrao(),
                cartao,
                chave,
                parcelas,
                resultado);

        try {
            cobrancaRepository.saveAndFlush(cobranca);
        } catch (DataIntegrityViolationException excecao) {
            throw new ConflitoException("Ja existe uma cobranca sendo processada com esta chave de idempotencia.");
        }

        if (requisicao.metodo() == MetodoPagamento.PIX && resultado.aprovada()) {
            cobranca.registrarPix(gerarPix(usuario, cobranca));
        }

        registrarEvento(cobranca, resultado.aprovada() ? "AUTORIZACAO" : "RECUSA", null,
                resultado.aprovada()
                        ? "codigo de autorizacao " + resultado.codigoAutorizacao()
                        : resultado.motivo().getDescricao());

        emissor.emitir(resultado.aprovada()
                ? (cobranca.getStatus() == StatusCobranca.CAPTURADA ? "cobranca.capturada" : "cobranca.autorizada")
                : "cobranca.recusada", cobranca);

        log.info("cobranca {} criada por {} com status {}",
                cobranca.getCodigo(), usuario.getEmail(), cobranca.getStatus());

        return new ResultadoDaCriacao(cobranca, false);
    }

    @Transactional
    public Cobranca capturar(Usuario usuario, String codigo) {
        Cobranca cobranca = buscarEntidade(usuario, codigo);
        StatusCobranca anterior = cobranca.getStatus();

        cobranca.capturar();
        registrarEvento(cobranca, "CAPTURA", anterior, "captura manual solicitada pelo estabelecimento");
        emissor.emitir("cobranca.capturada", cobranca);

        return cobranca;
    }

    @Transactional
    public Cobranca cancelar(Usuario usuario, String codigo) {
        Cobranca cobranca = buscarEntidade(usuario, codigo);
        StatusCobranca anterior = cobranca.getStatus();

        cobranca.cancelar();
        registrarEvento(cobranca, "CANCELAMENTO", anterior, "autorizacao desfeita antes da captura");
        emissor.emitir("cobranca.cancelada", cobranca);

        return cobranca;
    }

    @Transactional
    public Estorno estornar(Usuario usuario, String codigo, EstornoRequest requisicao) {
        Cobranca cobranca = buscarEntidade(usuario, codigo);
        StatusCobranca anterior = cobranca.getStatus();

        long valor = requisicao.valorEmCentavos() == null
                ? cobranca.saldoEstornavelEmCentavos()
                : requisicao.valorEmCentavos();

        cobranca.registrarEstorno(valor);
        Estorno estorno = estornoRepository.save(new Estorno(cobranca, valor, requisicao.motivo()));
        registrarEvento(cobranca, "ESTORNO", anterior, "estorno de " + valor + " centavos");
        emissor.emitir("cobranca.estornada", cobranca);

        log.info("cobranca {} estornada em {} centavos, novo status {}",
                cobranca.getCodigo(), valor, cobranca.getStatus());

        return estorno;
    }

    /**
     * Nao e somente leitura de proposito. As cobrancas Pix criadas antes de o
     * BR Code existir ficaram sem o copia e cola, e o codigo e deterministico a
     * partir de dados que ja estao gravados. Em vez de deixar registro
     * historico pela metade, ele e completado na primeira vez que alguem abre a
     * cobranca. Acontece uma vez por registro e nunca reescreve o que ja tem.
     */
    @Transactional
    public Cobranca buscar(Usuario usuario, String codigo) {
        Cobranca cobranca = buscarEntidade(usuario, codigo);

        if (cobranca.getMetodo() == MetodoPagamento.PIX
                && cobranca.getPixCopiaECola() == null
                && cobranca.getStatus() != StatusCobranca.RECUSADA) {
            cobranca.registrarPix(gerarPix(usuario, cobranca));
        }

        return cobranca;
    }

    @Transactional(readOnly = true)
    public Page<Cobranca> listar(Usuario usuario, StatusCobranca status, MetodoPagamento metodo,
                                 Instant desde, Instant ate, String busca, Pageable paginacao) {
        return cobrancaRepository.findAll(CobrancaSpecs.de(usuario, status, metodo, desde, ate, busca), paginacao);
    }

    @Transactional(readOnly = true)
    public ResumoResponse resumir(Usuario usuario, Instant desde) {
        Object[] linha = cobrancaRepository.resumir(
                usuario, LIQUIDADAS, StatusCobranca.AUTORIZADA, StatusCobranca.RECUSADA, desde);

        // o Hibernate entrega a projecao de coluna unica embrulhada em outro vetor
        Object[] colunas = linha.length == 1 && linha[0] instanceof Object[] interno ? interno : linha;

        return ResumoResponse.de(
                comoLongo(colunas[0]), comoLongo(colunas[1]), comoLongo(colunas[2]),
                comoLongo(colunas[3]), comoLongo(colunas[4]));
    }

    @Transactional(readOnly = true)
    public List<DiaDoMovimento> movimentoPorDia(Usuario usuario, Instant desde) {
        return cobrancaRepository.movimentoPorDia(usuario.getId(), desde).stream()
                .map(linha -> new DiaDoMovimento(
                        comoData(linha[0]),
                        comoLongo(linha[1]),
                        comoLongo(linha[2]),
                        comoLongo(linha[3])))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FatiaDeBandeira> mixDeBandeiras(Usuario usuario, Instant desde) {
        return cobrancaRepository.mixDeBandeiras(usuario, desde).stream()
                .map(linha -> new FatiaDeBandeira(
                        String.valueOf(linha[0]),
                        comoLongo(linha[1]),
                        comoLongo(linha[2])))
                .toList();
    }

    public record FatiaDeBandeira(String bandeira, long transacoes, long valorEmCentavos) {
    }

    @Transactional(readOnly = true)
    public List<EventoCobranca> eventos(Usuario usuario, String codigo) {
        return eventoRepository.findByCobrancaOrderByCriadoEmAsc(buscarEntidade(usuario, codigo));
    }

    @Transactional(readOnly = true)
    public List<Estorno> estornos(Usuario usuario, String codigo) {
        return estornoRepository.findByCobrancaOrderByCriadoEmAsc(buscarEntidade(usuario, codigo));
    }

    private String gerarPix(Usuario usuario, Cobranca cobranca) {
        return BrCode.gerar(
                usuario.chavePixOuEmail(),
                usuario.getNomeEstabelecimento(),
                usuario.cidadeOuPadrao(),
                cobranca.getValorEmCentavos(),
                cobranca.getCodigo());
    }

    private int validarParcelamento(CriarCobrancaRequest requisicao) {
        int parcelas = requisicao.parcelasOuUma();

        if (parcelas > 1 && !requisicao.metodo().permiteParcelamento()) {
            throw new RegraDeNegocioException("Parcelamento so existe no cartao de credito.");
        }
        if (requisicao.valorEmCentavos() / parcelas < PARCELA_MINIMA_EM_CENTAVOS) {
            throw new RegraDeNegocioException(
                    "Cada parcela precisa ficar em pelo menos " + PARCELA_MINIMA_EM_CENTAVOS + " centavos.");
        }

        return parcelas;
    }

    private Cobranca buscarEntidade(Usuario usuario, String codigo) {
        return cobrancaRepository.findByCodigoAndUsuario(codigo, usuario)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Nenhuma cobranca encontrada com o codigo " + codigo + "."));
    }

    private CartaoTokenizado tokenizar(DadosCartaoRequest cartao) {
        return CartaoTokenizado.tokenizar(cartao.numero(), cartao.validadeMes(),
                cartao.validadeAno(), cartao.nomePortador());
    }

    private void registrarEvento(Cobranca cobranca, String tipo, StatusCobranca anterior, String detalhe) {
        eventoRepository.save(new EventoCobranca(cobranca, tipo, anterior, detalhe));
    }

    private String normalizarChave(String chave) {
        return chave == null || chave.isBlank() ? null : chave.trim();
    }

    private static long comoLongo(Object valor) {
        return valor == null ? 0L : ((Number) valor).longValue();
    }

    private static LocalDate comoData(Object valor) {
        if (valor instanceof LocalDate data) {
            return data;
        }
        if (valor instanceof Date data) {
            return data.toLocalDate();
        }
        return LocalDate.parse(String.valueOf(valor));
    }
}
