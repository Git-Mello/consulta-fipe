package br.com.consultafipe.model.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import br.com.consultafipe.config.FipeProperties;
import br.com.consultafipe.exception.FalhaAutenticacaoException;
import br.com.consultafipe.exception.LimiteDeUsoExcedidoException;
import br.com.consultafipe.exception.RecursoNaoEncontradoException;
import br.com.consultafipe.exception.RequisicaoInvalidaException;
import br.com.consultafipe.exception.ServicoExternoIndisponivelException;
import br.com.consultafipe.model.client.dto.DetalheVeiculoFipeDto;
import br.com.consultafipe.model.client.dto.ItemFipeDto;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class FipeClientTest {

    private static final String BASE_URL = "https://fipe.teste/api/v2";
    private static final String URL_MARCAS = BASE_URL + "/cars/brands";
    private static final String URL_MODELOS = URL_MARCAS + "/21/models";
    private static final String URL_ANOS = URL_MODELOS + "/7965/years";
    private static final String URL_PRECO = URL_ANOS + "/2018-5";
    private static final int NOVAS_TENTATIVAS = 2;

    private MockRestServiceServer servidor;
    private FipeClient client;

    @BeforeEach
    void prepararClientSemToken() {
        client = criarClient(null);
    }

    @Nested
    class RespostasDeSucesso {

        @Test
        void deveListarMarcasDaRespostaReal() {
            servidor.expect(once(), requestTo(URL_MARCAS))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess(fixture("marcas-carros.json"), MediaType.APPLICATION_JSON));

            List<ItemFipeDto> marcas = client.listarMarcas("cars");

            assertThat(marcas).hasSize(108)
                    .contains(new ItemFipeDto("21", "Fiat"), new ItemFipeDto("59", "VW - VolksWagen"));
            servidor.verify();
        }

        @Test
        void deveListarModelosDaMarca() {
            servidor.expect(once(), requestTo(URL_MODELOS))
                    .andRespond(withSuccess(fixture("modelos-carros-marca-21.json"), MediaType.APPLICATION_JSON));

            List<ItemFipeDto> modelos = client.listarModelos("cars", "21");

            assertThat(modelos).hasSize(586).contains(new ItemFipeDto("7965", "ARGO DRIVE 1.0 6V Flex"));
            servidor.verify();
        }

        @Test
        void deveListarAnosDoModeloIncluindoZeroKm() {
            servidor.expect(once(), requestTo(URL_ANOS))
                    .andRespond(withSuccess(fixture("anos-carros-marca-21-modelo-7965.json"),
                            MediaType.APPLICATION_JSON));

            List<ItemFipeDto> anos = client.listarAnos("cars", "21", "7965");

            assertThat(anos).hasSize(10)
                    .startsWith(new ItemFipeDto("2018-5", "2018 Flex"))
                    .endsWith(new ItemFipeDto("32000-5", "32000 Flex"));
            servidor.verify();
        }

        @Test
        void deveBuscarPrecoComTodosOsCampos() {
            servidor.expect(once(), requestTo(URL_PRECO))
                    .andRespond(withSuccess(fixture("preco-carros-marca-21-modelo-7965-ano-2018-5.json"),
                            MediaType.APPLICATION_JSON));

            DetalheVeiculoFipeDto preco = client.buscarPreco("cars", "21", "7965", "2018-5");

            assertThat(preco).isEqualTo(new DetalheVeiculoFipeDto(1, "R$ 48.136,00", "Fiat",
                    "ARGO DRIVE 1.0 6V Flex", 2018, "Flex", "001494-0", "novembro de 2026", "F"));
            servidor.verify();
        }

        @Test
        void deveIgnorarCamposDesconhecidosNaResposta() {
            servidor.expect(requestTo(URL_MARCAS))
                    .andRespond(withSuccess("""
                            [{"code":"21","name":"Fiat","campoNovo":true}]""", MediaType.APPLICATION_JSON));

            List<ItemFipeDto> marcas = client.listarMarcas("cars");

            assertThat(marcas).containsExactly(new ItemFipeDto("21", "Fiat"));
        }

        @Test
        void deveDevolverListaVaziaQuandoApiNaoRetornarItens() {
            servidor.expect(requestTo(URL_MARCAS)).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

            assertThat(client.listarMarcas("cars")).isEmpty();
        }
    }

    @Nested
    class Autenticacao {

        @Test
        void naoDeveEnviarTokenQuandoNaoConfigurado() {
            servidor.expect(requestTo(URL_MARCAS))
                    .andExpect(headerDoesNotExist(FipeClient.HEADER_TOKEN))
                    .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

            client.listarMarcas("cars");

            servidor.verify();
        }

        @Test
        void deveEnviarTokenNoHeaderQuandoConfigurado() {
            FipeClient clientComToken = criarClient("token-de-teste");
            servidor.expect(requestTo(URL_MARCAS))
                    .andExpect(header(FipeClient.HEADER_TOKEN, "token-de-teste"))
                    .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

            clientComToken.listarMarcas("cars");

            servidor.verify();
        }

        @Test
        void deveLancarFalhaDeAutenticacaoQuandoTokenForRecusado() {
            FipeClient clientComToken = criarClient("token-invalido");
            servidor.expect(once(), requestTo(URL_MARCAS))
                    .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                            .body(fixture("erro-401.json")).contentType(MediaType.APPLICATION_JSON));

            assertThatThrownBy(() -> clientComToken.listarMarcas("cars"))
                    .isInstanceOf(FalhaAutenticacaoException.class)
                    .hasMessageContaining("401")
                    .hasMessageNotContaining("token-invalido");
            servidor.verify();
        }

        @Test
        void deveLancarFalhaDeAutenticacaoQuandoAcessoForProibido() {
            servidor.expect(once(), requestTo(URL_MARCAS)).andRespond(withStatus(HttpStatus.FORBIDDEN));

            assertThatThrownBy(() -> client.listarMarcas("cars")).isInstanceOf(FalhaAutenticacaoException.class);
        }
    }

    @Nested
    class ErrosDoPedido {

        @Test
        void deveLancarRecursoNaoEncontradoQuandoApiRetornar404() {
            servidor.expect(once(), requestTo(URL_ANOS + "/1990-1"))
                    .andRespond(withStatus(HttpStatus.NOT_FOUND)
                            .body(fixture("erro-404.json")).contentType(MediaType.APPLICATION_JSON));

            assertThatThrownBy(() -> client.buscarPreco("cars", "21", "7965", "1990-1"))
                    .isInstanceOf(RecursoNaoEncontradoException.class)
                    .hasMessageContaining("404")
                    .hasMessageContaining("/cars/brands/21/models/7965/years/1990-1")
                    .hasMessageContaining("veículo não encontrado");
            servidor.verify();
        }

        @Test
        void deveLancarRequisicaoInvalidaQuandoApiRetornar400() {
            servidor.expect(once(), requestTo(URL_MARCAS + "/abc/models"))
                    .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                            .body(fixture("erro-400.json")).contentType(MediaType.APPLICATION_JSON));

            assertThatThrownBy(() -> client.listarModelos("cars", "abc"))
                    .isInstanceOf(RequisicaoInvalidaException.class)
                    .hasMessageContaining("invalid brand id");
            servidor.verify();
        }
    }

    @Nested
    class LimiteDeUso {

        @Test
        @DisplayName("429 com x-ratelimit-reset: informa quando a cota libera e não tenta de novo")
        void deveLancarLimiteExcedidoComTempoDoHeaderDaFipe() {
            servidor.expect(once(), requestTo(URL_MARCAS))
                    .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                            .header("x-ratelimit-reset", "3600")
                            .body(fixture("erro-429-sintetico.json")).contentType(MediaType.APPLICATION_JSON));

            assertThatThrownBy(() -> client.listarMarcas("cars"))
                    .isInstanceOfSatisfying(LimiteDeUsoExcedidoException.class, erro ->
                            assertThat(erro.tempoParaLiberar()).contains(Duration.ofHours(1)));
            servidor.verify();
        }

        @Test
        void devePriorizarRetryAfterQuandoExistir() {
            servidor.expect(once(), requestTo(URL_MARCAS))
                    .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                            .header("Retry-After", "120")
                            .header("x-ratelimit-reset", "3600"));

            assertThatThrownBy(() -> client.listarMarcas("cars"))
                    .isInstanceOfSatisfying(LimiteDeUsoExcedidoException.class, erro ->
                            assertThat(erro.tempoParaLiberar()).contains(Duration.ofMinutes(2)));
        }

        @Test
        void deveLancarLimiteExcedidoSemTempoQuandoApiNaoInformar() {
            servidor.expect(once(), requestTo(URL_MARCAS))
                    .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "amanhã"));

            assertThatThrownBy(() -> client.listarMarcas("cars"))
                    .isInstanceOfSatisfying(LimiteDeUsoExcedidoException.class, erro ->
                            assertThat(erro.tempoParaLiberar()).isEmpty());
        }
    }

    @Nested
    class IndisponibilidadeEhTentativas {

        @Test
        void deveTentarDeNovoQuandoApiRetornar503EDepoisResponder() {
            servidor.expect(once(), requestTo(URL_MARCAS)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
            servidor.expect(once(), requestTo(URL_MARCAS))
                    .andRespond(withSuccess("[{\"code\":\"21\",\"name\":\"Fiat\"}]", MediaType.APPLICATION_JSON));

            List<ItemFipeDto> marcas = client.listarMarcas("cars");

            assertThat(marcas).containsExactly(new ItemFipeDto("21", "Fiat"));
            servidor.verify();
        }

        @Test
        void deveDesistirDepoisDasNovasTentativasQuandoApiContinuarFora() {
            servidor.expect(times(1 + NOVAS_TENTATIVAS), requestTo(URL_MARCAS))
                    .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

            assertThatThrownBy(() -> client.listarMarcas("cars"))
                    .isInstanceOfSatisfying(ServicoExternoIndisponivelException.class, erro ->
                            assertThat(erro.isTemporaria()).isTrue());
            servidor.verify();
        }

        @Test
        void deveTentarDeNovoEmTimeoutEDesistirNoLimite() {
            servidor.expect(times(1 + NOVAS_TENTATIVAS), requestTo(URL_PRECO))
                    .andRespond(withException(new SocketTimeoutException("Read timed out")));

            assertThatThrownBy(() -> client.buscarPreco("cars", "21", "7965", "2018-5"))
                    .isInstanceOf(ServicoExternoIndisponivelException.class)
                    .hasMessageContaining("Timeout ou falha de conexão")
                    .hasRootCauseInstanceOf(SocketTimeoutException.class);
            servidor.verify();
        }

        @Test
        void naoDeveTentarDeNovoQuandoApiRetornar500() {
            servidor.expect(once(), requestTo(URL_MARCAS)).andRespond(withServerError());

            assertThatThrownBy(() -> client.listarMarcas("cars"))
                    .isInstanceOfSatisfying(ServicoExternoIndisponivelException.class, erro ->
                            assertThat(erro.isTemporaria()).isFalse());
            servidor.verify();
        }

        @Test
        void naoDeveTentarDeNovoQuandoApiRetornar404() {
            servidor.expect(once(), requestTo(URL_MODELOS)).andRespond(withStatus(HttpStatus.NOT_FOUND));

            assertThatThrownBy(() -> client.listarModelos("cars", "21"))
                    .isInstanceOf(RecursoNaoEncontradoException.class);
            servidor.verify();
        }

        @Test
        void deveLancarIndisponivelQuandoRespostaNaoForJsonValido() {
            servidor.expect(once(), requestTo(URL_MARCAS))
                    .andRespond(withSuccess("<html>manutenção</html>", MediaType.APPLICATION_JSON));

            assertThatThrownBy(() -> client.listarMarcas("cars"))
                    .isInstanceOf(ServicoExternoIndisponivelException.class)
                    .hasMessageContaining("Resposta inválida");
            servidor.verify();
        }

        @Test
        void deveLancarIndisponivelQuandoPrecoVierVazio() {
            servidor.expect(once(), requestTo(URL_PRECO)).andRespond(withSuccess());

            assertThatThrownBy(() -> client.buscarPreco("cars", "21", "7965", "2018-5"))
                    .isInstanceOf(ServicoExternoIndisponivelException.class);
        }
    }

    private FipeClient criarClient(String token) {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        servidor = MockRestServiceServer.bindTo(builder).build();
        return new FipeClient(builder.build(), propriedades(token));
    }

    private static FipeProperties propriedades(String token) {
        return new FipeProperties(
                URI.create(BASE_URL),
                token,
                new FipeProperties.Timeouts(Duration.ofSeconds(3), Duration.ofSeconds(5)),
                new FipeProperties.Tentativas(NOVAS_TENTATIVAS, Duration.ofMillis(1)),
                new FipeProperties.Cache(Duration.ofHours(24), Duration.ofHours(6), 100));
    }

    private static ClassPathResource fixture(String nome) {
        return new ClassPathResource("fixtures/" + nome);
    }
}
