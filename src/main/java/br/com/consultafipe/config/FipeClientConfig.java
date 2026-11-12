package br.com.consultafipe.config;

import java.net.http.HttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Monta o {@link RestClient} usado pelo {@code FipeClient}: URL base e timeouts de conexão
 * e de leitura. Rotas, autenticação e tradução de erros ficam no próprio client.
 */
@Configuration(proxyBeanMethods = false)
public class FipeClientConfig {

    @Bean
    RestClient fipeRestClient(RestClient.Builder builder, FipeProperties propriedades) {
        return builder
                .baseUrl(semBarraNoFinal(propriedades.baseUrl().toString()))
                .requestFactory(criarRequestFactory(propriedades.timeouts()))
                .build();
    }

    static ClientHttpRequestFactory criarRequestFactory(FipeProperties.Timeouts timeouts) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(timeouts.conexao())
                .build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(httpClient);
        fabrica.setReadTimeout(timeouts.leitura());
        return fabrica;
    }

    private static String semBarraNoFinal(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
