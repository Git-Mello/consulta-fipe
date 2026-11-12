# CLAUDE.md

Consulta à Tabela FIPE: Java 25 + Spring Boot 4.1 (Spring MVC + Thymeleaf), padrão **MVC**. Visão completa no [README.md](README.md).

## Comandos

```bash
./mvnw test                                  # todos os testes (Windows: .\mvnw.cmd test)
./mvnw test -Dtest=FipeClientTest            # uma classe
./mvnw spring-boot:run                       # sobe em http://localhost:8080
./mvnw clean package                         # jar em target/
```

- O projeto exige **JDK 25**. Se `java -version` mostrar outra versão (ex.: num terminal aberto antes da instalação), aponte o `JAVA_HOME` para `C:\Program Files\Eclipse Adoptium\jdk-25.0.5.8-hotspot` antes de rodar o `mvnw`.
- No Windows, parar o `spring-boot:run` pode deixar o processo `java` filho vivo, segurando a porta 8080. Confira com `Get-NetTCPConnection -LocalPort 8080` e encerre só o processo da `ConsultaFipeApplication`.
- `./mvnw test` precisa passar com **0 testes ignorados** antes de qualquer commit.

## Arquitetura e regras entre camadas

Fluxo obrigatório: `View ⇄ Controller → Service → Client`. Cada camada só fala com a vizinha.

| Pacote | Papel | Pode depender de | Nunca |
|---|---|---|---|
| `model/client` | `FipeClient` + `dto/`: **único** lugar que conhece rotas, headers, JSON e status da FIPE | `config`, `exception` | Ser chamado por controller; expor DTO para fora do Model |
| `model/service` | `FipeService` (orquestração, ordenação, `@Cacheable`) + `ConversorFipe` (DTO → domínio, regra do ano `32000` = Zero km) | `client`, `domain`, `exception` | Conhecer HTTP, request, templates |
| `model/domain` | `TipoVeiculo`, `Marca`, `Modelo`, `AnoModelo`, `PrecoFipe`: records/enum imutáveis e validados no construtor | nada do projeto | Depender de Spring ou de DTO |
| `controller` | `PaginaController` (`@Controller`: `index` e fragmento `resultado :: card`) e `CatalogoController` (`@RestController` sob `/api`): só validam e delegam | `service`, `domain` | Regra de negócio, chamar o client |
| `exception` | Exceções próprias + `GlobalExceptionHandler` (único `@ControllerAdvice`) + `MapeadorDeErros` | `domain` | Expor mensagem técnica ou stack trace ao usuário |
| `config` | `FipeProperties` (`@ConfigurationProperties`), `FipeClientConfig` (RestClient + timeouts), `CacheConfig` (Caffeine) | — | `System.getenv` / `@Value` espalhados |
| View | `templates/` (Thymeleaf) e `static/` (`styles.css`, `consulta.js`) | atributos do modelo e rotas `/api` e `/consulta` | Regra de negócio; chamar a API da FIPE; receber DTO externo |

- Uma mudança no formato da API da FIPE deve afetar só `model/client` e, no máximo, o `ConversorFipe`. A exceção é `TipoVeiculo.codigoApi()` (`cars`, `motorcycles`, `trucks`), pedida pela especificação.
- Erros: o client traduz cada status em uma `FipeException` (`RecursoNaoEncontrado`, `RequisicaoInvalida`, `FalhaAutenticacao`, `LimiteDeUsoExcedido`, `ServicoExternoIndisponivel`). O `GlobalExceptionHandler` responde no formato da rota: `/api/**` → `ProblemDetail`; `/consulta/**` → `fragments/resultado :: erro`; o resto → `error.html`. Para mudar status ou mensagens, mexa no `MapeadorDeErros`.
- Cache só no service (`@Cacheable(sync = true)`). Os nomes ficam em `FipeService.CACHE_*`, e o `CacheConfig` só aceita esses nomes: um cache novo precisa ser registrado lá.

## Convenções

- **Código e testes em português** (classes, métodos, variáveis, mensagens). Termos técnicos consagrados em inglês continuam como estão (`Controller`, `Service`, `DTO`).
- Injeção **só por construtor**, com campos `private final`. Imutabilidade por padrão (`record`, `List.copyOf`, `toList()`).
- Configuração nova: um campo em `FipeProperties` (validado), um placeholder com valor padrão no `application.yml` e uma linha no `.env.example` e na tabela do README.
- Validação de `@PathVariable`: anotações compostas (`@CodigoNumerico`, `@CodigoAno`) com a **validação de método nativa do Spring 7**. **Não** use `@Validated` na classe: ele troca a `HandlerMethodValidationException` (que traz o nome do campo) por `ConstraintViolationException`.
- `spring.mvc.problemdetails` fica **desligado** de propósito, e o `GlobalExceptionHandler` tem `@Order(HIGHEST_PRECEDENCE)`. O advice do Boot capturava os erros do Spring e respondia JSON até nas rotas de página.
- Logs com SLF4J e placeholders, sempre com método, rota e status. **Nunca** logar o token. O `FipeProperties.toString()` já o mascara.
- Thymeleaf: só `th:text`, nunca `th:utext`. Acesse records com chamada de método: `${preco.valor()}`.
- JavaScript (`consulta.js`, sem build): três seções (HTTP / Tela / Fluxo), textos via `textContent` ou `new Option`. O **único** `innerHTML` insere o fragmento vindo do nosso servidor. O `SegurancaDaViewTest` quebra se isso mudar.
- Commits em Conventional Commits, em português (`feat(fipe): ...`), um por etapa lógica. O `PROMPT.md` **não** é versionado.

## Testes

- Um tipo de teste por camada:
  - client: `MockRestServiceServer`;
  - service: Mockito;
  - cache: contexto mínimo do Spring;
  - controllers: `@WebMvcTest` com `@MockitoBean`;
  - view: `TemplatesTest`.
- Nomes: `<Classe>Test`, métodos `deve...` em português, corpo em preparação / execução / verificação, asserções com AssertJ.
- **Nenhum teste acessa a internet.** As respostas da FIPE são fixtures reais em `src/test/resources/fixtures/` (ver o `LEIA-ME.md` de lá). Use-as em vez de escrever JSON de cabeça.
- Objetos de domínio de exemplo ficam em `fixture/DominioFixtures`.
- No MockMvc, `get("/x/%20")` codifica o `%` de novo. Para mandar a URL como está, use `get(URI.create(url))`.
