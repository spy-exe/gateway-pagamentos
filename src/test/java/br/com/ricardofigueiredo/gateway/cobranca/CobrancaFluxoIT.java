package br.com.ricardofigueiredo.gateway.cobranca;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercita a API de ponta a ponta pelo protocolo HTTP, com banco H2 em memoria
 * criado pelas mesmas migrations do Flyway que rodam em producao.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CobrancaFluxoIT {

    private static final String CARTAO_APROVADO = "4111111111111111";
    private static final String CARTAO_BLOQUEADO = "4111000000080000";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("registro devolve o estabelecimento criado e o login devolve token utilizavel")
    void registroELogin() throws Exception {
        String token = autenticar();

        mockMvc.perform(get("/api/v1/autenticacao/eu").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomeEstabelecimento").value("Padaria do Ricardo"));
    }

    @Test
    @DisplayName("requisicao sem token recebe 401 no formato de problema")
    void semTokenRecebe401() throws Exception {
        mockMvc.perform(get("/api/v1/cobrancas"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Nao autenticado"));
    }

    @Test
    @DisplayName("cobranca no Pix e aprovada e capturada na mesma requisicao")
    void pixCapturaDireto() throws Exception {
        String token = autenticar();

        mockMvc.perform(post("/api/v1/cobrancas")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"valorEmCentavos": 4990, "descricao": "Cafe e pao na chapa", "metodo": "PIX"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CAPTURADA"))
                .andExpect(jsonPath("$.codigoAutorizacao").isNotEmpty())
                .andExpect(jsonPath("$.cartao").doesNotExist());
    }

    @Test
    @DisplayName("cartao com captura manual passa por autorizacao, captura e estorno total")
    void fluxoCompletoDeCartao() throws Exception {
        String token = autenticar();
        String codigo = criarComCartao(token, CARTAO_APROVADO, 30_000L, false);

        mockMvc.perform(get("/api/v1/cobrancas/" + codigo).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.status").value("AUTORIZADA"))
                .andExpect(jsonPath("$.cartao.bandeira").value("VISA"))
                .andExpect(jsonPath("$.cartao.ultimosQuatro").value("1111"));

        mockMvc.perform(post("/api/v1/cobrancas/" + codigo + "/captura")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CAPTURADA"));

        mockMvc.perform(post("/api/v1/cobrancas/" + codigo + "/estornos")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"valorEmCentavos": 10000, "motivo": "item devolvido"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.valorEmCentavos").value(10000));

        mockMvc.perform(get("/api/v1/cobrancas/" + codigo).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.status").value("PARCIALMENTE_ESTORNADA"))
                .andExpect(jsonPath("$.saldoEstornavelEmCentavos").value(20000));

        mockMvc.perform(post("/api/v1/cobrancas/" + codigo + "/estornos")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/cobrancas/" + codigo).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.status").value("ESTORNADA"))
                .andExpect(jsonPath("$.saldoEstornavelEmCentavos").value(0));
    }

    @Test
    @DisplayName("cartao bloqueado gera cobranca recusada com o motivo preenchido")
    void cartaoBloqueado() throws Exception {
        String token = autenticar();

        mockMvc.perform(post("/api/v1/cobrancas")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoComCartao(CARTAO_BLOQUEADO, 10_000L, true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RECUSADA"))
                .andExpect(jsonPath("$.motivoRecusa").value("CARTAO_BLOQUEADO"))
                .andExpect(jsonPath("$.descricaoDaRecusa").isNotEmpty());
    }

    @Test
    @DisplayName("capturar cobranca ja capturada devolve 422 sem alterar o estado")
    void capturaInvalida() throws Exception {
        String token = autenticar();
        String codigo = criarComCartao(token, CARTAO_APROVADO, 5_000L, true);

        mockMvc.perform(post("/api/v1/cobrancas/" + codigo + "/captura")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Operacao nao permitida"));
    }

    @Test
    @DisplayName("mesma chave de idempotencia nao gera uma segunda cobranca")
    void idempotencia() throws Exception {
        String token = autenticar();
        String chave = UUID.randomUUID().toString();
        String corpo = corpoComCartao(CARTAO_APROVADO, 7_500L, true);

        MvcResult primeira = mockMvc.perform(post("/api/v1/cobrancas")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .header("Idempotency-Key", chave)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult segunda = mockMvc.perform(post("/api/v1/cobrancas")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .header("Idempotency-Key", chave)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(codigoDe(segunda)).isEqualTo(codigoDe(primeira));

        mockMvc.perform(get("/api/v1/cobrancas").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.totalDeItens").value(1));
    }

    @Test
    @DisplayName("um estabelecimento nao enxerga a cobranca de outro")
    void isolamentoEntreEstabelecimentos() throws Exception {
        String tokenDaPadaria = autenticar();
        String codigo = criarComCartao(tokenDaPadaria, CARTAO_APROVADO, 9_000L, true);

        String tokenDaLanchonete = autenticar();

        mockMvc.perform(get("/api/v1/cobrancas/" + codigo).header(HttpHeaders.AUTHORIZATION, tokenDaLanchonete))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Recurso nao encontrado"));
    }

    @Test
    @DisplayName("valor invalido devolve 400 apontando o campo")
    void validacaoDeValor() throws Exception {
        String token = autenticar();

        mockMvc.perform(post("/api/v1/cobrancas")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"valorEmCentavos": -10, "descricao": "teste", "metodo": "PIX"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.valorEmCentavos").value("o valor deve ser maior que zero"));
    }

    @Test
    @DisplayName("Pix acompanhado de dados de cartao e recusado na validacao")
    void pixComCartaoNaoPassa() throws Exception {
        String token = autenticar();

        mockMvc.perform(post("/api/v1/cobrancas")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "valorEmCentavos": 1000,
                                  "descricao": "teste",
                                  "metodo": "PIX",
                                  "cartao": {
                                    "numero": "4111111111111111",
                                    "validadeMes": 12,
                                    "validadeAno": 2030,
                                    "nomePortador": "Ricardo Figueiredo"
                                  }
                                }"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("numero de cartao com digito verificador errado devolve 422")
    void cartaoInvalido() throws Exception {
        String token = autenticar();

        mockMvc.perform(post("/api/v1/cobrancas")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoComCartao("4111111111111112", 1_000L, true)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("linha do tempo registra cada transicao da cobranca")
    void linhaDoTempo() throws Exception {
        String token = autenticar();
        String codigo = criarComCartao(token, CARTAO_APROVADO, 12_000L, false);

        mockMvc.perform(post("/api/v1/cobrancas/" + codigo + "/captura")
                .header(HttpHeaders.AUTHORIZATION, token));
        mockMvc.perform(post("/api/v1/cobrancas/" + codigo + "/estornos")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"));

        mockMvc.perform(get("/api/v1/cobrancas/" + codigo + "/eventos")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].tipo").value("AUTORIZACAO"))
                .andExpect(jsonPath("$[1].tipo").value("CAPTURA"))
                .andExpect(jsonPath("$[2].tipo").value("ESTORNO"))
                .andExpect(jsonPath("$[2].statusNovo").value("ESTORNADA"));
    }

    @Test
    @DisplayName("listagem aceita filtro por status")
    void listagemFiltrada() throws Exception {
        String token = autenticar();
        criarComCartao(token, CARTAO_APROVADO, 1_000L, true);
        criarComCartao(token, CARTAO_BLOQUEADO, 2_000L, true);

        mockMvc.perform(get("/api/v1/cobrancas").param("status", "RECUSADA")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDeItens").value(1))
                .andExpect(jsonPath("$.itens[0].status").value("RECUSADA"));
    }

    private String autenticar() throws Exception {
        String email = "loja-" + UUID.randomUUID() + "@exemplo.com";

        mockMvc.perform(post("/api/v1/autenticacao/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "senha": "senhaforte123", "nomeEstabelecimento": "Padaria do Ricardo"}"""
                                .formatted(email)))
                .andExpect(status().isCreated());

        MvcResult login = mockMvc.perform(post("/api/v1/autenticacao/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "senha": "senhaforte123"}""".formatted(email)))
                .andExpect(status().isOk())
                .andReturn();

        return "Bearer " + ler(login).get("token").asText();
    }

    private String criarComCartao(String token, String numero, long valor, boolean capturaAutomatica)
            throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/v1/cobrancas")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoComCartao(numero, valor, capturaAutomatica)))
                .andExpect(status().isCreated())
                .andReturn();

        return codigoDe(resultado);
    }

    private String corpoComCartao(String numero, long valor, boolean capturaAutomatica) {
        return """
                {
                  "valorEmCentavos": %d,
                  "descricao": "Pedido de teste",
                  "metodo": "CARTAO_CREDITO",
                  "capturaAutomatica": %b,
                  "cartao": {
                    "numero": "%s",
                    "validadeMes": 12,
                    "validadeAno": 2030,
                    "nomePortador": "Ricardo Figueiredo"
                  }
                }""".formatted(valor, capturaAutomatica, numero);
    }

    private String codigoDe(MvcResult resultado) throws Exception {
        return ler(resultado).get("codigo").asText();
    }

    private JsonNode ler(MvcResult resultado) throws Exception {
        return objectMapper.readTree(resultado.getResponse().getContentAsString());
    }
}
