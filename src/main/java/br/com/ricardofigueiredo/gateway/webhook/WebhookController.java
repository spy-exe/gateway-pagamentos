package br.com.ricardofigueiredo.gateway.webhook;

import br.com.ricardofigueiredo.gateway.comum.PaginaResponse;
import br.com.ricardofigueiredo.gateway.seguranca.UsuarioAutenticado;
import br.com.ricardofigueiredo.gateway.webhook.dto.CriarEndpointRequest;
import br.com.ricardofigueiredo.gateway.webhook.dto.EndpointResponse;
import br.com.ricardofigueiredo.gateway.webhook.dto.EntregaResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/webhooks")
@Tag(name = "Webhooks", description = "Endpoints que recebem os eventos das cobrancas")
public class WebhookController {

    private final WebhookService webhookService;

    public WebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping
    @Operation(summary = "Cadastra um endpoint",
            description = """
                    A resposta traz o segredo por inteiro, e e a unica vez que isso acontece.
                    Guarde-o: e com ele que o cabecalho Aval-Assinatura e conferido do outro lado.""")
    public ResponseEntity<EndpointResponse> cadastrar(
            @AuthenticationPrincipal UsuarioAutenticado autenticado,
            @Valid @RequestBody CriarEndpointRequest requisicao) {

        EndpointWebhook endpoint = webhookService.cadastrar(autenticado.getUsuario(), requisicao);
        return ResponseEntity.status(HttpStatus.CREATED).body(EndpointResponse.comSegredo(endpoint));
    }

    @GetMapping
    @Operation(summary = "Lista os endpoints cadastrados, com o segredo mascarado")
    public List<EndpointResponse> listar(@AuthenticationPrincipal UsuarioAutenticado autenticado) {
        return webhookService.listar(autenticado.getUsuario()).stream()
                .map(EndpointResponse::mascarado)
                .toList();
    }

    @PostMapping("/{codigo}/situacao")
    @Operation(summary = "Liga ou desliga o envio para um endpoint")
    public EndpointResponse alternar(@AuthenticationPrincipal UsuarioAutenticado autenticado,
                                     @PathVariable String codigo,
                                     @RequestParam boolean ativo) {
        return EndpointResponse.mascarado(webhookService.alternar(autenticado.getUsuario(), codigo, ativo));
    }

    @DeleteMapping("/{codigo}")
    @Operation(summary = "Remove o endpoint e o historico de entregas dele")
    public ResponseEntity<Void> remover(@AuthenticationPrincipal UsuarioAutenticado autenticado,
                                        @PathVariable String codigo) {
        webhookService.remover(autenticado.getUsuario(), codigo);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{codigo}/entregas")
    @Operation(summary = "Historico de entregas do endpoint, da mais recente para a mais antiga")
    public PaginaResponse<EntregaResponse> entregas(
            @AuthenticationPrincipal UsuarioAutenticado autenticado,
            @PathVariable String codigo,
            @PageableDefault(size = 20) Pageable paginacao) {

        return PaginaResponse.de(
                webhookService.entregas(autenticado.getUsuario(), codigo, paginacao), EntregaResponse::de);
    }

    @PostMapping("/entregas/{codigo}/reenvio")
    @Operation(summary = "Devolve uma entrega para a fila",
            description = "Serve para quando o endpoint estava fora do ar e ja voltou.")
    public EntregaResponse reenviar(@AuthenticationPrincipal UsuarioAutenticado autenticado,
                                    @PathVariable String codigo) {
        return EntregaResponse.de(webhookService.reenviar(autenticado.getUsuario(), codigo));
    }
}
