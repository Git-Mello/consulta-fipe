package br.com.consultafipe.model.client;

import br.com.consultafipe.config.FipeProperties;
import br.com.consultafipe.exception.FalhaAutenticacaoException;
import br.com.consultafipe.exception.FipeException;
import br.com.consultafipe.exception.LimiteDeUsoExcedidoException;
import br.com.consultafipe.exception.RecursoNaoEncontradoException;
import br.com.consultafipe.exception.RequisicaoInvalidaException;
import br.com.consultafipe.exception.ServicoExternoIndisponivelException;
import br.com.consultafipe.model.client.dto.DetalheVeiculoFipeDto;
import br.com.consultafipe.model.client.dto.ItemFipeDto;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Única classe que conhece a API da FIPE: rotas, header de autenticação, formato do JSON
 * e significado dos status HTTP. Devolve os DTOs no formato externo e traduz toda falha
 * em uma {@link FipeException}, para que as camadas de cima não dependam de detalhes HTTP.
 *
 * <p>Os parâmetros {@code tipoVeiculo} usam os valores da API ({@code cars}, {@code motorcycles},
 * {@code trucks}); a tradução a partir do domínio é feita pelo service.
 */
@Component
public class FipeClient {

    static final String HEADER_TOKEN = "X-Subscription-Token";
    static final String HEADER_RESET_COTA = "x-ratelimit-reset";

    private static final String ROTA_MARCAS = "/{tipo}/brands";
    private static final String ROTA_MODELOS = ROTA_MARCAS + "/{marcaId}/models";
    private static final String ROTA_ANOS = ROTA_MODELOS + "/{modeloId}/years";
    private static final String ROTA_PRECO = ROTA_ANOS + "/{anoId}";

    private static final ParameterizedTypeReference<List<ItemFipeDto>> LISTA_DE_ITENS =
            new ParameterizedTypeReference<>() {
            };
    private static final int LIMITE_CORPO_NO_LOG = 200;
    private static final Logger log = LoggerFactory.getLogger(FipeClient.class);

    private final RestClient restClient;
    private final FipeProperties propriedades;
    private final RetryTemplate tentativas;

    public FipeClient(RestClient fipeRestClient, FipeProperties propriedades) {
        this.restClient = fipeRestClient;
        this.propriedades = propriedades;
        this.tentativas = new RetryTemplate(politicaDeTentativas(propriedades.tentativas()));
    }

    public List<ItemFipeDto> listarMarcas(String tipoVeiculo) {
        return listar(ROTA_MARCAS, tipoVeiculo);
    }

    public List<ItemFipeDto> listarModelos(String tipoVeiculo, String marcaId) {
        return listar(ROTA_MODELOS, tipoVeiculo, marcaId);
    }

    public List<ItemFipeDto> listarAnos(String tipoVeiculo, String marcaId, String modeloId) {
        return listar(ROTA_ANOS, tipoVeiculo, marcaId, modeloId);
    }

    public DetalheVeiculoFipeDto buscarPreco(String tipoVeiculo, String marcaId, String modeloId, String anoId) {
        DetalheVeiculoFipeDto detalhe = executar(
                resposta -> resposta.body(DetalheVeiculoFipeDto.class),
                ROTA_PRECO, tipoVeiculo, marcaId, modeloId, anoId);
        if (detalhe == null) {
            throw ServicoExternoIndisponivelException.definitiva("FIPE devolveu preço vazio", null);
        }
        return detalhe;
    }

    private List<ItemFipeDto> listar(String rota, Object... variaveis) {
        List<ItemFipeDto> itens = executar(resposta -> resposta.body(LISTA_DE_ITENS), rota, variaveis);
        return itens == null ? List.of() : List.copyOf(itens);
    }

    /** Faz o GET com novas tentativas nas falhas temporárias. */
    private <T> T executar(Function<RestClient.ResponseSpec, T> leitor, String rota, Object... variaveis) {
        String rotaExpandida = UriComponentsBuilder.fromUriString(rota).buildAndExpand(variaveis).toUriString();
        return tentativas.invoke(() -> chamarUmaVez(leitor, rotaExpandida, rota, variaveis));
    }

