package br.com.consultafipe.model.domain;

import static br.com.consultafipe.model.domain.Validacoes.exigirTexto;

import java.util.Objects;

/**
 * Preço de um veículo na Tabela FIPE, pronto para exibição.
 *
 * @param tipo          tipo do veículo
 * @param valor         preço já formatado em reais, como a FIPE publica (ex.: {@code "R$ 48.136,00"})
 * @param marca         nome da marca
 * @param modelo        nome do modelo
 * @param anoModelo     ano do modelo, ou {@code null} quando o veículo é zero km
 * @param combustivel   combustível (ex.: {@code "Flex"})
 * @param codigoFipe    código FIPE do veículo (ex.: {@code "001494-0"})
 * @param mesReferencia mês da tabela consultada (ex.: {@code "novembro de 2026"})
 */
public record PrecoFipe(
        TipoVeiculo tipo,
        String valor,
        String marca,
        String modelo,
        Integer anoModelo,
        String combustivel,
        String codigoFipe,
        String mesReferencia) {

    public PrecoFipe {
        Objects.requireNonNull(tipo, "tipo é obrigatório");
        exigirTexto(valor, "valor");
        exigirTexto(marca, "marca");
        exigirTexto(modelo, "modelo");
        exigirTexto(combustivel, "combustivel");
        exigirTexto(codigoFipe, "codigoFipe");
        exigirTexto(mesReferencia, "mesReferencia");
    }

    public boolean isZeroKm() {
        return anoModelo == null;
    }

    /** {@code "2018"} ou {@code "Zero km"}. */
    public String anoDescricao() {
        return isZeroKm() ? "Zero km" : String.valueOf(anoModelo);
    }
}
