package br.com.consultafipe.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.consultafipe.exception.LimiteDeUsoExcedidoException;
import br.com.consultafipe.exception.RecursoNaoEncontradoException;
import br.com.consultafipe.exception.ServicoExternoIndisponivelException;
import br.com.consultafipe.model.client.FipeClient;
import br.com.consultafipe.model.client.dto.DetalheVeiculoFipeDto;
import br.com.consultafipe.model.client.dto.ItemFipeDto;
import br.com.consultafipe.model.domain.AnoModelo;
import br.com.consultafipe.model.domain.Marca;
import br.com.consultafipe.model.domain.Modelo;
import br.com.consultafipe.model.domain.PrecoFipe;
import br.com.consultafipe.model.domain.TipoVeiculo;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class FipeServiceTest {

    @Mock
    private FipeClient client;

    @InjectMocks
    private FipeService service;

    @Nested
    class Marcas {

        @ParameterizedTest
        @CsvSource({"CARROS, cars", "MOTOS, motorcycles", "CAMINHOES, trucks"})
        void deveConsultarAApiComOCodigoExternoDoTipo(TipoVeiculo tipo, String codigoApi) {
            when(client.listarMarcas(codigoApi)).thenReturn(itens("1", "Marca"));

            List<Marca> marcas = service.listarMarcas(tipo);

            assertThat(marcas).containsExactly(new Marca("1", "Marca"));
        }

        @Test
        void deveOrdenarMarcasAlfabeticamenteSemDiferenciarMaiusculasNemAcentos() {
            when(client.listarMarcas("cars")).thenReturn(itens(
                    "59", "VW - VolksWagen", "6", "Audi", "5", "Ásia Motors",
                    "189", "ASTON MARTIN", "1", "Acura", "245", "chery"));

            List<Marca> marcas = service.listarMarcas(TipoVeiculo.CARROS);

            assertThat(marcas).extracting(Marca::nome)
                    .containsExactly("Acura", "Ásia Motors", "ASTON MARTIN", "Audi", "chery", "VW - VolksWagen");
        }

        @Test
        void deveDescartarItensSemCodigoOuNome() {
            when(client.listarMarcas("cars")).thenReturn(Arrays.asList(
                    new ItemFipeDto("21", "Fiat"), new ItemFipeDto(null, "Sem código"),
                    new ItemFipeDto("99", " "), null));

            List<Marca> marcas = service.listarMarcas(TipoVeiculo.CARROS);

            assertThat(marcas).containsExactly(new Marca("21", "Fiat"));
        }

        @Test
        void deveDevolverListaVaziaQuandoApiNaoTiverMarcas() {
            when(client.listarMarcas("trucks")).thenReturn(List.of());

            assertThat(service.listarMarcas(TipoVeiculo.CAMINHOES)).isEmpty();
        }
    }

    @Nested
    class Modelos {

        @Test
        void deveConverterEOrdenarModelosDaMarca() {
            when(client.listarModelos("cars", "21")).thenReturn(itens(
                    "7966", "ARGO DRIVE 1.3 8V Flex", "437", "147 C/ CL", "7965", "ARGO DRIVE 1.0 6V Flex"));

            List<Modelo> modelos = service.listarModelos(TipoVeiculo.CARROS, "21");

            assertThat(modelos).containsExactly(
                    new Modelo("437", "147 C/ CL"),
                    new Modelo("7965", "ARGO DRIVE 1.0 6V Flex"),
                    new Modelo("7966", "ARGO DRIVE 1.3 8V Flex"));
        }
    }

    @Nested
    class Anos {

        @Test
        void deveMostrarSoOAnoEOrdenarDoMaisNovoParaOMaisAntigo() {
            when(client.listarAnos("cars", "21", "7965")).thenReturn(itens(
                    "2018-5", "2018 Flex", "2020-5", "2020 Flex", "2019-5", "2019 Flex"));

            List<AnoModelo> anos = service.listarAnos(TipoVeiculo.CARROS, "21", "7965");

            assertThat(anos).containsExactly(
                    new AnoModelo("2020-5", "2020"),
                    new AnoModelo("2019-5", "2019"),
                    new AnoModelo("2018-5", "2018"));
        }

        @Test
        void deveMostrarZeroKmNoLugarDoAno32000EColocarPrimeiro() {
            when(client.listarAnos("cars", "21", "7965")).thenReturn(itens(
                    "2025-5", "2025 Flex", "32000-5", "32000 Flex"));

            List<AnoModelo> anos = service.listarAnos(TipoVeiculo.CARROS, "21", "7965");

            assertThat(anos).containsExactly(
                    new AnoModelo("32000-5", "Zero km"),
                    new AnoModelo("2025-5", "2025"));
        }

        @Test
        void deveManterOCombustivelSoNosAnosQueSeRepetem() {
            when(client.listarAnos("cars", "21", "7965")).thenReturn(itens(
                    "2005-2", "2005 Álcool", "2006-1", "2006 Gasolina", "2005-1", "2005 Gasolina"));

            List<AnoModelo> anos = service.listarAnos(TipoVeiculo.CARROS, "21", "7965");

            assertThat(anos).extracting(AnoModelo::descricao)
                    .containsExactlyInAnyOrder("2006", "2005 Gasolina", "2005 Álcool");
            assertThat(anos.getFirst()).isEqualTo(new AnoModelo("2006-1", "2006"));
        }

        @Test
        void deveManterOCombustivelNoZeroKmQuandoEleSeRepete() {
            when(client.listarAnos("cars", "21", "7965")).thenReturn(itens(
                    "32000-1", "32000 Gasolina", "32000-5", "32000 Flex", "2024-5", "2024 Flex"));

            List<AnoModelo> anos = service.listarAnos(TipoVeiculo.CARROS, "21", "7965");

            assertThat(anos).extracting(AnoModelo::descricao)
                    .containsExactly("Zero km Flex", "Zero km Gasolina", "2024");
        }

        @Test
        void deveOrdenarOsAnosDaRespostaRealDaApi() throws IOException {
            when(client.listarAnos("cars", "21", "7965"))
                    .thenReturn(fixtureDeItens("anos-carros-marca-21-modelo-7965.json"));

            List<AnoModelo> anos = service.listarAnos(TipoVeiculo.CARROS, "21", "7965");

            assertThat(anos).hasSize(10);
            assertThat(anos.getFirst()).isEqualTo(new AnoModelo("32000-5", "Zero km"));
            assertThat(anos.get(1)).isEqualTo(new AnoModelo("2026-5", "2026"));
            assertThat(anos.getLast()).isEqualTo(new AnoModelo("2018-5", "2018"));
        }

        @Test
        void deveManterNoFimAnosComCodigoForaDoPadrao() {
            when(client.listarAnos("cars", "21", "7965")).thenReturn(itens(
                    "xpto", "Ano estranho", "2018-5", "2018 Flex"));

            List<AnoModelo> anos = service.listarAnos(TipoVeiculo.CARROS, "21", "7965");

            assertThat(anos).extracting(AnoModelo::codigo).containsExactly("2018-5", "xpto");
        }
    }

    @Nested
    class Precos {

        @Test
        void deveConverterPrecoParaODominio() {
            when(client.buscarPreco("cars", "21", "7965", "2018-5")).thenReturn(detalhe(2018, "R$ 48.136,00"));

            PrecoFipe preco = service.buscarPreco(TipoVeiculo.CARROS, "21", "7965", "2018-5");

            assertThat(preco).isEqualTo(new PrecoFipe(TipoVeiculo.CARROS, "R$ 48.136,00", "Fiat",
                    "ARGO DRIVE 1.0 6V Flex", 2018, "Flex", "001494-0", "novembro de 2026"));
            assertThat(preco.anoDescricao()).isEqualTo("2018");
        }

        @Test
        void deveConverterAno32000ParaZeroKm() {
            when(client.buscarPreco("cars", "21", "7965", "32000-5")).thenReturn(detalhe(32000, "R$ 95.000,00"));

            PrecoFipe preco = service.buscarPreco(TipoVeiculo.CARROS, "21", "7965", "32000-5");

            assertThat(preco.isZeroKm()).isTrue();
            assertThat(preco.anoDescricao()).isEqualTo("Zero km");
        }

        @Test
        void deveLancarIndisponivelQuandoPrecoVierSemValor() {
            when(client.buscarPreco("cars", "21", "7965", "2018-5")).thenReturn(detalhe(2018, null));

            assertThatThrownBy(() -> service.buscarPreco(TipoVeiculo.CARROS, "21", "7965", "2018-5"))
                    .isInstanceOf(ServicoExternoIndisponivelException.class)
                    .hasMessageContaining("Resposta incompleta")
                    .hasMessageContaining("valor");
        }

        @Test
        void deveLancarIndisponivelQuandoPrecoVierSemAnoModelo() {
            when(client.buscarPreco("cars", "21", "7965", "2018-5")).thenReturn(detalhe(null, "R$ 48.136,00"));

            assertThatThrownBy(() -> service.buscarPreco(TipoVeiculo.CARROS, "21", "7965", "2018-5"))
                    .isInstanceOf(ServicoExternoIndisponivelException.class)
                    .hasMessageContaining("ano-modelo");
        }
    }

    @Nested
    class Erros {

        @Test
        void devePropagarRecursoNaoEncontradoDoClient() {
            RecursoNaoEncontradoException erro = new RecursoNaoEncontradoException("FIPE respondeu 404");
            when(client.listarAnos("cars", "21", "999999")).thenThrow(erro);

            assertThatThrownBy(() -> service.listarAnos(TipoVeiculo.CARROS, "21", "999999")).isSameAs(erro);
        }

        @Test
        void devePropagarLimiteDeUsoDoClient() {
            LimiteDeUsoExcedidoException erro =
                    new LimiteDeUsoExcedidoException("FIPE respondeu 429", Duration.ofHours(3));
            when(client.listarMarcas("motorcycles")).thenThrow(erro);

            assertThatThrownBy(() -> service.listarMarcas(TipoVeiculo.MOTOS)).isSameAs(erro);
        }

        @Test
        void naoDeveChamarAApiComParametroAusente() {
            assertThatThrownBy(() -> service.listarMarcas(null)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> service.listarModelos(TipoVeiculo.CARROS, null))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> service.buscarPreco(TipoVeiculo.CARROS, "21", "7965", null))
                    .isInstanceOf(NullPointerException.class);

            verifyNoInteractions(client);
        }
    }

    /** Monta itens a partir de pares código/nome. */
    private static List<ItemFipeDto> itens(String... codigosENomes) {
        ItemFipeDto[] itens = new ItemFipeDto[codigosENomes.length / 2];
        for (int i = 0; i < itens.length; i++) {
            itens[i] = new ItemFipeDto(codigosENomes[2 * i], codigosENomes[2 * i + 1]);
        }
        return List.of(itens);
    }

    private static DetalheVeiculoFipeDto detalhe(Integer anoModelo, String preco) {
        return new DetalheVeiculoFipeDto(preco, "Fiat", "ARGO DRIVE 1.0 6V Flex", anoModelo, "Flex",
                "001494-0", "novembro de 2026");
    }

    private static List<ItemFipeDto> fixtureDeItens(String nome) throws IOException {
        try (InputStream json = new ClassPathResource("fixtures/" + nome).getInputStream()) {
            return JsonMapper.builder().build().readValue(json, new TypeReference<>() {
            });
        }
    }
}
