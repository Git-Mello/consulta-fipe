package br.com.consultafipe.model.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * Tipos de veículo da Tabela FIPE.
 *
 * <p>{@code slug} é o valor usado nas URLs da aplicação (em português) e {@code codigoApi}
 * o valor que a API da FIPE espera no caminho.
 */
public enum TipoVeiculo {

    CARROS("carros", "cars", "Carros"),
    MOTOS("motos", "motorcycles", "Motos"),
    CAMINHOES("caminhoes", "trucks", "Caminhões");

    private final String slug;
    private final String codigoApi;
    private final String rotulo;

    TipoVeiculo(String slug, String codigoApi, String rotulo) {
        this.slug = slug;
        this.codigoApi = codigoApi;
        this.rotulo = rotulo;
    }

    /** Converte o valor da URL ({@code carros}, {@code motos}, {@code caminhoes}) no tipo. */
    public static Optional<TipoVeiculo> doSlug(String slug) {
        return Arrays.stream(values())
                .filter(tipo -> tipo.slug.equals(slug))
                .findFirst();
    }

    public String slug() {
        return slug;
    }

    public String codigoApi() {
        return codigoApi;
    }

    public String rotulo() {
        return rotulo;
    }
}
