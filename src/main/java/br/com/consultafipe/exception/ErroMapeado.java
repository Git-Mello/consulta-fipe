package br.com.consultafipe.exception;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;

/**
 * O que a aplicação responde ao usuário para uma falha: status HTTP e textos em português,
 * sem detalhes internos.
 *
 * @param status           status HTTP da resposta
 * @param titulo           título curto do erro
 * @param mensagem         explicação para o usuário
 * @param tentarDeNovoEm   quando tentar novamente (vira o header {@code Retry-After}), se souber
 * @param camposInvalidos  parâmetro → motivo, quando a entrada foi recusada
 */
record ErroMapeado(HttpStatus status, String titulo, String mensagem, Duration tentarDeNovoEm,
                   Map<String, String> camposInvalidos) {

    ErroMapeado {
        camposInvalidos = Map.copyOf(camposInvalidos);
    }

    static ErroMapeado de(HttpStatus status, String titulo, String mensagem) {
        return new ErroMapeado(status, titulo, mensagem, null, Map.of());
    }

    Optional<Duration> retryAfter() {
        return Optional.ofNullable(tentarDeNovoEm);
    }
}
