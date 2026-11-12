package br.com.consultafipe.exception;

/**
 * A API da FIPE não respondeu como esperado: erro 5xx, timeout, falha de conexão
 * ou resposta em formato inválido.
 */
public class ServicoExternoIndisponivelException extends FipeException {

    private final boolean temporaria;

    private ServicoExternoIndisponivelException(String mensagem, Throwable causa, boolean temporaria) {
        super(mensagem, causa);
        this.temporaria = temporaria;
    }

    /** Falha passageira (timeout, conexão, 502/503/504): vale a pena tentar de novo. */
    public static ServicoExternoIndisponivelException temporaria(String mensagem, Throwable causa) {
        return new ServicoExternoIndisponivelException(mensagem, causa, true);
    }

    /** Falha que não melhora repetindo o pedido (500, resposta inválida). */
    public static ServicoExternoIndisponivelException definitiva(String mensagem, Throwable causa) {
        return new ServicoExternoIndisponivelException(mensagem, causa, false);
    }

    public boolean isTemporaria() {
        return temporaria;
    }
}
