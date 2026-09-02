package br.com.ricardofigueiredo.gateway.cobranca;

import br.com.ricardofigueiredo.gateway.autorizacao.AutorizadorSimulado;
import br.com.ricardofigueiredo.gateway.autorizacao.CartaoTokenizado;
import br.com.ricardofigueiredo.gateway.autorizacao.ResultadoAutorizacao;
import br.com.ricardofigueiredo.gateway.cobranca.dto.CriarCobrancaRequest;
import br.com.ricardofigueiredo.gateway.cobranca.dto.DadosCartaoRequest;
import br.com.ricardofigueiredo.gateway.cobranca.dto.EstornoRequest;
import br.com.ricardofigueiredo.gateway.comum.excecao.ConflitoException;
import br.com.ricardofigueiredo.gateway.comum.excecao.RecursoNaoEncontradoException;
import br.com.ricardofigueiredo.gateway.usuario.Usuario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CobrancaService {

    private static final Logger log = LoggerFactory.getLogger(CobrancaService.class);

    private final CobrancaRepository cobrancaRepository;
    private final EstornoRepository estornoRepository;
    private final EventoCobrancaRepository eventoRepository;
    private final AutorizadorSimulado autorizador;

    public CobrancaService(CobrancaRepository cobrancaRepository,
                           EstornoRepository estornoRepository,
                           EventoCobrancaRepository eventoRepository,
                           AutorizadorSimulado autorizador) {
        this.cobrancaRepository = cobrancaRepository;
        this.estornoRepository = estornoRepository;
        this.eventoRepository = eventoRepository;
        this.autorizador = autorizador;
    }

    /**
     * Indica se a cobranca foi criada agora ou recuperada por chave de
     * idempotencia, para que o controller responda 201 ou 200.
     */
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
                resultado);

        try {
            cobrancaRepository.saveAndFlush(cobranca);
        } catch (DataIntegrityViolationException excecao) {
            throw new ConflitoException("Ja existe uma cobranca sendo processada com esta chave de idempotencia.");
        }

        registrarEvento(cobranca, resultado.aprovada() ? "AUTORIZACAO" : "RECUSA", null,
                resultado.aprovada()
                        ? "codigo de autorizacao " + resultado.codigoAutorizacao()
                        : resultado.motivo().getDescricao());

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

        return cobranca;
    }

    @Transactional
    public Cobranca cancelar(Usuario usuario, String codigo) {
        Cobranca cobranca = buscarEntidade(usuario, codigo);
        StatusCobranca anterior = cobranca.getStatus();

        cobranca.cancelar();
        registrarEvento(cobranca, "CANCELAMENTO", anterior, "autorizacao desfeita antes da captura");

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

        log.info("cobranca {} estornada em {} centavos, novo status {}",
                cobranca.getCodigo(), valor, cobranca.getStatus());

        return estorno;
    }

    @Transactional(readOnly = true)
    public Cobranca buscar(Usuario usuario, String codigo) {
        return buscarEntidade(usuario, codigo);
    }

    @Transactional(readOnly = true)
    public Page<Cobranca> listar(Usuario usuario, StatusCobranca status, MetodoPagamento metodo,
                                 Pageable paginacao) {
        if (status != null && metodo != null) {
            return cobrancaRepository.findByUsuarioAndStatusAndMetodo(usuario, status, metodo, paginacao);
        }
        if (status != null) {
            return cobrancaRepository.findByUsuarioAndStatus(usuario, status, paginacao);
        }
        if (metodo != null) {
            return cobrancaRepository.findByUsuarioAndMetodo(usuario, metodo, paginacao);
        }
        return cobrancaRepository.findByUsuario(usuario, paginacao);
    }

    @Transactional(readOnly = true)
    public List<EventoCobranca> eventos(Usuario usuario, String codigo) {
        return eventoRepository.findByCobrancaOrderByCriadoEmAsc(buscarEntidade(usuario, codigo));
    }

    @Transactional(readOnly = true)
    public List<Estorno> estornos(Usuario usuario, String codigo) {
        return estornoRepository.findByCobrancaOrderByCriadoEmAsc(buscarEntidade(usuario, codigo));
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
}
