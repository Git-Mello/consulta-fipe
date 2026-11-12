package br.com.consultafipe.model.domain;

import static br.com.consultafipe.model.domain.Validacoes.exigirTexto;

/** Marca de veículo. Ex.: {@code Marca("21", "Fiat")}. */
public record Marca(String codigo, String nome) {

    public Marca {
        exigirTexto(codigo, "codigo");
        exigirTexto(nome, "nome");
    }
}
