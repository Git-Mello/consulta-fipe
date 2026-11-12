package br.com.consultafipe.model.domain;

/** Validações compartilhadas pelos construtores dos records de domínio. */
final class Validacoes {

    private Validacoes() {
    }

    static void exigirTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " é obrigatório");
        }
    }
}
