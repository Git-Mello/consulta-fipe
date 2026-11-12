package br.com.consultafipe.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Guarda as regras de saída escapada da View, para que uma mudança futura não as quebre sem querer:
 * templates só com {@code th:text} e o JavaScript inserindo como HTML apenas o fragmento do servidor.
 */
class SegurancaDaViewTest {

    private static final Path TEMPLATES = Path.of("src/main/resources/templates");
    private static final Path SCRIPT = Path.of("src/main/resources/static/js/consulta.js");

    @Test
    void nenhumTemplateDeveUsarThUtext() throws IOException {
        try (Stream<Path> arquivos = Files.walk(TEMPLATES)) {
            List<Path> comUtext = arquivos
                    .filter(arquivo -> arquivo.toString().endsWith(".html"))
                    .filter(arquivo -> ler(arquivo).contains("th:utext"))
                    .toList();

            assertThat(comUtext).isEmpty();
        }
    }

    @Test
    void oJavaScriptSoDeveInserirHtmlNoFragmentoDoServidor() throws IOException {
        String script = Files.readString(SCRIPT, StandardCharsets.UTF_8);

        assertThat(Pattern.compile("\\.innerHTML\\s*=").matcher(script).results().count())
                .as("atribuições a innerHTML em consulta.js").isEqualTo(1);
        assertThat(script).contains("areaResultado.innerHTML = html;");
        assertThat(script).doesNotContain("outerHTML", "insertAdjacentHTML", "document.write", "eval(");
    }

    @Test
    void oJavaScriptNaoDeveChamarAApiDaFipeDireto() throws IOException {
        String script = Files.readString(SCRIPT, StandardCharsets.UTF_8);

        assertThat(script).doesNotContain("fipe.api.br", "parallelum", "X-Subscription-Token");
    }

    private static String ler(Path arquivo) {
        try {
            return Files.readString(arquivo, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível ler " + arquivo, e);
        }
    }
}
