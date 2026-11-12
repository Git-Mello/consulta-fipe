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
import java.util.function.Function;
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
        return converterValidos(itens, item -> new Marca(item.code(), item.name()), "marca");
    }

    static List<Modelo> paraModelos(List<ItemFipeDto> itens) {
        return converterValidos(itens, item -> new Modelo(item.code(), item.name()), "modelo");
    }

    static List<AnoModelo> paraAnos(List<ItemFipeDto> itens) {
        return converterValidos(itens, item -> new AnoModelo(item.code(), descricaoDoAno(item)), "ano");
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

    private static String descricaoDoAno(ItemFipeDto item) {
        String nome = item.name();
        Integer ano = item.code() == null ? null : anoDoCodigo(item.code());
        if (nome == null || ano == null || ano != ANO_ZERO_KM_NA_FIPE) {
            return nome;
        }
        return nome.replaceFirst("^" + ANO_ZERO_KM_NA_FIPE, ROTULO_ZERO_KM);
    }

    private static Integer anoOuNuloSeZeroKm(int anoModelo) {
        return anoModelo == ANO_ZERO_KM_NA_FIPE ? null : anoModelo;
    }

    /** Converte cada item e descarta (com log) os que vierem sem código ou sem nome. */
    private static <T> List<T> converterValidos(List<ItemFipeDto> itens, Function<ItemFipeDto, T> conversor,
                                                String descricao) {
        List<T> convertidos = itens.stream()
                .filter(item -> item != null && temTexto(item.code()) && temTexto(item.name()))
                .map(conversor)
                .toList();
        if (convertidos.size() < itens.size()) {
            log.warn("FIPE devolveu {} item(ns) de {} sem código ou nome; descartado(s)",
                    itens.size() - convertidos.size(), descricao);
        }
        return convertidos;
    }

    private static boolean temTexto(String valor) {
        return valor != null && !valor.isBlank();
    }

    private static ServicoExternoIndisponivelException respostaIncompleta(String detalhe, Throwable causa) {
        return ServicoExternoIndisponivelException.definitiva("Resposta incompleta da FIPE: " + detalhe, causa);
    }
}
