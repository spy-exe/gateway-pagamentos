package br.com.ricardofigueiredo.gateway.linkpagamento;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "gateway.webhook.intervalo-ms=3600000")
class LinkPagamentoFluxoIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("o estabelecimento cria e lista um link pronto para compartilhar")
    void criaELista() throws Exception {
        String token = autenticar();

        MvcResult criado = criarLink(token, "PIX", 1, null);
        String codigo = ler(criado).get("codigo").asText();

        assertThat(codigo).startsWith("link_");
        mockMvc.perform(get("/api/v1/links-pagamento").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].codigo").value(codigo))
                .andExpect(jsonPath("$[0].situacao").value("ATIVO"))
                .andExpect(jsonPath("$[0].usos").value(0));
    }

    @Test
    @DisplayName("o checkout Pix e publico e devolve a cobranca com BR Code")
    void checkoutPixPublico() throws Exception {
        String codigo = ler(criarLink(autenticar(), "PIX", 1, null)).get("codigo").asText();

        mockMvc.perform(get("/api/v1/links-pagamento/publicos/" + codigo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estabelecimento").value("Mercearia Sao Jorge"))
                .andExpect(jsonPath("$.situacao").value("ATIVO"));

        mockMvc.perform(post("/api/v1/links-pagamento/publicos/" + codigo + "/finalizacao")
                        .header("Idempotency-Key", "checkout-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CAPTURADA"))
                .andExpect(jsonPath("$.pixCopiaECola").isNotEmpty())
                .andExpect(jsonPath("$.codigoDoLinkPagamento").value(codigo));
    }

    @Test
    @DisplayName("a origem do link fica disponivel na listagem fora da sessao do banco")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void listaOrigemSemOpenSessionInView() throws Exception {
        String token = autenticar();
        String codigo = ler(criarLink(token, "PIX", 1, null)).get("codigo").asText();

        mockMvc.perform(post("/api/v1/links-pagamento/publicos/" + codigo + "/finalizacao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/cobrancas").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[0].codigoDoLinkPagamento").value(codigo));
    }

    @Test
    @DisplayName("repetir a chave devolve a mesma cobranca e nao consome outro uso")
    void finalizacaoIdempotente() throws Exception {
        String token = autenticar();
        String codigo = ler(criarLink(token, "CARTAO_CREDITO", 6, 2)).get("codigo").asText();
        String corpo = cartao(3);

        MvcResult primeira = mockMvc.perform(post("/api/v1/links-pagamento/publicos/" + codigo + "/finalizacao")
                        .header("Idempotency-Key", "navegador-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parcelas").value(3))
                .andReturn();

        mockMvc.perform(post("/api/v1/links-pagamento/publicos/" + codigo + "/finalizacao")
                        .header("Idempotency-Key", "navegador-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value(ler(primeira).get("codigo").asText()));

        mockMvc.perform(get("/api/v1/links-pagamento").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$[0].usos").value(1));
    }

    @Test
    @DisplayName("pagamento recusado nao esgota o link e permite nova tentativa")
    void recusaNaoConsomeUso() throws Exception {
        String token = autenticar();
        String codigo = ler(criarLink(token, "CARTAO_CREDITO", 1, 1)).get("codigo").asText();

        mockMvc.perform(post("/api/v1/links-pagamento/publicos/" + codigo + "/finalizacao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cartaoComNumero(1, "4111000000080000")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RECUSADA"));

        mockMvc.perform(get("/api/v1/links-pagamento").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$[0].usos").value(0))
                .andExpect(jsonPath("$[0].situacao").value("ATIVO"));

        mockMvc.perform(post("/api/v1/links-pagamento/publicos/" + codigo + "/finalizacao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cartao(1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CAPTURADA"));
    }

    @Test
    @DisplayName("o checkout respeita o limite de usos e o parcelamento oferecido")
    void limitesDoCheckout() throws Exception {
        String codigo = ler(criarLink(autenticar(), "CARTAO_CREDITO", 2, 1)).get("codigo").asText();

        mockMvc.perform(post("/api/v1/links-pagamento/publicos/" + codigo + "/finalizacao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cartao(3)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("ate 2")));

        mockMvc.perform(post("/api/v1/links-pagamento/publicos/" + codigo + "/finalizacao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cartao(2)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/links-pagamento/publicos/" + codigo + "/finalizacao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cartao(1)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("limite")));
    }

    @Test
    @DisplayName("o dono pausa o link e outro estabelecimento nao consegue altera-lo")
    void pausaProtegidaPeloDono() throws Exception {
        String tokenDoDono = autenticar();
        String codigo = ler(criarLink(tokenDoDono, "PIX", 1, null)).get("codigo").asText();

        mockMvc.perform(post("/api/v1/links-pagamento/" + codigo + "/situacao")
                        .param("ativo", "false")
                        .header(HttpHeaders.AUTHORIZATION, tokenDoDono))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("PAUSADO"));

        mockMvc.perform(post("/api/v1/links-pagamento/" + codigo + "/situacao")
                        .param("ativo", "true")
                        .header(HttpHeaders.AUTHORIZATION, autenticar()))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/links-pagamento/publicos/" + codigo + "/finalizacao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("pausado")));
    }

    @Test
    @DisplayName("um link Pix nao pode anunciar parcelas")
    void pixParceladoNaoPassa() throws Exception {
        mockMvc.perform(post("/api/v1/links-pagamento")
                        .header(HttpHeaders.AUTHORIZATION, autenticar())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDoLink("PIX", 3, null)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("credito")));
    }

    private MvcResult criarLink(String token, String metodo, int parcelas, Integer limite) throws Exception {
        return mockMvc.perform(post("/api/v1/links-pagamento")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoDoLink(metodo, parcelas, limite)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private String corpoDoLink(String metodo, int parcelas, Integer limite) {
        return """
                {
                  "descricao": "Cesta especial da casa",
                  "valorEmCentavos": 15900,
                  "metodo": "%s",
                  "parcelasMaximas": %d,
                  "limiteDeUsos": %s
                }""".formatted(metodo, parcelas, limite == null ? "null" : limite);
    }

    private String cartao(int parcelas) {
        return cartaoComNumero(parcelas, "4111111111111111");
    }

    private String cartaoComNumero(int parcelas, String numero) {
        return """
                {
                  "parcelas": %d,
                  "cartao": {
                    "numero": "%s",
                    "validadeMes": 12,
                    "validadeAno": 2030,
                    "nomePortador": "Cliente da Mercearia"
                  }
                }""".formatted(parcelas, numero);
    }

    private String autenticar() throws Exception {
        String email = "link-" + UUID.randomUUID() + "@exemplo.com";
        mockMvc.perform(post("/api/v1/autenticacao/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","senha":"senhaforte123",
                                 "nomeEstabelecimento":"Mercearia Sao Jorge",
                                 "chavePix":"%s","cidade":"NITEROI"}""".formatted(email, email)))
                .andExpect(status().isCreated());

        MvcResult login = mockMvc.perform(post("/api/v1/autenticacao/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","senha":"senhaforte123"}""".formatted(email)))
                .andReturn();
        return "Bearer " + ler(login).get("token").asText();
    }

    private JsonNode ler(MvcResult resultado) throws Exception {
        return objectMapper.readTree(resultado.getResponse().getContentAsString());
    }
}
