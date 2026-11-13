package br.com.consultafipe.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.consultafipe.exception.FalhaAutenticacaoException;
import br.com.consultafipe.exception.LimiteDeUsoExcedidoException;
import br.com.consultafipe.exception.RecursoNaoEncontradoException;
import br.com.consultafipe.exception.ServicoExternoIndisponivelException;
import br.com.consultafipe.fixture.DominioFixtures;
import br.com.consultafipe.model.domain.TipoVeiculo;
import br.com.consultafipe.model.service.FipeService;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CatalogoController.class)
class CatalogoControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private FipeService service;

    @Nested
    class Listas {

        @Test
        void deveDevolverAsMarcasDoTipoEmJson() throws Exception {
            when(service.listarMarcas(TipoVeiculo.CARROS)).thenReturn(DominioFixtures.marcas());

            mvc.perform(get("/api/carros/marcas"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].codigo").value("21"))
                    .andExpect(jsonPath("$[0].nome").value("Fiat"));
        }

        @Test
        void deveAceitarMotosECaminhoes() throws Exception {
            when(service.listarMarcas(TipoVeiculo.CAMINHOES)).thenReturn(DominioFixtures.marcas());

            mvc.perform(get("/api/caminhoes/marcas")).andExpect(status().isOk());
        }

        @Test
        void deveDevolverOsModelosDaMarca() throws Exception {
            when(service.listarModelos(TipoVeiculo.CARROS, "21")).thenReturn(DominioFixtures.modelos());

            mvc.perform(get("/api/carros/marcas/21/modelos"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].codigo").value("7965"))
                    .andExpect(jsonPath("$[0].nome").value("ARGO DRIVE 1.0 6V Flex"));
        }

        @Test
        void deveDevolverOsAnosDoModelo() throws Exception {
            when(service.listarAnos(TipoVeiculo.MOTOS, "80", "123")).thenReturn(DominioFixtures.anos());

            mvc.perform(get("/api/motos/marcas/80/modelos/123/anos"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].codigo").value("32000-5"))
                    .andExpect(jsonPath("$[0].descricao").value("Zero km"));
        }
    }

    @Nested
    class ErrosEmProblemDetail {

        @ParameterizedTest(name = "{0} → campo {1}")
        @CsvSource({
                "/api/bicicletas/marcas,                   tipo",
                "/api/carros/marcas/abc/modelos,           marcaId",
                "/api/carros/marcas/21/modelos/7x9/anos,   modeloId",
                "/api/carros/marcas/%20/modelos,           marcaId"})
        void deveRecusarParametroInvalidoIndicandoOCampo(String url, String campo) throws Exception {
            // URI.create: a URL chega como está (sem codificar de novo o "%20", que deve virar um espaço)
            mvc.perform(get(URI.create(url)))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.title").value("Parâmetro inválido"))
                    .andExpect(jsonPath("$.detail").value(containsString("'" + campo + "'")))
                    .andExpect(jsonPath("$.campos." + campo).exists());

            verifyNoInteractions(service);
        }

        @Test
        void deveListarOsTiposAceitosQuandoOTipoForInvalido() throws Exception {
            mvc.perform(get("/api/bicicletas/marcas"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.campos.tipo").value("deve ser um destes tipos: carros, motos, caminhoes"));
        }

        @Test
        void deveResponder404QuandoAMarcaNaoExistirNaFipe() throws Exception {
            when(service.listarModelos(TipoVeiculo.CARROS, "999999"))
                    .thenThrow(new RecursoNaoEncontradoException("FIPE respondeu 404"));

            mvc.perform(get("/api/carros/marcas/999999/modelos"))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.title").value("Não encontrado"))
                    .andExpect(jsonPath("$.instance").value("/api/carros/marcas/999999/modelos"));
        }

        @Test
        void deveResponder503ComRetryAfterQuandoACotaAcabar() throws Exception {
            when(service.listarMarcas(TipoVeiculo.CARROS))
                    .thenThrow(new LimiteDeUsoExcedidoException("FIPE respondeu 429", Duration.ofMinutes(15)));

            mvc.perform(get("/api/carros/marcas"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(header().string("Retry-After", "900"))
                    .andExpect(jsonPath("$.title").value("Cota diária esgotada"))
                    .andExpect(jsonPath("$.detail").value(containsString("em cerca de 15 minutos")));
        }

        @Test
        void deveAvisarQueACotaRenovaEm24hQuandoAFipeNaoInformarOTempo() throws Exception {
            when(service.listarMarcas(TipoVeiculo.CARROS))
                    .thenThrow(new LimiteDeUsoExcedidoException("FIPE respondeu 429", null));

            mvc.perform(get("/api/carros/marcas"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(header().doesNotExist("Retry-After"))
                    .andExpect(jsonPath("$.detail").value(containsString("renovada a cada 24 horas")));
        }

        @Test
        void deveResponder503QuandoAFipeEstiverForaDoAr() throws Exception {
            when(service.listarMarcas(TipoVeiculo.CARROS))
                    .thenThrow(ServicoExternoIndisponivelException.temporaria("FIPE respondeu 502", null));

            mvc.perform(get("/api/carros/marcas"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.title").value("FIPE indisponível"))
                    .andExpect(jsonPath("$.detail").value(containsString("indisponível no momento")));
        }

        @Test
        void deveTratarTokenRecusadoComoIndisponibilidadeSemCitarOToken() throws Exception {
            when(service.listarMarcas(TipoVeiculo.CARROS))
                    .thenThrow(new FalhaAutenticacaoException("FIPE respondeu 401: token de assinatura inválido"));

            mvc.perform(get("/api/carros/marcas"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.title").value("FIPE indisponível"))
                    .andExpect(content().string(not(containsString("token"))));
        }

        @Test
        void naoDeveExporDetalhesDeErroInesperado() throws Exception {
            when(service.listarMarcas(TipoVeiculo.CARROS))
                    .thenThrow(new IllegalStateException("detalhe interno sigiloso"));

            mvc.perform(get("/api/carros/marcas"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.title").value("Erro inesperado"))
                    .andExpect(content().string(not(containsString("sigiloso"))))
                    .andExpect(content().string(not(containsString("IllegalStateException"))));
        }

        @Test
        void deveResponderProblemDetailParaRotaDaApiInexistente() throws Exception {
            mvc.perform(get("/api/carros/rota-inexistente"))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(404));
        }
    }
}
