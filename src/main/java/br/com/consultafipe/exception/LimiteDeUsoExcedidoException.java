package br.com.consultafipe.exception;

import java.time.Duration;
import java.util.Optional;

/** A cota de requisições da API da FIPE acabou (HTTP 429). Não deve ser repetida. */
public class LimiteDeUsoExcedidoException extends FipeException {

    private final Duration tempoParaLiberar;

    /**
     * @param mensagem         detalhe técnico para o log
     * @param tempoParaLiberar quanto falta para a cota ser renovada, ou {@code null} se a API não informou
     */
    public LimiteDeUsoExcedidoException(String mensagem, Duration tempoParaLiberar) {
        super(mensagem);
        this.tempoParaLiberar = tempoParaLiberar;
    }

    public Optional<Duration> tempoParaLiberar() {
        return Optional.ofNullable(tempoParaLiberar);
    }
}
