package br.com.consultafipe.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.consultafipe.controller.PaginaController;
import br.com.consultafipe.exception.LimiteDeUsoExcedidoException;
import br.com.consultafipe.exception.RecursoNaoEncontradoException;
import br.com.consultafipe.fixture.DominioFixtures;
import br.com.consultafipe.model.domain.TipoVeiculo;
import br.com.consultafipe.model.service.FipeService;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Renderiza os templates Thymeleaf de verdade e confere que os dados aparecem no HTML. */
@WebMvcTest(PaginaController.class)
class TemplatesTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private FipeService service;

    @Nested
    class PaginaInicial {

        @Test
        void deveUsarOLayoutComCssJavaScriptEIdioma() throws Exception {
            String html = renderizar(get("/"));

            assertThat(html)
                    .contains("<html lang=\"pt-BR\">")
                    .contains("<title>Consulta FIPE</title>")
                    .contains("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
                    .contains("<link rel=\"stylesheet\" href=\"/css/styles.css\">")
                    .contains("<script src=\"/js/consulta.js\" defer></script>")
                    .contains("class=\"cabecalho\"")
                    .contains("class=\"rodape\"");
        }

        @Test
        void deveTrazerOTipoPreenchidoPeloServidorEAsDemaisEtapasDesabilitadas() throws Exception {
            String html = renderizar(get("/"));

            assertThat(tag(html, "select", "tipo")).doesNotContain("disabled");
            assertThat(html).contains("<option value=\"carros\">Carros</option>",
                    "<option value=\"motos\">Motos</option>",
                    "<option value=\"caminhoes\">Caminhões</option>");
            assertThat(tag(html, "input", "marca")).contains("disabled");
            assertThat(tag(html, "input", "modelo")).contains("disabled");
            assertThat(tag(html, "select", "ano")).contains("disabled");
        }

        @ParameterizedTest
        @CsvSource({"marca", "modelo"})
        void deveTerUmaCaixaDeBuscaComListaNoPadraoCombobox(String campo) throws Exception {
            String html = renderizar(get("/"));

            assertThat(tag(html, "input", campo)).contains(
                    "type=\"text\"",
                    "role=\"combobox\"",
                    "aria-autocomplete=\"list\"",
                    "aria-expanded=\"false\"",
                    "aria-controls=\"" + campo + "-lista\"",
                    "aria-describedby=\"" + campo + "-status\"",
                    "autocomplete=\"off\"");
            assertThat(tag(html, "ul", campo + "-lista")).contains(
                    "role=\"listbox\"", "aria-labelledby=\"" + campo + "-rotulo\"", "hidden");
            assertThat(tag(html, "label", campo + "-rotulo")).contains("for=\"" + campo + "\"");
            assertThat(html).doesNotContain(campo + "-filtro");
        }

        @Test
        void deveInformarAoJavaScriptAsRotasDaAplicacao() throws Exception {
            String html = renderizar(get("/"));

            assertThat(tag(html, "form", "consulta"))
                    .contains("data-api-base=\"/api\"")
                    .contains("data-consulta-base=\"/consulta\"");
            assertThat(tag(html, "section", "resultado")).contains("aria-live=\"polite\"");
        }
    }

    @Nested
    class FragmentoDoResultado {

        @ParameterizedTest(name = "{0}: {1}")
        @CsvSource(delimiter = '|', value = {
                "Tipo              | Carros",
                "Marca             | Fiat",
                "Modelo            | ARGO DRIVE 1.0 6V Flex",
                "Ano-modelo        | 2018",
                "Combustível       | Flex",
                "Código FIPE       | 001494-0"})
        void deveMostrarCadaDetalheDoVeiculoNoCard(String rotulo, String valor) throws Exception {
            when(service.buscarPreco(TipoVeiculo.CARROS, "21", "7965", "2018-5"))
                    .thenReturn(DominioFixtures.precoArgo2018());

            String html = renderizar(get("/consulta/carros/21/7965/2018-5"));

            assertThat(html).containsPattern(
                    "<dt>" + Pattern.quote(rotulo) + "</dt>\\s*<dd[^>]*>" + Pattern.quote(valor) + "</dd>");
        }

        @Test
        void deveDestacarOPrecoEOTipoDoVeiculo() throws Exception {
            when(service.buscarPreco(TipoVeiculo.CARROS, "21", "7965", "2018-5"))
                    .thenReturn(DominioFixtures.precoArgo2018());

            String html = renderizar(get("/consulta/carros/21/7965/2018-5"));

            assertThat(html)
                    .startsWith("<article class=\"resultado\"")
                    .contains("<p class=\"resultado__preco\">R$ 48.136,00</p>")
                    .contains("<dd class=\"resultado__tipo\">Carros</dd>")
                    .doesNotContain("<html", "<head>", "<body");
        }

        @Test
        void deveExplicarOMesDeReferenciaJuntoDoPreco() throws Exception {
            when(service.buscarPreco(TipoVeiculo.CARROS, "21", "7965", "2018-5"))
                    .thenReturn(DominioFixtures.precoArgo2018());

            String html = renderizar(get("/consulta/carros/21/7965/2018-5"));

            assertThat(html)
                    .contains("Média de mercado em <span>novembro de 2026</span>,")
                    .contains("não o valor de um veículo específico.");
        }

        @Test
        void deveRenderizarOFragmentoDeErroComAMensagem() throws Exception {
            when(service.buscarPreco(TipoVeiculo.CARROS, "21", "7965", "2018-5"))
                    .thenThrow(new RecursoNaoEncontradoException("FIPE respondeu 404"));

            String html = mvc.perform(get("/consulta/carros/21/7965/2018-5"))
                    .andExpect(status().isNotFound())
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

            assertThat(html)
                    .startsWith("<div class=\"resultado resultado--erro\" role=\"alert\" data-gravidade=\"falha\">")
                    .contains("<p class=\"resultado__titulo\">Não encontrado</p>")
                    .contains("Não encontramos esse veículo na Tabela FIPE");
        }

        @Test
        void deveMarcarComoTemporarioOErroDeCotaEsgotada() throws Exception {
            when(service.buscarPreco(TipoVeiculo.CARROS, "21", "7965", "2018-5"))
                    .thenThrow(new LimiteDeUsoExcedidoException("FIPE respondeu 429", Duration.ofHours(3)));

            String html = mvc.perform(get("/consulta/carros/21/7965/2018-5"))
                    .andExpect(status().isServiceUnavailable())
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

            assertThat(html)
                    .startsWith("<div class=\"resultado resultado--erro\" role=\"alert\" data-gravidade=\"temporaria\">")
                    .contains("<p class=\"resultado__titulo\">Cota diária esgotada</p>");
        }
    }

    @Nested
    class PaginaDeErro {

        @Test
        void deveRenderizarAPaginaDeErroComOLayout() throws Exception {
            String html = mvc.perform(get("/rota-inexistente"))
                    .andExpect(status().isNotFound())
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

            assertThat(html)
                    .contains("<title>Página não encontrada · Consulta FIPE</title>")
                    .contains("<p class=\"erro__status\">404</p>")
                    .contains("<h1 class=\"erro__titulo\">Página não encontrada</h1>")
                    .contains("<a class=\"botao\" href=\"/\">Voltar para a consulta</a>");
        }

        @Test
        void deveTerTextosPadraoQuandoRenderizadaPeloErrorDoSpringBoot() throws Exception {
            String html = mvc.perform(get("/error")
                            .accept(MediaType.TEXT_HTML)
                            .requestAttr("jakarta.servlet.error.status_code", 500))
                    .andExpect(status().isInternalServerError())
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

            assertThat(html)
                    .contains("<title>Erro · Consulta FIPE</title>")
                    .contains("<h1 class=\"erro__titulo\">A página falhou</h1>")
                    .contains("Um erro do nosso lado interrompeu a página. Volte para a consulta e tente de novo.");
        }
    }

    @Nested
    class ArquivosEstaticos {

        @ParameterizedTest
        @CsvSource({"/css/styles.css, text/css", "/js/consulta.js, text/javascript", "/favicon.svg, image/svg+xml",
                "/fonts/barlow-400.woff2, font/woff2", "/fonts/barlow-semi-condensed-600.woff2, font/woff2"})
        void deveServirOsArquivosEstaticos(String caminho, String tipo) throws Exception {
            mvc.perform(get(caminho))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(tipo));
        }
    }

    private String renderizar(MockHttpServletRequestBuilder pedido) throws Exception {
        return mvc.perform(pedido)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    /** Devolve a tag de abertura do elemento com o id informado (ex.: {@code <select id="marca" ...>}). */
    private static String tag(String html, String elemento, String id) {
        Matcher encontrado = Pattern.compile("<" + elemento + "\\b[^>]*\\bid=\"" + Pattern.quote(id) + "\"[^>]*>")
                .matcher(html);
        assertThat(encontrado.find()).as("<%s id=\"%s\"> no HTML", elemento, id).isTrue();
        return encontrado.group();
    }
}
