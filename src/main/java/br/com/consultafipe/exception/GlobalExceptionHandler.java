package br.com.consultafipe.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

/**
 * Ponto central de tratamento de erros. A mesma falha é respondida no formato de cada tipo de rota:
 * <ul>
 *   <li>{@code /api/**}: {@link ProblemDetail} (RFC 9457) em JSON;</li>
 *   <li>{@code /consulta/**}: fragmento {@code resultado :: erro}, que o JavaScript insere no lugar do card;</li>
 *   <li>demais rotas: página {@code error.html}.</li>
 * </ul>
 * Mensagens em português, sem stack trace nem detalhes internos. O erro original vai para o log.
 */
@ControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {

    static final String VIEW_PAGINA_DE_ERRO = "error";
    static final String VIEW_FRAGMENTO_DE_ERRO = "fragments/resultado :: erro";

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public Object tratar(Exception erro, HttpServletRequest pedido, HttpServletResponse resposta) {
        ErroMapeado mapeado = MapeadorDeErros.mapear(erro);
        String rota = rotaDo(pedido);
        registrarNoLog(erro, mapeado, pedido.getMethod(), rota);
        mapeado.retryAfter().ifPresent(tempo ->
                resposta.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(tempo.toSeconds())));

        if (rota.startsWith("/api/")) {
            return comoProblemDetail(mapeado, rota);
        }
        String view = rota.startsWith("/consulta/") ? VIEW_FRAGMENTO_DE_ERRO : VIEW_PAGINA_DE_ERRO;
        return comoView(view, mapeado);
    }

    private static ResponseEntity<ProblemDetail> comoProblemDetail(ErroMapeado mapeado, String rota) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(mapeado.status(), mapeado.mensagem());
        problema.setTitle(mapeado.titulo());
        problema.setInstance(URI.create(rota));
        if (!mapeado.camposInvalidos().isEmpty()) {
            problema.setProperty("campos", mapeado.camposInvalidos());
        }
        return ResponseEntity.status(mapeado.status()).body(problema);
    }

    private static ModelAndView comoView(String view, ErroMapeado mapeado) {
        Map<String, Object> modelo = Map.of(
                "status", mapeado.status().value(),
                "titulo", mapeado.titulo(),
                "mensagem", mapeado.mensagem());
        return new ModelAndView(view, modelo, mapeado.status());
    }

    /**
     * Erro inesperado vai com stack trace. Nos demais basta o motivo: para falhas da FIPE, a mensagem técnica
     * (status e rota externa); para entrada inválida, o motivo já traduzido, que indica o campo.
     */
    private static void registrarNoLog(Exception erro, ErroMapeado mapeado, String metodo, String rota) {
        int status = mapeado.status().value();
        boolean falhaDaFipe = erro instanceof FipeException;
        if (status >= 500 && !falhaDaFipe) {
            log.error("{} {} -> {}: erro inesperado", metodo, rota, status, erro);
            return;
        }
        String motivo = falhaDaFipe ? erro.getMessage() : mapeado.mensagem();
        if (status >= 500) {
            log.warn("{} {} -> {}: {}", metodo, rota, status, motivo);
        } else {
            log.info("{} {} -> {}: {}", metodo, rota, status, motivo);
        }
    }

    private static String rotaDo(HttpServletRequest pedido) {
        return pedido.getRequestURI().substring(pedido.getContextPath().length());
    }
}
