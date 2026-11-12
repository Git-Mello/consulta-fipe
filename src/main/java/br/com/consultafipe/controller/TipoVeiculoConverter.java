package br.com.consultafipe.controller;

import br.com.consultafipe.model.domain.TipoVeiculo;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Converte o {@code {tipo}} das URLs ({@code carros}, {@code motos}, {@code caminhoes}) em {@link TipoVeiculo}.
 * Um valor desconhecido faz o Spring lançar {@code MethodArgumentTypeMismatchException}, respondida com 400.
 */
@Component
public class TipoVeiculoConverter implements Converter<String, TipoVeiculo> {

    @Override
    public TipoVeiculo convert(String slug) {
        return TipoVeiculo.doSlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Tipo de veículo desconhecido"));
    }
}
