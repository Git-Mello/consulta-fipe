package br.com.consultafipe.model.service;

import br.com.consultafipe.model.client.FipeClient;
import br.com.consultafipe.model.domain.AnoModelo;
import br.com.consultafipe.model.domain.Marca;
import br.com.consultafipe.model.domain.Modelo;
import br.com.consultafipe.model.domain.PrecoFipe;
import br.com.consultafipe.model.domain.TipoVeiculo;
import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Regras da consulta à Tabela FIPE: chama o client, converte os DTOs em domínio,
 * ordena as listas para exibição e guarda os resultados em cache, para poupar a cota diária da API.
 *
 * <p>Erros do client ({@code FipeException}) são propagados sem alteração e nunca ficam em cache.
 */
@Service
public class FipeService {

    public static final String CACHE_MARCAS = "marcas";
    public static final String CACHE_MODELOS = "modelos";
    public static final String CACHE_ANOS = "anos";
    public static final String CACHE_PRECOS = "precos";

    private static final Locale PORTUGUES_BRASIL = Locale.of("pt", "BR");

    /** Zero km primeiro; depois ano decrescente; no mesmo ano, pela descrição (combustível). */
    private static final Comparator<AnoModelo> ORDEM_DOS_ANOS = Comparator
            .comparing((AnoModelo ano) -> !ConversorFipe.ehZeroKm(ano))
            .thenComparing(ano -> ConversorFipe.anoDoCodigo(ano.codigo()),
                    Comparator.nullsLast(Comparator.<Integer>reverseOrder()))
            .thenComparing(AnoModelo::descricao);

    private final FipeClient client;

    public FipeService(FipeClient client) {
        this.client = client;
    }

    /** Marcas do tipo, em ordem alfabética (sem diferenciar maiúsculas). */
    @Cacheable(cacheNames = CACHE_MARCAS, sync = true)
    public List<Marca> listarMarcas(TipoVeiculo tipo) {
        Objects.requireNonNull(tipo, "tipo é obrigatório");
        List<Marca> marcas = ConversorFipe.paraMarcas(client.listarMarcas(tipo.codigoApi()));
        return ordenarPorNome(marcas, Marca::nome);
    }

    /** Modelos da marca, em ordem alfabética (sem diferenciar maiúsculas). */
    @Cacheable(cacheNames = CACHE_MODELOS, sync = true)
    public List<Modelo> listarModelos(TipoVeiculo tipo, String marcaId) {
        Objects.requireNonNull(tipo, "tipo é obrigatório");
        Objects.requireNonNull(marcaId, "marcaId é obrigatório");
        List<Modelo> modelos = ConversorFipe.paraModelos(client.listarModelos(tipo.codigoApi(), marcaId));
        return ordenarPorNome(modelos, Modelo::nome);
    }

    /** Anos do modelo: zero km primeiro, depois do mais novo para o mais antigo. */
    @Cacheable(cacheNames = CACHE_ANOS, sync = true)
    public List<AnoModelo> listarAnos(TipoVeiculo tipo, String marcaId, String modeloId) {
        Objects.requireNonNull(tipo, "tipo é obrigatório");
        Objects.requireNonNull(marcaId, "marcaId é obrigatório");
        Objects.requireNonNull(modeloId, "modeloId é obrigatório");
        List<AnoModelo> anos = ConversorFipe.paraAnos(client.listarAnos(tipo.codigoApi(), marcaId, modeloId));
        return anos.stream().sorted(ORDEM_DOS_ANOS).toList();
    }

    @Cacheable(cacheNames = CACHE_PRECOS, sync = true)
    public PrecoFipe buscarPreco(TipoVeiculo tipo, String marcaId, String modeloId, String anoId) {
        Objects.requireNonNull(tipo, "tipo é obrigatório");
        Objects.requireNonNull(marcaId, "marcaId é obrigatório");
        Objects.requireNonNull(modeloId, "modeloId é obrigatório");
        Objects.requireNonNull(anoId, "anoId é obrigatório");
        return ConversorFipe.paraPreco(tipo, client.buscarPreco(tipo.codigoApi(), marcaId, modeloId, anoId));
    }

    private static <T> List<T> ordenarPorNome(List<T> itens, Function<T, String> nome) {
        Collator collator = Collator.getInstance(PORTUGUES_BRASIL);
        collator.setStrength(Collator.SECONDARY);
        return itens.stream().sorted(Comparator.comparing(nome, collator)).toList();
    }
}
