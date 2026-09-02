package br.com.ricardofigueiredo.gateway.cobranca;

import br.com.ricardofigueiredo.gateway.cobranca.dto.CobrancaResponse;
import br.com.ricardofigueiredo.gateway.cobranca.dto.CriarCobrancaRequest;
import br.com.ricardofigueiredo.gateway.cobranca.dto.EstornoRequest;
import br.com.ricardofigueiredo.gateway.cobranca.dto.EstornoResponse;
import br.com.ricardofigueiredo.gateway.cobranca.dto.EventoResponse;
import br.com.ricardofigueiredo.gateway.comum.PaginaResponse;
import br.com.ricardofigueiredo.gateway.seguranca.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
import java.util.List;

@RestController
@RequestMapping("/api/v1/cobrancas")
@Tag(name = "Cobrancas", description = "Criacao, captura, cancelamento e estorno de cobrancas")
public class CobrancaController {

    private final CobrancaService cobrancaService;

    public CobrancaController(CobrancaService cobrancaService) {
        this.cobrancaService = cobrancaService;
    }

    @PostMapping
    @Operation(summary = "Cria uma cobranca e roda a autorizacao",
            description = """
                    Envie o cabecalho Idempotency-Key para garantir que uma nova tentativa
                    do aplicativo, apos queda de rede, nao gere uma segunda cobranca. Quando a
                    chave ja foi usada, a cobranca original volta com status 200.""")
    public ResponseEntity<CobrancaResponse> criar(
            @AuthenticationPrincipal UsuarioAutenticado autenticado,
            @Parameter(description = "Chave que identifica a tentativa, opcional")
            @RequestHeader(name = "Idempotency-Key", required = false) String chaveIdempotencia,
            @Valid @RequestBody CriarCobrancaRequest requisicao) {

        var resultado = cobrancaService.criar(autenticado.getUsuario(), requisicao, chaveIdempotencia);
        CobrancaResponse corpo = CobrancaResponse.de(resultado.cobranca());

        if (resultado.recuperadaPorIdempotencia()) {
            return ResponseEntity.ok(corpo);
        }
        return ResponseEntity.created(URI.create("/api/v1/cobrancas/" + corpo.codigo())).body(corpo);
    }

    @GetMapping
    @Operation(summary = "Lista as cobrancas do estabelecimento, da mais recente para a mais antiga")
    public PaginaResponse<CobrancaResponse> listar(
            @AuthenticationPrincipal UsuarioAutenticado autenticado,
            @RequestParam(required = false) StatusCobranca status,
            @RequestParam(required = false) MetodoPagamento metodo,
            @PageableDefault(size = 20, sort = "criadoEm", direction = Sort.Direction.DESC) Pageable paginacao) {

        Page<Cobranca> pagina = cobrancaService.listar(autenticado.getUsuario(), status, metodo, paginacao);
        return PaginaResponse.de(pagina, CobrancaResponse::de);
    }

    @GetMapping("/{codigo}")
    @Operation(summary = "Consulta uma cobranca pelo codigo")
    public CobrancaResponse buscar(@AuthenticationPrincipal UsuarioAutenticado autenticado,
                                   @PathVariable String codigo) {
        return CobrancaResponse.de(cobrancaService.buscar(autenticado.getUsuario(), codigo));
    }

    @PostMapping("/{codigo}/captura")
    @Operation(summary = "Captura uma cobranca autorizada")
    public CobrancaResponse capturar(@AuthenticationPrincipal UsuarioAutenticado autenticado,
                                     @PathVariable String codigo) {
        return CobrancaResponse.de(cobrancaService.capturar(autenticado.getUsuario(), codigo));
    }

    @PostMapping("/{codigo}/cancelamento")
    @Operation(summary = "Cancela uma cobranca autorizada que ainda nao foi capturada")
    public CobrancaResponse cancelar(@AuthenticationPrincipal UsuarioAutenticado autenticado,
                                     @PathVariable String codigo) {
        return CobrancaResponse.de(cobrancaService.cancelar(autenticado.getUsuario(), codigo));
    }

    @PostMapping("/{codigo}/estornos")
    @Operation(summary = "Estorna a cobranca no todo ou em parte",
            description = "Omitir valorEmCentavos estorna todo o saldo ainda disponivel.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Estorno registrado")
    public ResponseEntity<EstornoResponse> estornar(
            @AuthenticationPrincipal UsuarioAutenticado autenticado,
            @PathVariable String codigo,
            @Valid @RequestBody(required = false) EstornoRequest requisicao) {

        EstornoRequest corpo = requisicao == null ? new EstornoRequest(null, null) : requisicao;
        Estorno estorno = cobrancaService.estornar(autenticado.getUsuario(), codigo, corpo);

        return ResponseEntity.status(HttpStatus.CREATED).body(EstornoResponse.de(estorno));
    }

    @GetMapping("/{codigo}/estornos")
    @Operation(summary = "Lista os estornos ja registrados na cobranca")
    public List<EstornoResponse> estornos(@AuthenticationPrincipal UsuarioAutenticado autenticado,
                                          @PathVariable String codigo) {
        return cobrancaService.estornos(autenticado.getUsuario(), codigo).stream()
                .map(EstornoResponse::de)
                .toList();
    }

    @GetMapping("/{codigo}/eventos")
    @Operation(summary = "Linha do tempo da cobranca, do mais antigo para o mais recente")
    public List<EventoResponse> eventos(@AuthenticationPrincipal UsuarioAutenticado autenticado,
                                        @PathVariable String codigo) {
        return cobrancaService.eventos(autenticado.getUsuario(), codigo).stream()
                .map(EventoResponse::de)
                .toList();
    }
}
