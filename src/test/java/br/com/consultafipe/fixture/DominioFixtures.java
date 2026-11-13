package br.com.consultafipe.fixture;

import br.com.consultafipe.model.domain.AnoModelo;
import br.com.consultafipe.model.domain.Marca;
import br.com.consultafipe.model.domain.Modelo;
import br.com.consultafipe.model.domain.PrecoFipe;
import br.com.consultafipe.model.domain.TipoVeiculo;
import java.util.List;

/** Objetos de domínio de exemplo, baseados na consulta real do Fiat ARGO DRIVE 1.0 6V Flex 2018. */
public final class DominioFixtures {

    private DominioFixtures() {
    }

    public static List<Marca> marcas() {
        return List.of(new Marca("21", "Fiat"), new Marca("59", "VW - VolksWagen"));
    }

    public static List<Modelo> modelos() {
        return List.of(new Modelo("7965", "ARGO DRIVE 1.0 6V Flex"), new Modelo("7966", "ARGO DRIVE 1.3 8V Flex"));
    }

    public static List<AnoModelo> anos() {
        return List.of(new AnoModelo("32000-5", "Zero km"), new AnoModelo("2018-5", "2018"));
    }

    public static PrecoFipe precoArgo2018() {
        return precoComModelo("ARGO DRIVE 1.0 6V Flex");
    }

    public static PrecoFipe precoComModelo(String modelo) {
        return new PrecoFipe(TipoVeiculo.CARROS, "R$ 48.136,00", "Fiat", modelo, 2018, "Flex", "001494-0",
                "novembro de 2026");
    }

    public static PrecoFipe precoZeroKm() {
        return new PrecoFipe(TipoVeiculo.CARROS, "R$ 95.990,00", "Fiat", "ARGO DRIVE 1.0 6V Flex", null, "Flex",
                "001494-0", "novembro de 2026");
    }
}
