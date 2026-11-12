package br.com.consultafipe.model.domain;

import static br.com.consultafipe.model.domain.Validacoes.exigirTexto;

/** Modelo de uma marca. Ex.: {@code Modelo("7965", "ARGO DRIVE 1.0 6V Flex")}. */
public record Modelo(String codigo, String nome) {

    public Modelo {
        exigirTexto(codigo, "codigo");
        exigirTexto(nome, "nome");
    }
}
