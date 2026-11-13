package br.com.consultafipe.model.domain;

import static br.com.consultafipe.model.domain.Validacoes.exigirTexto;

/**
 * Ano de um modelo. O código identifica ano e combustível ({@code "2018-5"}); a descrição mostra só o ano,
 * ex.: {@code AnoModelo("2018-5", "2018")} ou {@code AnoModelo("32000-5", "Zero km")}.
 * Quando o modelo tem o mesmo ano com combustíveis diferentes, a descrição dessas opções inclui o combustível:
 * {@code AnoModelo("2005-1", "2005 Gasolina")} e {@code AnoModelo("2005-2", "2005 Álcool")}.
 * O combustível do veículo escolhido sempre aparece no {@link PrecoFipe}.
 */
public record AnoModelo(String codigo, String descricao) {

    public AnoModelo {
        exigirTexto(codigo, "codigo");
        exigirTexto(descricao, "descricao");
    }
}
