package br.com.consultafipe.model.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class TipoVeiculoTest {

    @ParameterizedTest
    @CsvSource({
            "carros,    CARROS,    cars,        Carros",
            "motos,     MOTOS,     motorcycles, Motos",
            "caminhoes, CAMINHOES, trucks,      Caminhões"})
    void deveMapearSlugDaUrlParaOCodigoDaApi(String slug, TipoVeiculo esperado, String codigoApi, String rotulo) {
        TipoVeiculo tipo = TipoVeiculo.doSlug(slug).orElseThrow();

        assertThat(tipo).isEqualTo(esperado);
        assertThat(tipo.slug()).isEqualTo(slug);
        assertThat(tipo.codigoApi()).isEqualTo(codigoApi);
        assertThat(tipo.rotulo()).isEqualTo(rotulo);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"cars", "CARROS", "Carros", "caminhões", "bicicletas", " carros"})
    void naoDeveReconhecerSlugInvalido(String slug) {
        assertThat(TipoVeiculo.doSlug(slug)).isEmpty();
    }
}
