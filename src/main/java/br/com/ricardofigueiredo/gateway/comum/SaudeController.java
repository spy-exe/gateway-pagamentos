package br.com.ricardofigueiredo.gateway.comum;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@Tag(name = "Servico", description = "Verificacao de disponibilidade")
public class SaudeController {

    @GetMapping("/saude")
    @Operation(summary = "Responde se a API esta no ar")
    public Map<String, Object> saude() {
        return Map.of("status", "no ar", "instante", Instant.now());
    }
}
