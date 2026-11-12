package br.com.consultafipe.exception;

import br.com.consultafipe.model.domain.TipoVeiculo;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Traduz qualquer exceção na resposta para o usuário: status e mensagem em português, sem detalhes internos. */
final class MapeadorDeErros {

    static final String MENSAGEM_INDISPONIVEL =
            "O serviço da Tabela FIPE está indisponível no momento. Tente novamente em alguns minutos.";
    static final String MENSAGEM_INESPERADA =
            "Ocorreu um erro inesperado ao processar a consulta. Tente novamente.";

    private MapeadorDeErros() {
    }

    static ErroMapeado mapear(Exception erro) {
        return switch (erro) {
            case RecursoNaoEncontradoException e -> ErroMapeado.de(HttpStatus.NOT_FOUND, "Não encontrado",
                    "Não encontramos esse veículo na Tabela FIPE. Confira a marca, o modelo e o ano escolhidos.");
            case LimiteDeUsoExcedidoException e -> cotaEsgotada(e.tempoParaLiberar());
            case FalhaAutenticacaoException e -> indisponivel();
            case ServicoExternoIndisponivelException e -> indisponivel();
            case RequisicaoInvalidaException e -> ErroMapeado.de(HttpStatus.BAD_REQUEST, "Consulta inválida",
                    "A Tabela FIPE recusou os dados da consulta. Refaça a escolha do veículo.");
            case MethodArgumentTypeMismatchException e -> parametrosInvalidos(Map.of(e.getName(), motivoDoTipo(e)));
            case HandlerMethodValidationException e -> parametrosInvalidos(motivosDaValidacao(e));
            case ErrorResponse e -> erroDoSpring(e);
            default -> ErroMapeado.de(HttpStatus.INTERNAL_SERVER_ERROR, "Erro inesperado", MENSAGEM_INESPERADA);
        };
    }

    private static ErroMapeado indisponivel() {
        return ErroMapeado.de(HttpStatus.SERVICE_UNAVAILABLE, "FIPE indisponível", MENSAGEM_INDISPONIVEL);
    }

    private static ErroMapeado cotaEsgotada(Optional<Duration> tempoParaLiberar) {
        String quando = tempoParaLiberar
                .map(tempo -> "Tente novamente " + descreverEspera(tempo) + ".")
                .orElse("Tente novamente mais tarde: a cota é renovada a cada 24 horas.");
        return new ErroMapeado(HttpStatus.SERVICE_UNAVAILABLE, "Cota diária esgotada",
                "A cota diária de consultas à Tabela FIPE acabou. " + quando,
                tempoParaLiberar.orElse(null), Map.of());
    }

    /** "em instantes", "em cerca de 15 minutos" ou "em cerca de 3 horas" (arredondado para cima). */
    static String descreverEspera(Duration tempo) {
        long minutos = (tempo.toSeconds() + 59) / 60;
        if (minutos < 1) {
            return "em instantes";
        }
        if (minutos < 60) {
            return "em cerca de " + minutos + (minutos == 1 ? " minuto" : " minutos");
        }
        long horas = (minutos + 59) / 60;
        return "em cerca de " + horas + (horas == 1 ? " hora" : " horas");
    }

    private static ErroMapeado parametrosInvalidos(Map<String, String> motivos) {
        String detalhe = motivos.entrySet().stream()
                .map(campo -> "'" + campo.getKey() + "' " + campo.getValue())
                .collect(Collectors.joining("; "));
        String mensagem = (motivos.size() == 1 ? "O campo " : "Os campos ") + detalhe + ".";
        return new ErroMapeado(HttpStatus.BAD_REQUEST, "Parâmetro inválido", mensagem, null, motivos);
    }

    private static String motivoDoTipo(MethodArgumentTypeMismatchException erro) {
        if (erro.getRequiredType() == TipoVeiculo.class) {
            String aceitos = Arrays.stream(TipoVeiculo.values())
                    .map(TipoVeiculo::slug)
                    .collect(Collectors.joining(", "));
            return "deve ser um destes tipos: " + aceitos;
        }
        return "tem um valor inválido";
    }

    private static Map<String, String> motivosDaValidacao(HandlerMethodValidationException erro) {
        Map<String, String> motivos = new LinkedHashMap<>();
        for (ParameterValidationResult resultado : erro.getParameterValidationResults()) {
            String motivo = resultado.getResolvableErrors().stream()
                    .map(falha -> falha.getDefaultMessage())
                    .findFirst()
                    .orElse("é inválido");
            motivos.putIfAbsent(nomeDoParametro(resultado.getMethodParameter()), motivo);
        }
        return motivos;
    }

    private static String nomeDoParametro(MethodParameter parametro) {
        PathVariable variavel = parametro.getParameterAnnotation(PathVariable.class);
        if (variavel != null && !variavel.value().isBlank()) {
            return variavel.value();
        }
        String nome = parametro.getParameterName();
        return nome != null ? nome : "parâmetro";
    }

    /** Erros do próprio Spring MVC (rota inexistente, método não permitido...). */
    private static ErroMapeado erroDoSpring(ErrorResponse erro) {
        HttpStatus status = Optional.ofNullable(HttpStatus.resolve(erro.getStatusCode().value()))
                .orElse(HttpStatus.INTERNAL_SERVER_ERROR);
        return switch (status) {
            case NOT_FOUND -> ErroMapeado.de(status, "Página não encontrada", "O endereço acessado não existe.");
            case METHOD_NOT_ALLOWED -> ErroMapeado.de(status, "Método não permitido",
                    "Esta operação não é aceita neste endereço.");
            default -> status.is4xxClientError()
                    ? ErroMapeado.de(status, "Requisição inválida", "A requisição não pôde ser atendida.")
                    : ErroMapeado.de(HttpStatus.INTERNAL_SERVER_ERROR, "Erro inesperado", MENSAGEM_INESPERADA);
        };
    }
}
