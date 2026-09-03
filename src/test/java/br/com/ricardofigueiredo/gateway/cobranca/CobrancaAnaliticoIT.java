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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Parcelamento, Pix e os numeros que o painel mostra no topo da listagem. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "gateway.webhook.intervalo-ms=3600000")
class CobrancaAnaliticoIT {

    private static final String CARTAO_APROVADO = "4111111111111111";
    private static final String CARTAO_BLOQUEADO = "4111000000080000";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("cobranca no Pix devolve o copia e cola pronto para o QR")
    void pixDevolveCopiaECola() throws Exception {
        String token = autenticar("loja@exemplo.com", "NITEROI");

        MvcResult resultado = mockMvc.perform(post("/api/v1/cobrancas")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"valorEmCentavos": 4990, "descricao": "Cafe e pao", "metodo": "PIX"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pixCopiaECola").isNotEmpty())
                .andReturn();

        String copiaECola = ler(resultado).get("pixCopiaECola").asText();

        assertThat(copiaECola).startsWith("000201");
        assertThat(copiaECola).contains("br.gov.bcb.pix");
        assertThat(copiaECola).contains("540549.90");
        assertThat(copiaECola).contains("NITEROI");
    }

    @Test
    @DisplayName("cobranca recusada no Pix nao gera copia e cola")
    void pixRecusadoNaoGeraCodigo() throws Exception {
        String token = autenticar("loja@exemplo.com", "NITEROI");

        mockMvc.perform(post("/api/v1/cobrancas")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"valorEmCentavos": 5000000, "descricao": "Acima do limite", "metodo": "PIX"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RECUSADA"))
                .andExpect(jsonPath("$.pixCopiaECola").doesNotExist());
    }

    @Test
    @DisplayName("o parcelamento divide o valor e joga o troco na primeira parcela")
    void parcelamentoDivideOValor() throws Exception {
        String token = autenticar("loja@exemplo.com", "NITEROI");

        mockMvc.perform(post("/api/v1/cobrancas")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoComCartao(CARTAO_APROVADO, 100_00, 3)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parcelas").value(3))
                .andExpect(jsonPath("$.valorDaParcelaEmCentavos").value(3333))
                .andExpect(jsonPath("$.ajusteNaPrimeiraParcelaEmCentavos").value(1));
    }

    @Test
    @DisplayName("Pix nao aceita parcelamento")
    void pixNaoParcela() throws Exception {
        String token = autenticar("loja@exemplo.com", "NITEROI");

        mockMvc.perform(post("/api/v1/cobrancas")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"valorEmCentavos": 30000, "descricao": "Pix", "metodo": "PIX", "parcelas": 3}"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("cartao de credito")));
    }

    @Test
    @DisplayName("parcela abaixo do minimo e recusada")
    void parcelaAbaixoDoMinimo() throws Exception {
        String token = autenticar("loja@exemplo.com", "NITEROI");

        mockMvc.perform(post("/api/v1/cobrancas")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoComCartao(CARTAO_APROVADO, 2000, 12)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("pelo menos")));
    }

    @Test
    @DisplayName("acima de doze parcelas a validacao barra antes da regra")
    void limiteDeParcelas() throws Exception {
        String token = autenticar("loja@exemplo.com", "NITEROI");

        mockMvc.perform(post("/api/v1/cobrancas")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoComCartao(CARTAO_APROVADO, 100_000, 18)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.parcelas").isNotEmpty());
    }

    @Test
    @DisplayName("o resumo soma no banco e nao depende da pagina carregada")
    void resumoSomaNoBanco() throws Exception {
        String token = autenticar("loja@exemplo.com", "NITEROI");

        criar(token, CARTAO_APROVADO, 10_000);
        criar(token, CARTAO_APROVADO, 25_000);
        criar(token, CARTAO_BLOQUEADO, 9_000);

        mockMvc.perform(get("/api/v1/cobrancas/resumo").param("dias", "30")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capturadoEmCentavos").value(35000))
                .andExpect(jsonPath("$.estornadoEmCentavos").value(0))
                .andExpect(jsonPath("$.liquidoEmCentavos").value(35000))
                .andExpect(jsonPath("$.recusadas").value(1))
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.taxaDeAprovacao").value(66.67));
    }

    @Test
    @DisplayName("conta sem movimento devolve resumo zerado, e nao erro")
    void resumoVazio() throws Exception {
        String token = autenticar("loja@exemplo.com", "NITEROI");

        mockMvc.perform(get("/api/v1/cobrancas/resumo").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.taxaDeAprovacao").value(0.0));
    }

    @Test
    @DisplayName("o movimento sai agrupado por dia")
    void movimentoPorDia() throws Exception {
        String token = autenticar("loja@exemplo.com", "NITEROI");
        criar(token, CARTAO_APROVADO, 12_000);
        criar(token, CARTAO_APROVADO, 8_000);

        mockMvc.perform(get("/api/v1/cobrancas/movimento").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].capturadoEmCentavos").value(20000))
                .andExpect(jsonPath("$[0].transacoes").value(2));
    }

    @Test
    @DisplayName("o mix de bandeiras conta apenas o que passou por cartao")
    void mixDeBandeiras() throws Exception {
        String token = autenticar("loja@exemplo.com", "NITEROI");
        criar(token, CARTAO_APROVADO, 10_000);
        criar(token, "5555555555554444", 20_000);

        mockMvc.perform(get("/api/v1/cobrancas/bandeiras").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("a busca livre encontra pela descricao e pelo codigo")
    void buscaLivre() throws Exception {
        String token = autenticar("loja@exemplo.com", "NITEROI");
        criar(token, CARTAO_APROVADO, 10_000);

        mockMvc.perform(get("/api/v1/cobrancas").param("busca", "teste")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDeItens").value(1));

        mockMvc.perform(get("/api/v1/cobrancas").param("busca", "cachorro")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.totalDeItens").value(0));
    }

    @Test
    @DisplayName("o filtro por intervalo de datas recorta a listagem")
    void filtroPorIntervalo() throws Exception {
        String token = autenticar("loja@exemplo.com", "NITEROI");
        criar(token, CARTAO_APROVADO, 10_000);

        mockMvc.perform(get("/api/v1/cobrancas")
                        .param("de", "2000-01-01T00:00:00Z")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.totalDeItens").value(1));

        mockMvc.perform(get("/api/v1/cobrancas")
                        .param("ate", "2000-01-01T00:00:00Z")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.totalDeItens").value(0));
    }

    @Test
    @DisplayName("a chave Pix informada no cadastro vai para o BR Code")
    void chavePixDoCadastro() throws Exception {
        String token = autenticar("11999998888", "SAO GONCALO");

        MvcResult resultado = mockMvc.perform(post("/api/v1/cobrancas")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"valorEmCentavos": 1500, "descricao": "Pao", "metodo": "PIX"}"""))
                .andReturn();

        assertThat(ler(resultado).get("pixCopiaECola").asText())
                .contains("11999998888")
                .contains("SAO GONCALO");
    }

    private void criar(String token, String numero, long valor) throws Exception {
        mockMvc.perform(post("/api/v1/cobrancas")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoComCartao(numero, valor, 1)))
                .andExpect(status().isCreated());
    }

    private String corpoComCartao(String numero, long valor, int parcelas) {
        return """
                {
                  "valorEmCentavos": %d,
                  "descricao": "Pedido de teste",
                  "metodo": "CARTAO_CREDITO",
                  "parcelas": %d,
                  "cartao": {
                    "numero": "%s",
                    "validadeMes": 12,
                    "validadeAno": 2030,
                    "nomePortador": "Ricardo Figueiredo"
                  }
                }""".formatted(valor, parcelas, numero);
    }

    private String autenticar(String chavePix, String cidade) throws Exception {
        String email = "analitico-" + UUID.randomUUID() + "@exemplo.com";

        mockMvc.perform(post("/api/v1/autenticacao/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "senha": "senhaforte123", "nomeEstabelecimento": "Mercearia Sao Jorge",
                                 "chavePix": "%s", "cidade": "%s"}""".formatted(email, chavePix, cidade)))
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
