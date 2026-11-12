package br.com.consultafipe.exception;

/** A API da FIPE recusou os parâmetros enviados (HTTP 400/422). */
public class RequisicaoInvalidaException extends FipeException {

    public RequisicaoInvalidaException(String mensagem) {
        super(mensagem);
    }
}
