package br.com.consultafipe.model.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PrecoFipeTest {

    @Test
    void deveDescreverOAnoQuandoNaoForZeroKm() {
        PrecoFipe preco = precoComAno(2018);

        assertThat(preco.isZeroKm()).isFalse();
        assertThat(preco.anoDescricao()).isEqualTo("2018");
    }

    @Test
    void deveDescreverComoZeroKmQuandoNaoHouverAno() {
        PrecoFipe preco = precoComAno(null);

        assertThat(preco.isZeroKm()).isTrue();
        assertThat(preco.anoDescricao()).isEqualTo("Zero km");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void deveRecusarPrecoSemValor(String valor) {
        assertThatThrownBy(() -> new PrecoFipe(TipoVeiculo.CARROS, valor, "Fiat", "ARGO DRIVE 1.0 6V Flex",
                2018, "Flex", "001494-0", "novembro de 2026"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("valor");
    }

    @Test
    void deveRecusarPrecoSemTipo() {
        assertThatThrownBy(() -> new PrecoFipe(null, "R$ 48.136,00", "Fiat", "ARGO DRIVE 1.0 6V Flex",
                2018, "Flex", "001494-0", "novembro de 2026"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("tipo");
    }

    @Test
    void deveRecusarItensDeCatalogoSemCodigoOuNome() {
        assertThatThrownBy(() -> new Marca(" ", "Fiat")).hasMessageContaining("codigo");
        assertThatThrownBy(() -> new Modelo("7965", null)).hasMessageContaining("nome");
        assertThatThrownBy(() -> new AnoModelo("2018-5", "")).hasMessageContaining("descricao");
    }

    private static PrecoFipe precoComAno(Integer ano) {
        return new PrecoFipe(TipoVeiculo.CARROS, "R$ 48.136,00", "Fiat", "ARGO DRIVE 1.0 6V Flex", ano,
                "Flex", "001494-0", "novembro de 2026");
    }
}
