package br.com.consultafipe.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class FipePropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ConfiguracaoDeTeste.class)
            .withPropertyValues(
                    "fipe.base-url=https://fipe.exemplo/api/v2",
                    "fipe.timeouts.conexao=2s",
                    "fipe.timeouts.leitura=7s",
                    "fipe.tentativas.maximo=2",
                    "fipe.tentativas.espera-inicial=300ms",
                    "fipe.cache.catalogo-ttl=24h",
                    "fipe.cache.preco-ttl=6h",
                    "fipe.cache.tamanho-maximo=1000");

    @Test
    void deveLerTodasAsPropriedadesTipadas() {
        runner.run(contexto -> {
            FipeProperties propriedades = contexto.getBean(FipeProperties.class);

            assertThat(propriedades.baseUrl()).isEqualTo(URI.create("https://fipe.exemplo/api/v2"));
            assertThat(propriedades.timeouts().conexao()).isEqualTo(Duration.ofSeconds(2));
            assertThat(propriedades.timeouts().leitura()).isEqualTo(Duration.ofSeconds(7));
            assertThat(propriedades.tentativas().maximo()).isEqualTo(2);
            assertThat(propriedades.tentativas().esperaInicial()).isEqualTo(Duration.ofMillis(300));
            assertThat(propriedades.cache().catalogoTtl()).isEqualTo(Duration.ofHours(24));
            assertThat(propriedades.cache().precoTtl()).isEqualTo(Duration.ofHours(6));
            assertThat(propriedades.cache().tamanhoMaximo()).isEqualTo(1000);
        });
    }

    @Test
    void deveFuncionarSemToken() {
        runner.withPropertyValues("fipe.token=").run(contexto -> {
            FipeProperties propriedades = contexto.getBean(FipeProperties.class);

            assertThat(propriedades.possuiToken()).isFalse();
        });
    }

    @Test
    void deveReconhecerTokenQuandoConfigurado() {
        runner.withPropertyValues("fipe.token=token-de-teste").run(contexto -> {
            FipeProperties propriedades = contexto.getBean(FipeProperties.class);

            assertThat(propriedades.possuiToken()).isTrue();
        });
    }

    @Test
    void naoDeveExporOTokenNoToString() {
        runner.withPropertyValues("fipe.token=token-secreto-123").run(contexto -> {
            String texto = contexto.getBean(FipeProperties.class).toString();

            assertThat(texto).doesNotContain("token-secreto-123").contains("token=****");
        });
    }

    @Test
    void deveFalharAoSubirSemUrlBase() {
        runner.withPropertyValues("fipe.base-url=").run(contexto ->
                assertThat(contexto).hasFailed());
    }

    @Test
    void deveFalharAoSubirComNumeroDeTentativasForaDoLimite() {
        runner.withPropertyValues("fipe.tentativas.maximo=10").run(contexto ->
                assertThat(contexto).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(FipeProperties.class)
    static class ConfiguracaoDeTeste {
    }
}
