package br.com.consultafipe.exception;

/**
 * Base das falhas na integração com a API da FIPE. A mensagem é técnica (vai para o log);
 * o texto exibido ao usuário é decidido na borda da aplicação, no handler de exceções.
 */
public abstract class FipeException extends RuntimeException {

    protected FipeException(String mensagem) {
        super(mensagem);
    }

    protected FipeException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
