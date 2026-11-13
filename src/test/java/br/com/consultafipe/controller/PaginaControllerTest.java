package br.com.consultafipe.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import br.com.consultafipe.exception.LimiteDeUsoExcedidoException;
import br.com.consultafipe.exception.RecursoNaoEncontradoException;
import br.com.consultafipe.exception.ServicoExternoIndisponivelException;
import br.com.consultafipe.fixture.DominioFixtures;
import br.com.consultafipe.model.domain.TipoVeiculo;
import br.com.consultafipe.model.service.FipeService;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PaginaController.class)
class PaginaControllerTest {

    private static final String URL_CONSULTA = "/consulta/carros/21/7965/2018-5";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private FipeService service;

    @Nested
    class PaginaInicial {

        @Test
        void deveExibirAPaginaInicialComOsTiposDeVeiculo() throws Exception {
            mvc.perform(get("/"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("index"))
                    .andExpect(model().attribute("tipos", List.of(TipoVeiculo.values())))
                    .andExpect(content().string(containsString("<option value=\"carros\">Carros</option>")))
                    .andExpect(content().string(containsString("<option value=\"caminhoes\">Caminhões</option>")));

            verifyNoInteractions(service);
        }

        @Test
        void deveExibirPaginaDeErroParaEnderecoInexistente() throws Exception {
            mvc.perform(get("/pagina-que-nao-existe"))
                    .andExpect(status().isNotFound())
                    .andExpect(view().name("error"))
                    .andExpect(model().attribute("titulo", "Página não encontrada"))
                    .andExpect(content().string(containsString("Voltar para a consulta")));
        }
    }

    @Nested
    class FragmentoDoResultado {

        @Test
        void deveRenderizarOCardComOPrecoDoDominio() throws Exception {
            when(service.buscarPreco(TipoVeiculo.CARROS, "21", "7965", "2018-5"))
                    .thenReturn(DominioFixtures.precoArgo2018());

            mvc.perform(get(URL_CONSULTA))
                    .andExpect(status().isOk())
                    .andExpect(view().name("fragments/resultado :: card"))
                    .andExpect(model().attribute("preco", DominioFixtures.precoArgo2018()))
                    .andExpect(content().string(containsString("R$ 48.136,00")))
                    .andExpect(content().string(containsString("001494-0")))
                    .andExpect(content().string(not(containsString("<html"))));
        }

        @Test
        void deveExibirZeroKmNoCard() throws Exception {
            when(service.buscarPreco(TipoVeiculo.CARROS, "21", "7965", "32000-5"))
                    .thenReturn(DominioFixtures.precoZeroKm());

            mvc.perform(get("/consulta/carros/21/7965/32000-5"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("<dd>Zero km</dd>")));
        }

        @Test
        void deveEscaparHtmlVindoDosDados() throws Exception {
            when(service.buscarPreco(TipoVeiculo.CARROS, "21", "7965", "2018-5"))
                    .thenReturn(DominioFixtures.precoComModelo("<script>alert(1)</script>"));

            mvc.perform(get(URL_CONSULTA))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")))
                    .andExpect(content().string(not(containsString("<script>"))));
        }
    }

    @Nested
    class ErrosDoFragmento {

        @Test
        void deveDevolverFragmentoDeErroQuandoVeiculoNaoExistir() throws Exception {
            when(service.buscarPreco(TipoVeiculo.CARROS, "21", "7965", "2018-5"))
                    .thenThrow(new RecursoNaoEncontradoException("FIPE respondeu 404 em GET /cars/..."));

            mvc.perform(get(URL_CONSULTA))
                    .andExpect(status().isNotFound())
                    .andExpect(view().name("fragments/resultado :: erro"))
                    .andExpect(content().string(containsString("Não encontramos esse veículo")))
                    .andExpect(content().string(not(containsString("FIPE respondeu"))));
        }

        @Test
        void deveInformarQuandoACotaDiariaAcabar() throws Exception {
            when(service.buscarPreco(TipoVeiculo.CARROS, "21", "7965", "2018-5"))
                    .thenThrow(new LimiteDeUsoExcedidoException("FIPE respondeu 429", Duration.ofHours(3)));

            mvc.perform(get(URL_CONSULTA))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(header().string("Retry-After", "10800"))
                    .andExpect(view().name("fragments/resultado :: erro"))
                    .andExpect(content().string(containsString("A cota diária de consultas")))
                    .andExpect(content().string(containsString("em cerca de 3 horas")));
        }

        @Test
        void deveInformarQuandoAFipeEstiverIndisponivel() throws Exception {
            when(service.buscarPreco(TipoVeiculo.CARROS, "21", "7965", "2018-5"))
                    .thenThrow(ServicoExternoIndisponivelException.temporaria("Timeout em GET /cars/...", null));

            mvc.perform(get(URL_CONSULTA))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(content().string(containsString("indisponível no momento")))
                    .andExpect(content().string(not(containsString("Timeout"))));
        }

        @Test
        void naoDeveExporDetalhesDeErroInesperado() throws Exception {
            when(service.buscarPreco(TipoVeiculo.CARROS, "21", "7965", "2018-5"))
                    .thenThrow(new IllegalStateException("detalhe interno sigiloso"));

            mvc.perform(get(URL_CONSULTA))
                    .andExpect(status().isInternalServerError())
                    .andExpect(view().name("fragments/resultado :: erro"))
                    .andExpect(content().string(containsString("erro inesperado")))
                    .andExpect(content().string(not(containsString("sigiloso"))))
                    .andExpect(content().string(not(containsString("IllegalStateException"))));
        }

        @ParameterizedTest(name = "{0} → campo {1}")
        @CsvSource({
                "/consulta/bicicletas/21/7965/2018-5, tipo",
                "/consulta/carros/abc/7965/2018-5,    marcaId",
                "/consulta/carros/21/1234567/2018-5,  modeloId",
                "/consulta/carros/21/7965/2018,       anoId",
                "/consulta/carros/21/7965/2018-5x,    anoId"})
        void deveRecusarParametroInvalidoSemConsultarAFipe(String url, String campo) throws Exception {
            mvc.perform(get(url))
                    .andExpect(status().isBadRequest())
                    .andExpect(view().name("fragments/resultado :: erro"))
                    .andExpect(model().attribute("titulo", "Parâmetro inválido"))
                    .andExpect(content().string(containsString("O campo &#39;" + campo + "&#39;")));

            verifyNoInteractions(service);
        }
    }
}
