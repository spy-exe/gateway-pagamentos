package br.com.ricardofigueiredo.gateway.webhook;

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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O entregador fica desligado aqui: o que se prova neste teste e que o evento
 * entra na caixa de saida na mesma transacao da cobranca. A entrega pela rede
 * tem prova propria, nos testes de unidade da espera e da assinatura.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "gateway.webhook.intervalo-ms=3600000")
class WebhookFluxoIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("o cadastro devolve o segredo por inteiro e a listagem devolve mascarado")
    void segredoApareceUmaVezSo() throws Exception {
        String token = autenticar();

        MvcResult cadastro = cadastrarEndpoint(token, "https://exemplo.com/eventos");
        String segredo = ler(cadastro).get("segredo").asText();

        assertThat(segredo).startsWith("whsec_").hasSizeGreaterThan(40);

        mockMvc.perform(get("/api/v1/webhooks").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].segredo").value(segredo.substring(0, 10) + "..."))
                .andExpect(jsonPath("$[0].ativo").value(true));
    }

    @Test
    @DisplayName("criar cobranca enfileira o evento para todos os endpoints ativos")
    void cobrancaEnfileiraEvento() throws Exception {
        String token = autenticar();
        String codigoDoEndpoint = ler(cadastrarEndpoint(token, "https://exemplo.com/eventos"))
                .get("codigo").asText();

        criarCobrancaPix(token);

        mockMvc.perform(get("/api/v1/webhooks/" + codigoDoEndpoint + "/entregas")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDeItens").value(1))
                .andExpect(jsonPath("$.itens[0].evento").value("cobranca.capturada"))
                .andExpect(jsonPath("$.itens[0].situacao").value("PENDENTE"))
                .andExpect(jsonPath("$.itens[0].tentativas").value(0))
                .andExpect(jsonPath("$.itens[0].corpo").isNotEmpty());
    }

    @Test
    @DisplayName("endpoint desligado para de receber evento")
    void endpointDesligadoNaoRecebe() throws Exception {
        String token = autenticar();
        String codigo = ler(cadastrarEndpoint(token, "https://exemplo.com/eventos")).get("codigo").asText();

        mockMvc.perform(post("/api/v1/webhooks/" + codigo + "/situacao")
                        .param("ativo", "false")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));

        criarCobrancaPix(token);

        mockMvc.perform(get("/api/v1/webhooks/" + codigo + "/entregas")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.totalDeItens").value(0));
    }

    @Test
    @DisplayName("a entrega pode voltar para a fila por reenvio manual")
    void reenvioVoltaParaAFila() throws Exception {
        String token = autenticar();
        String codigoDoEndpoint = ler(cadastrarEndpoint(token, "https://exemplo.com/eventos"))
                .get("codigo").asText();
        criarCobrancaPix(token);

        MvcResult entregas = mockMvc.perform(get("/api/v1/webhooks/" + codigoDoEndpoint + "/entregas")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andReturn();
        String codigoDaEntrega = ler(entregas).get("itens").get(0).get("codigo").asText();

        mockMvc.perform(post("/api/v1/webhooks/entregas/" + codigoDaEntrega + "/reenvio")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("PENDENTE"));
    }

    @Test
    @DisplayName("entrega de outro estabelecimento nao pode ser reenviada")
    void reenvioDeOutroEstabelecimentoNaoPassa() throws Exception {
        String tokenDoDono = autenticar();
        String codigoDoEndpoint = ler(cadastrarEndpoint(tokenDoDono, "https://exemplo.com/eventos"))
                .get("codigo").asText();
        criarCobrancaPix(tokenDoDono);

        MvcResult entregas = mockMvc.perform(get("/api/v1/webhooks/" + codigoDoEndpoint + "/entregas")
                .header(HttpHeaders.AUTHORIZATION, tokenDoDono)).andReturn();
        String codigoDaEntrega = ler(entregas).get("itens").get(0).get("codigo").asText();

        String tokenDeOutro = autenticar();

        mockMvc.perform(post("/api/v1/webhooks/entregas/" + codigoDaEntrega + "/reenvio")
                        .header(HttpHeaders.AUTHORIZATION, tokenDeOutro))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("o sexto endpoint e recusado")
    void limitePorEstabelecimento() throws Exception {
        String token = autenticar();

        for (int indice = 0; indice < 5; indice++) {
            cadastrarEndpoint(token, "https://exemplo.com/eventos/" + indice);
        }

        mockMvc.perform(post("/api/v1/webhooks")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "https://exemplo.com/sobrando"}"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("no maximo 5")));
    }

    @Test
    @DisplayName("URL sem esquema http e recusada na validacao")
    void urlPrecisaDeEsquema() throws Exception {
        String token = autenticar();

        mockMvc.perform(post("/api/v1/webhooks")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "exemplo.com/eventos"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.url").isNotEmpty());
    }

    @Test
    @DisplayName("remover o endpoint leva junto o historico de entregas")
    void removerLevaOHistorico() throws Exception {
        String token = autenticar();
        String codigo = ler(cadastrarEndpoint(token, "https://exemplo.com/eventos")).get("codigo").asText();
        criarCobrancaPix(token);

        mockMvc.perform(delete("/api/v1/webhooks/" + codigo).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/webhooks").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("endpoint inexistente devolve 404")
    void endpointInexistente() throws Exception {
        String token = autenticar();

        mockMvc.perform(get("/api/v1/webhooks/whk_naoexiste/entregas")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound());
    }

    private MvcResult cadastrarEndpoint(String token, String url) throws Exception {
        return mockMvc.perform(post("/api/v1/webhooks")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "%s", "descricao": "ambiente de teste"}""".formatted(url)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private void criarCobrancaPix(String token) throws Exception {
        mockMvc.perform(post("/api/v1/cobrancas")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"valorEmCentavos": 4990, "descricao": "Cafe", "metodo": "PIX"}"""))
                .andExpect(status().isCreated());
    }

    private String autenticar() throws Exception {
        String email = "webhook-" + UUID.randomUUID() + "@exemplo.com";

        mockMvc.perform(post("/api/v1/autenticacao/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "senha": "senhaforte123", "nomeEstabelecimento": "Loja"}"""
                                .formatted(email)))
                .andExpect(status().isCreated());

        MvcResult login = mockMvc.perform(post("/api/v1/autenticacao/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "senha": "senhaforte123"}""".formatted(email)))
                .andReturn();

        return "Bearer " + ler(login).get("token").asText();
    }

    private JsonNode ler(MvcResult resultado) throws Exception {
        return objectMapper.readTree(resultado.getResponse().getContentAsString());
    }
}
