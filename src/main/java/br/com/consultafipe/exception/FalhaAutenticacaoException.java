package br.com.consultafipe.exception;

/**
 * A API da FIPE recusou o token (HTTP 401/403): ele está errado ou expirou.
 * É um problema de configuração do servidor, não do usuário.
 */
public class FalhaAutenticacaoException extends FipeException {

    public FalhaAutenticacaoException(String mensagem) {
        super(mensagem);
    }
}
