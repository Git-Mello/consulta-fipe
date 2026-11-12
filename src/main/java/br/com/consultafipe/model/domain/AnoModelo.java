package br.com.consultafipe.model.domain;

import static br.com.consultafipe.model.domain.Validacoes.exigirTexto;

/**
 * Ano/versão de um modelo, combinando ano e combustível.
 * Ex.: {@code AnoModelo("2018-5", "2018 Flex")} ou {@code AnoModelo("32000-5", "Zero km Flex")}.
 */
public record AnoModelo(String codigo, String descricao) {

    public AnoModelo {
        exigirTexto(codigo, "codigo");
        exigirTexto(descricao, "descricao");
    }
}
