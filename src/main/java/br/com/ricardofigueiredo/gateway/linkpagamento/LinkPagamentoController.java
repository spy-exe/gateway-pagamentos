package br.com.ricardofigueiredo.gateway.linkpagamento;

import br.com.ricardofigueiredo.gateway.cobranca.dto.CobrancaResponse;
import br.com.ricardofigueiredo.gateway.linkpagamento.dto.CheckoutLinkPagamentoResponse;
import br.com.ricardofigueiredo.gateway.linkpagamento.dto.CriarLinkPagamentoRequest;
import br.com.ricardofigueiredo.gateway.linkpagamento.dto.FinalizarLinkPagamentoRequest;
import br.com.ricardofigueiredo.gateway.linkpagamento.dto.LinkPagamentoResponse;
import br.com.ricardofigueiredo.gateway.seguranca.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/links-pagamento")
@Tag(name = "Links de pagamento", description = "Checkout compartilhavel sem integracao do comprador")
public class LinkPagamentoController {

    private final LinkPagamentoService linkService;

    public LinkPagamentoController(LinkPagamentoService linkService) {
        this.linkService = linkService;
    }

    @PostMapping
    @Operation(summary = "Cria um link de pagamento")
    public ResponseEntity<LinkPagamentoResponse> criar(
            @AuthenticationPrincipal UsuarioAutenticado autenticado,
            @Valid @RequestBody CriarLinkPagamentoRequest requisicao) {
        LinkPagamento link = linkService.criar(autenticado.getUsuario(), requisicao);
        return ResponseEntity.created(URI.create("/api/v1/links-pagamento/" + link.getCodigo()))
                .body(LinkPagamentoResponse.de(link, Instant.now()));
    }

    @GetMapping
    @Operation(summary = "Lista os links do estabelecimento")
    public List<LinkPagamentoResponse> listar(@AuthenticationPrincipal UsuarioAutenticado autenticado) {
        Instant agora = Instant.now();
        return linkService.listar(autenticado.getUsuario()).stream()
                .map(link -> LinkPagamentoResponse.de(link, agora))
                .toList();
    }

    @PostMapping("/{codigo}/situacao")
    @Operation(summary = "Pausa ou reativa um link")
    public LinkPagamentoResponse definirAtivo(
            @AuthenticationPrincipal UsuarioAutenticado autenticado,
            @PathVariable String codigo,
            @RequestParam boolean ativo) {
        return LinkPagamentoResponse.de(
                linkService.definirAtivo(autenticado.getUsuario(), codigo, ativo), Instant.now());
    }

    @GetMapping("/publicos/{codigo}")
    @Operation(summary = "Abre os dados publicos do checkout")
    public CheckoutLinkPagamentoResponse abrir(@PathVariable String codigo) {
        return linkService.abrirCheckout(codigo);
    }

    @PostMapping("/publicos/{codigo}/finalizacao")
    @Operation(summary = "Conclui uma cobranca pelo checkout publico")
    public ResponseEntity<CobrancaResponse> finalizar(
            @PathVariable String codigo,
            @RequestHeader(name = "Idempotency-Key", required = false) String chaveIdempotencia,
            @Valid @RequestBody FinalizarLinkPagamentoRequest requisicao) {
        var resultado = linkService.finalizar(codigo, requisicao, chaveIdempotencia);
        CobrancaResponse corpo = CobrancaResponse.de(resultado.cobranca());
        return resultado.recuperadaPorIdempotencia()
                ? ResponseEntity.ok(corpo)
                : ResponseEntity.status(HttpStatus.CREATED).body(corpo);
    }
}
