package br.com.consultafipe.exception;

/** A combinação pedida (marca, modelo, ano...) não existe na Tabela FIPE (HTTP 404). */
public class RecursoNaoEncontradoException extends FipeException {

    public RecursoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
