package br.com.consultafipe;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.consultafipe.config.FipeProperties;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Teste de fumaça: a aplicação sobe com os valores padrão do application.yml. */
@SpringBootTest(properties = "FIPE_API_TOKEN=")
class ConsultaFipeApplicationTests {

    @Autowired
    private FipeProperties propriedades;

    @Test
    void deveSubirComAsConfiguracoesPadrao() {
        assertThat(propriedades.baseUrl()).isEqualTo(URI.create("https://fipe.api.br/api/v2"));
        assertThat(propriedades.possuiToken()).isFalse();
        assertThat(propriedades.timeouts().conexao()).isEqualTo(Duration.ofSeconds(5));
        assertThat(propriedades.timeouts().leitura()).isEqualTo(Duration.ofSeconds(5));
        assertThat(propriedades.cache().catalogoTtl()).isEqualTo(Duration.ofHours(24));
        assertThat(propriedades.cache().precoTtl()).isEqualTo(Duration.ofHours(6));
    }
}
