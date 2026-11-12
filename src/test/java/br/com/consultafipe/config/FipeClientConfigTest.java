package br.com.consultafipe.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.consultafipe.exception.ServicoExternoIndisponivelException;
import br.com.consultafipe.model.client.FipeClient;
import br.com.consultafipe.model.client.dto.ItemFipeDto;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * Testa o RestClient montado pela configuração contra um servidor HTTP local (loopback),
 * para verificar o comportamento real de timeout e de URL base. Não acessa a internet.
 */
class FipeClientConfigTest {

    private final CountDownLatch liberarResposta = new CountDownLatch(1);
    private HttpServer servidor;

    @AfterEach
    void pararServidor() {
        liberarResposta.countDown();
        if (servidor != null) {
            servidor.stop(0);
        }
    }

    @Test
    void deveInterromperAChamadaQuandoALeituraPassarDoTimeout() throws IOException {
        iniciarServidor(troca -> {
            aguardarLiberacao();
            responder(troca, "[]");
        });
        FipeClient client = clientApontandoPara(urlBase(), Duration.ofMillis(300));

        long inicio = System.nanoTime();

        assertThatThrownBy(() -> client.listarMarcas("cars"))
                .isInstanceOf(ServicoExternoIndisponivelException.class)
                .hasMessageContaining("Timeout");
        assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isLessThan(Duration.ofSeconds(3));
    }

    @Test
    void deveMontarAsRotasAPartirDaUrlBaseMesmoComBarraNoFinal() throws IOException {
        AtomicReference<String> caminhoRecebido = new AtomicReference<>();
        iniciarServidor(troca -> {
            caminhoRecebido.set(troca.getRequestURI().getPath());
            responder(troca, "[{\"code\":\"21\",\"name\":\"Fiat\"}]");
        });
        FipeClient client = clientApontandoPara(urlBase() + "/", Duration.ofSeconds(5));

        var marcas = client.listarMarcas("cars");

        assertThat(caminhoRecebido.get()).isEqualTo("/api/v2/cars/brands");
        assertThat(marcas).containsExactly(new ItemFipeDto("21", "Fiat"));
    }

    private FipeClient clientApontandoPara(String urlBase, Duration timeoutLeitura) {
        FipeProperties propriedades = new FipeProperties(
                URI.create(urlBase),
                null,
                new FipeProperties.Timeouts(Duration.ofSeconds(1), timeoutLeitura),
                new FipeProperties.Tentativas(0, Duration.ofMillis(1)),
                new FipeProperties.Cache(Duration.ofHours(24), Duration.ofHours(6), 100));
        RestClient restClient = new FipeClientConfig().fipeRestClient(RestClient.builder(), propriedades);
        return new FipeClient(restClient, propriedades);
    }

    private void iniciarServidor(ManipuladorDeTeste manipulador) throws IOException {
        servidor = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        servidor.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        servidor.createContext("/", troca -> {
            try (troca) {
                manipulador.tratar(troca);
            }
        });
        servidor.start();
    }

    private String urlBase() {
        return "http://127.0.0.1:" + servidor.getAddress().getPort() + "/api/v2";
    }

    private void aguardarLiberacao() {
        try {
            liberarResposta.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void responder(HttpExchange troca, String json) throws IOException {
        byte[] corpo = json.getBytes(StandardCharsets.UTF_8);
        troca.getResponseHeaders().set("Content-Type", "application/json");
        troca.sendResponseHeaders(200, corpo.length);
        try (OutputStream saida = troca.getResponseBody()) {
            saida.write(corpo);
        }
    }

    @FunctionalInterface
    private interface ManipuladorDeTeste {
        void tratar(HttpExchange troca) throws IOException;
    }
}
