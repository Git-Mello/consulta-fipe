package br.com.consultafipe.model.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.consultafipe.config.CacheConfig;
import br.com.consultafipe.config.FipeProperties;
import br.com.consultafipe.exception.ServicoExternoIndisponivelException;
import br.com.consultafipe.model.client.FipeClient;
import br.com.consultafipe.model.client.dto.DetalheVeiculoFipeDto;
import br.com.consultafipe.model.client.dto.ItemFipeDto;
import br.com.consultafipe.model.domain.TipoVeiculo;
import com.github.benmanes.caffeine.cache.Cache;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/** Verifica o cache do {@link FipeService} com o proxy real do Spring e o client simulado. */
@SpringJUnitConfig({CacheConfig.class, FipeService.class, FipeServiceCacheTest.Propriedades.class})
class FipeServiceCacheTest {

    private static final List<ItemFipeDto> FIAT = List.of(new ItemFipeDto("21", "Fiat"));

    @MockitoBean
    private FipeClient client;

    @Autowired
    private FipeService service;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void limparCaches() {
        cacheManager.getCacheNames().forEach(nome -> cacheManager.getCache(nome).clear());
    }

    @Test
    void naoDeveChamarAApiDeNovoParaMarcasJaConsultadas() {
        when(client.listarMarcas("cars")).thenReturn(FIAT);

        service.listarMarcas(TipoVeiculo.CARROS);
        var segundaConsulta = service.listarMarcas(TipoVeiculo.CARROS);

        assertThat(segundaConsulta).hasSize(1);
        verify(client, times(1)).listarMarcas("cars");
    }

    @Test
    void deveSepararOCachePorTipoDeVeiculo() {
        when(client.listarMarcas("cars")).thenReturn(FIAT);
        when(client.listarMarcas("motorcycles")).thenReturn(List.of(new ItemFipeDto("80", "HONDA")));

        service.listarMarcas(TipoVeiculo.CARROS);
        service.listarMarcas(TipoVeiculo.MOTOS);
        service.listarMarcas(TipoVeiculo.MOTOS);

        verify(client, times(1)).listarMarcas("cars");
        verify(client, times(1)).listarMarcas("motorcycles");
    }

    @Test
    void deveGuardarModelosEAnosPorMarcaEModelo() {
        when(client.listarModelos("cars", "21")).thenReturn(List.of(new ItemFipeDto("7965", "ARGO")));
        when(client.listarAnos("cars", "21", "7965")).thenReturn(List.of(new ItemFipeDto("2018-5", "2018 Flex")));

        service.listarModelos(TipoVeiculo.CARROS, "21");
        service.listarModelos(TipoVeiculo.CARROS, "21");
        service.listarAnos(TipoVeiculo.CARROS, "21", "7965");
        service.listarAnos(TipoVeiculo.CARROS, "21", "7965");

        verify(client, times(1)).listarModelos("cars", "21");
        verify(client, times(1)).listarAnos("cars", "21", "7965");
    }

    @Test
    void deveGuardarPrecoPorVeiculoEAno() {
        when(client.buscarPreco("cars", "21", "7965", "2018-5")).thenReturn(detalhe());

        service.buscarPreco(TipoVeiculo.CARROS, "21", "7965", "2018-5");
        service.buscarPreco(TipoVeiculo.CARROS, "21", "7965", "2018-5");

        verify(client, times(1)).buscarPreco("cars", "21", "7965", "2018-5");
    }

    @Test
    void naoDeveGuardarErrosNoCache() {
        when(client.listarMarcas("cars"))
                .thenThrow(ServicoExternoIndisponivelException.temporaria("FIPE fora do ar", null))
                .thenReturn(FIAT);

        assertThatThrownBy(() -> service.listarMarcas(TipoVeiculo.CARROS))
                .isInstanceOf(ServicoExternoIndisponivelException.class);
        var depoisDaFalha = service.listarMarcas(TipoVeiculo.CARROS);

        assertThat(depoisDaFalha).hasSize(1);
        verify(client, times(2)).listarMarcas("cars");
    }

    @ParameterizedTest
    @CsvSource({"marcas, PT24H", "modelos, PT24H", "anos, PT24H", "precos, PT6H"})
    void deveAplicarAExpiracaoConfiguradaEmCadaCache(String nome, Duration expiracaoEsperada) {
        Cache<Object, Object> cache = ((CaffeineCache) cacheManager.getCache(nome)).getNativeCache();

        var expiracao = cache.policy().expireAfterWrite().orElseThrow();
        var limite = cache.policy().eviction().orElseThrow();

        assertThat(expiracao.getExpiresAfter()).isEqualTo(expiracaoEsperada);
        assertThat(limite.getMaximum()).isEqualTo(500);
    }

    @Test
    void naoDeveCriarCachesForaDaListaConhecida() {
        assertThat(cacheManager.getCacheNames()).containsExactlyInAnyOrder("marcas", "modelos", "anos", "precos");
        assertThat(cacheManager.getCache("cache-com-nome-errado")).isNull();
    }

    private static DetalheVeiculoFipeDto detalhe() {
        return new DetalheVeiculoFipeDto(1, "R$ 48.136,00", "Fiat", "ARGO DRIVE 1.0 6V Flex", 2018, "Flex",
                "001494-0", "novembro de 2026", "F");
    }

    @Configuration(proxyBeanMethods = false)
    static class Propriedades {

        @Bean
        FipeProperties fipeProperties() {
            return new FipeProperties(
                    URI.create("https://fipe.teste/api/v2"),
                    null,
                    new FipeProperties.Timeouts(Duration.ofSeconds(3), Duration.ofSeconds(5)),
                    new FipeProperties.Tentativas(0, Duration.ofMillis(1)),
                    new FipeProperties.Cache(Duration.ofHours(24), Duration.ofHours(6), 500));
        }
    }
}
