package br.com.consultafipe.config;

import br.com.consultafipe.model.service.FipeService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.List;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Caches Caffeine do {@link FipeService}. Marcas, modelos e anos só mudam com a tabela mensal
 * e usam a expiração de catálogo (padrão 24h); preços usam uma expiração própria (padrão 6h).
 */
@Configuration(proxyBeanMethods = false)
@EnableCaching
public class CacheConfig {

    @Bean
    CacheManager cacheManager(FipeProperties propriedades) {
        FipeProperties.Cache configuracao = propriedades.cache();
        CaffeineCacheManager gerenciador = new CaffeineCacheManager();
        gerenciador.setAllowNullValues(false);
        // Lista fixa: um nome de cache desconhecido gera erro em vez de criar um cache sem expiração.
        gerenciador.setCacheNames(List.of());
        registrar(gerenciador, FipeService.CACHE_MARCAS, configuracao.catalogoTtl(), configuracao.tamanhoMaximo());
        registrar(gerenciador, FipeService.CACHE_MODELOS, configuracao.catalogoTtl(), configuracao.tamanhoMaximo());
        registrar(gerenciador, FipeService.CACHE_ANOS, configuracao.catalogoTtl(), configuracao.tamanhoMaximo());
        registrar(gerenciador, FipeService.CACHE_PRECOS, configuracao.precoTtl(), configuracao.tamanhoMaximo());
        return gerenciador;
    }

    private static void registrar(CaffeineCacheManager gerenciador, String nome, Duration expiracao,
                                  long tamanhoMaximo) {
        Cache<Object, Object> cache = Caffeine.newBuilder()
                .expireAfterWrite(expiracao)
                .maximumSize(tamanhoMaximo)
                .build();
        gerenciador.registerCustomCache(nome, cache);
    }
}
