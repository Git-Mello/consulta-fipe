package br.com.consultafipe.model.service;

import br.com.consultafipe.exception.ServicoExternoIndisponivelException;
import br.com.consultafipe.model.client.dto.DetalheVeiculoFipeDto;
import br.com.consultafipe.model.client.dto.ItemFipeDto;
import br.com.consultafipe.model.domain.AnoModelo;
import br.com.consultafipe.model.domain.Marca;
import br.com.consultafipe.model.domain.Modelo;
import br.com.consultafipe.model.domain.PrecoFipe;
import br.com.consultafipe.model.domain.TipoVeiculo;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Converte os DTOs da API da FIPE em objetos de domínio. É o único ponto, fora do client,
 * que conhece o formato externo, incluindo a convenção do ano {@code 32000} para zero km.
 */
final class ConversorFipe {

    /** A FIPE representa veículos zero km com o ano-modelo 32000 (ex.: {@code "32000-5"}). */
    static final int ANO_ZERO_KM_NA_FIPE = 32000;

    private static final String ROTULO_ZERO_KM = "Zero km";
    private static final Logger log = LoggerFactory.getLogger(ConversorFipe.class);

    private ConversorFipe() {
    }

    static List<Marca> paraMarcas(List<ItemFipeDto> itens) {
        return validos(itens, "marca").stream().map(item -> new Marca(item.code(), item.name())).toList();
    }

    static List<Modelo> paraModelos(List<ItemFipeDto> itens) {
        return validos(itens, "modelo").stream().map(item -> new Modelo(item.code(), item.name())).toList();
    }

    /**
     * Anos do modelo descritos só pelo ano ({@code "2018"}, {@code "Zero km"}). Quando o mesmo ano aparece
     * mais de uma vez (um por combustível), essas opções mantêm o combustível ({@code "2005 Gasolina"},
     * {@code "2005 Álcool"}), para a pessoa saber a diferença entre elas.
     */
    static List<AnoModelo> paraAnos(List<ItemFipeDto> itens) {
        List<ItemFipeDto> anos = validos(itens, "ano");
        Map<String, Long> vezesDeCadaAno = anos.stream()
                .collect(Collectors.groupingBy(ConversorFipe::rotuloDoAno, Collectors.counting()));
        return anos.stream()
                .map(item -> {
                    String rotulo = rotuloDoAno(item);
                    boolean repetido = vezesDeCadaAno.get(rotulo) > 1;
                    return new AnoModelo(item.code(), repetido ? rotuloComCombustivel(item) : rotulo);
                })
                .toList();
    }

    static PrecoFipe paraPreco(TipoVeiculo tipo, DetalheVeiculoFipeDto dto) {
        if (dto.modelYear() == null) {
            throw respostaIncompleta("preço sem ano-modelo", null);
        }
        try {
            return new PrecoFipe(tipo, dto.price(), dto.brand(), dto.model(), anoOuNuloSeZeroKm(dto.modelYear()),
                    dto.fuel(), dto.codeFipe(), dto.referenceMonth());
        } catch (IllegalArgumentException e) {
            throw respostaIncompleta("preço com campo obrigatório ausente: " + e.getMessage(), e);
        }
    }

    /** Ano extraído do código ({@code "2018-5"} → 2018), ou {@code null} se o código não seguir o padrão. */
    static Integer anoDoCodigo(String codigo) {
        int separador = codigo.indexOf('-');
        String ano = separador > 0 ? codigo.substring(0, separador) : codigo;
        return ano.matches("\\d{4,5}") ? Integer.valueOf(ano) : null;
    }

    static boolean ehZeroKm(AnoModelo ano) {
        Integer valor = anoDoCodigo(ano.codigo());
        return valor != null && valor == ANO_ZERO_KM_NA_FIPE;
    }

    /** Só o ano: {@code "2018-5"} → {@code "2018"}, {@code "32000-5"} → {@code "Zero km"}; fora do padrão, o nome. */
    private static String rotuloDoAno(ItemFipeDto item) {
        Integer ano = anoDoCodigo(item.code());
        if (ano == null) {
            return item.name();
        }
        return ano == ANO_ZERO_KM_NA_FIPE ? ROTULO_ZERO_KM : String.valueOf(ano);
    }

    /** O nome da FIPE, que traz o combustível ({@code "2005 Gasolina"}), com o 32000 trocado por "Zero km". */
    private static String rotuloComCombustivel(ItemFipeDto item) {
        Integer ano = anoDoCodigo(item.code());
        if (ano == null || ano != ANO_ZERO_KM_NA_FIPE) {
            return item.name();
        }
        return item.name().replaceFirst("^" + ANO_ZERO_KM_NA_FIPE, ROTULO_ZERO_KM);
    }

    private static Integer anoOuNuloSeZeroKm(int anoModelo) {
        return anoModelo == ANO_ZERO_KM_NA_FIPE ? null : anoModelo;
    }

    /** Descarta (com log) os itens que vierem sem código ou sem nome. */
    private static List<ItemFipeDto> validos(List<ItemFipeDto> itens, String descricao) {
        List<ItemFipeDto> validos = itens.stream()
                .filter(item -> item != null && temTexto(item.code()) && temTexto(item.name()))
                .toList();
        if (validos.size() < itens.size()) {
            log.warn("FIPE devolveu {} item(ns) de {} sem código ou nome; descartado(s)",
                    itens.size() - validos.size(), descricao);
        }
        return validos;
    }

    private static boolean temTexto(String valor) {
        return valor != null && !valor.isBlank();
    }

    private static ServicoExternoIndisponivelException respostaIncompleta(String detalhe, Throwable causa) {
        return ServicoExternoIndisponivelException.definitiva("Resposta incompleta da FIPE: " + detalhe, causa);
    }
}