    private <T> T chamarUmaVez(Function<RestClient.ResponseSpec, T> leitor, String rotaExpandida,
                               String rota, Object... variaveis) {
        long inicio = System.nanoTime();
        try {
            T corpo = leitor.apply(restClient.get()
                    .uri(rota, variaveis)
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(this::adicionarToken)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError,
                            (pedido, resposta) -> { throw traduzirErro(rotaExpandida, resposta); }));
            log.info("FIPE GET {} -> 200 em {} ms", rotaExpandida, milissegundosDesde(inicio));
            return corpo;
        } catch (FipeException e) {
            log.warn("FIPE GET {} falhou em {} ms: {}", rotaExpandida, milissegundosDesde(inicio), e.getMessage());
            throw e;
        } catch (ResourceAccessException e) {
            log.warn("FIPE GET {} sem resposta em {} ms: {}", rotaExpandida, milissegundosDesde(inicio), e.getMessage());
            throw ServicoExternoIndisponivelException.temporaria(
                    "Timeout ou falha de conexão em GET " + rotaExpandida, e);
        } catch (RestClientException e) {
            log.warn("FIPE GET {} com resposta inválida: {}", rotaExpandida, e.getMessage());
            throw ServicoExternoIndisponivelException.definitiva(
                    "Resposta inválida da FIPE em GET " + rotaExpandida, e);
        }
    }

    private void adicionarToken(HttpHeaders headers) {
        if (propriedades.possuiToken()) {
            headers.set(HEADER_TOKEN, propriedades.token());
        }
    }

    private FipeException traduzirErro(String rota, ClientHttpResponse resposta) throws IOException {
        int status = resposta.getStatusCode().value();
        String detalhe = "FIPE respondeu %d em GET %s: %s".formatted(status, rota, lerCorpoResumido(resposta));
        return switch (status) {
            case 404 -> new RecursoNaoEncontradoException(detalhe);
            case 400, 422 -> new RequisicaoInvalidaException(detalhe);
            case 401, 403 -> falhaDeAutenticacao(detalhe);
            case 429 -> new LimiteDeUsoExcedidoException(detalhe, tempoParaLiberarCota(resposta.getHeaders()));
            case 502, 503, 504 -> ServicoExternoIndisponivelException.temporaria(detalhe, null);
            default -> ServicoExternoIndisponivelException.definitiva(detalhe, null);
        };
    }

    private static FalhaAutenticacaoException falhaDeAutenticacao(String detalhe) {
        log.error("Token da FIPE recusado: verifique a variável FIPE_API_TOKEN (inválido ou expirado)");
        return new FalhaAutenticacaoException(detalhe);
    }

    /** Usa o {@code Retry-After} (em segundos) se existir; senão o {@code x-ratelimit-reset} da FIPE. */
    static Duration tempoParaLiberarCota(HttpHeaders headers) {
        Duration retryAfter = segundos(headers.getFirst(HttpHeaders.RETRY_AFTER));
        return retryAfter != null ? retryAfter : segundos(headers.getFirst(HEADER_RESET_COTA));
    }

    private static Duration segundos(String valor) {
        if (valor == null || !valor.trim().matches("\\d{1,9}")) {
            return null;
        }
        return Duration.ofSeconds(Long.parseLong(valor.trim()));
    }

    private static String lerCorpoResumido(ClientHttpResponse resposta) {
        try (InputStream corpo = resposta.getBody()) {
            String texto = new String(corpo.readNBytes(LIMITE_CORPO_NO_LOG), StandardCharsets.UTF_8);
            return texto.isBlank() ? "<sem corpo>" : texto.strip();
        } catch (IOException e) {
            return "<corpo ilegível: " + e.getMessage() + ">";
        }
    }

    private static RetryPolicy politicaDeTentativas(FipeProperties.Tentativas configuracao) {
        return RetryPolicy.builder()
                .maxRetries(configuracao.maximo())
                .delay(configuracao.esperaInicial())
                .multiplier(2)
                .predicate(FipeClient::ehFalhaTemporaria)
                .build();
    }

    private static boolean ehFalhaTemporaria(Throwable erro) {
        return erro instanceof ServicoExternoIndisponivelException e && e.isTemporaria();
    }

    private static long milissegundosDesde(long inicioEmNanos) {
        return Duration.ofNanos(System.nanoTime() - inicioEmNanos).toMillis();
    }
}
