package br.com.consultafipe.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuração da integração com a API da FIPE, lida do {@code application.yml}
 * (prefixo {@code fipe}). A aplicação não sobe se algum valor obrigatório faltar.
 *
 * @param baseUrl    URL base da API (ex.: {@code https://fipe.api.br/api/v2})
 * @param token      token de assinatura opcional; sem ele a API libera 500 requisições/dia
 * @param timeouts   timeouts de conexão e de leitura das chamadas HTTP
 * @param tentativas novas tentativas em falhas temporárias (timeout, 502, 503, 504)
 * @param cache      expiração e tamanho dos caches de catálogo e de preços
 */
@Validated
@ConfigurationProperties(prefix = "fipe")
public record FipeProperties(
        @NotNull URI baseUrl,
        String token,
        @NotNull @Valid Timeouts timeouts,
        @NotNull @Valid Tentativas tentativas,
        @NotNull @Valid Cache cache) {

    public boolean possuiToken() {
        return token != null && !token.isBlank();
    }

    /** Nunca expõe o token, mesmo que o objeto seja logado por engano. */
    @Override
    public String toString() {
        return "FipeProperties[baseUrl=%s, token=%s, timeouts=%s, tentativas=%s, cache=%s]"
                .formatted(baseUrl, possuiToken() ? "****" : "<ausente>", timeouts, tentativas, cache);
    }

    public record Timeouts(@NotNull Duration conexao, @NotNull Duration leitura) {
    }

    public record Tentativas(@Min(0) @Max(5) int maximo, @NotNull Duration esperaInicial) {
    }

    public record Cache(@NotNull Duration catalogoTtl, @NotNull Duration precoTtl, @Positive long tamanhoMaximo) {
    }
}
