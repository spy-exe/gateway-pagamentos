package br.com.ricardofigueiredo.gateway.usuario;

import br.com.ricardofigueiredo.gateway.seguranca.UsuarioAutenticado;
import br.com.ricardofigueiredo.gateway.usuario.dto.LoginRequest;
import br.com.ricardofigueiredo.gateway.usuario.dto.RegistroRequest;
import br.com.ricardofigueiredo.gateway.usuario.dto.TokenResponse;
import br.com.ricardofigueiredo.gateway.usuario.dto.UsuarioResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/autenticacao")
@Tag(name = "Autenticacao", description = "Cadastro do estabelecimento e emissao de token")
public class AutenticacaoController {

    private final AutenticacaoService autenticacaoService;

    public AutenticacaoController(AutenticacaoService autenticacaoService) {
        this.autenticacaoService = autenticacaoService;
    }

    @PostMapping("/registro")
    @Operation(summary = "Cadastra um estabelecimento")
    public ResponseEntity<UsuarioResponse> registrar(@Valid @RequestBody RegistroRequest requisicao) {
        Usuario usuario = autenticacaoService.registrar(requisicao);
        return ResponseEntity.status(HttpStatus.CREATED).body(UsuarioResponse.de(usuario));
    }

    @PostMapping("/login")
    @Operation(summary = "Troca e-mail e senha por um token JWT")
    public TokenResponse entrar(@Valid @RequestBody LoginRequest requisicao) {
        return autenticacaoService.autenticar(requisicao);
    }

    @GetMapping("/eu")
    @Operation(summary = "Devolve os dados do estabelecimento dono do token")
    public UsuarioResponse eu(@AuthenticationPrincipal UsuarioAutenticado autenticado) {
        return UsuarioResponse.de(autenticado.getUsuario());
    }
}
