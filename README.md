# Consulta FIPE

Aplicação web para consultar o preço de veículos pela **Tabela FIPE**. O usuário escolhe tipo de veículo → marca → modelo → ano e vê o preço FIPE com os detalhes do veículo, sem recarregar a página.

Os dados vêm da API pública da FIPE ([fipe.api.br](https://fipe.api.br/docs/api)). O navegador **nunca** chama essa API direto: todas as consultas passam pela aplicação, e o token (quando houver) fica só no servidor.

## Stack

- Java 25 (LTS) e Spring Boot 4.1 (Spring MVC + Thymeleaf), com Maven Wrapper
- `RestClient` do Spring para a API da FIPE, com timeouts e novas tentativas (`RetryTemplate` do Spring 7)
- Cache com Caffeine
- HTML (Thymeleaf), CSS e JavaScript puros, sem framework de frontend e sem build de JS

## Arquitetura (MVC)

```
Navegador ──▶ Controller ──▶ Service ──▶ Client ──▶ API da FIPE
 (View)   ◀──  (valida e   ◀── (regras, ◀── (HTTP,
               escolhe a       conversão,    DTOs
               view)           cache)        externos)
```

Cada camada só conversa com a vizinha. O Controller nunca chama o Client, e a View nunca recebe DTOs da API externa.

| Camada | Onde | Responsabilidade |
|---|---|---|
| **Model**, domínio | `model/domain` | `TipoVeiculo` (enum) e os records `Marca`, `Modelo`, `AnoModelo`, `PrecoFipe`, imutáveis e validados no construtor |
| **Model**, regras | `model/service` | `FipeService`: converte DTO → domínio (`ConversorFipe`), traduz o ano `32000` para "Zero km", ordena as listas e aplica o cache |
| **Model**, acesso a dados | `model/client` | `FipeClient`: única classe que conhece a API (rotas, header do token, status HTTP), com DTOs no formato externo |
| **Controller** | `controller` | `PaginaController` (página inicial e fragmento do preço) e `CatalogoController` (JSON dos selects). Finos: validam a entrada e delegam ao service |
| **View** | `templates/`, `static/` | Templates Thymeleaf renderizados no servidor. O card de resultado é um fragmento; o `consulta.js` só preenche os selects e insere o fragmento |
| Apoio | `config`, `exception` | Configuração tipada (`FipeProperties`), `RestClient`, caches, exceções próprias e o `@ControllerAdvice` central |

### Estrutura

```
src/main/java/br/com/consultafipe/
├── ConsultaFipeApplication.java
├── controller/      PaginaController, CatalogoController, TipoVeiculoConverter, @CodigoNumerico, @CodigoAno
├── model/
│   ├── domain/      TipoVeiculo, Marca, Modelo, AnoModelo, PrecoFipe
│   ├── service/     FipeService, ConversorFipe
│   └── client/      FipeClient + dto/ (ItemFipeDto, DetalheVeiculoFipeDto)
├── config/          FipeProperties, FipeClientConfig, CacheConfig
└── exception/       FipeException e filhas, GlobalExceptionHandler, MapeadorDeErros
src/main/resources/
├── application.yml
├── templates/       index.html, error.html, fragments/layout.html, fragments/resultado.html
└── static/          css/styles.css, js/consulta.js, favicon.svg
```

## Pré-requisitos

- **JDK 25** (`java -version`). Nas versões anteriores ao 25 o projeto não compila.
- Não é preciso instalar o Maven: o projeto usa o Maven Wrapper (`mvnw`).
- Acesso à internet para a aplicação falar com a API da FIPE. Os testes não precisam.

## Configuração

Todas as variáveis são opcionais: a aplicação funciona sem token. Veja [`.env.example`](.env.example). Há duas formas de configurar:
- copiar o arquivo para `.env` na pasta do projeto: ele é lido ao subir a aplicação e nunca é versionado;
- definir as variáveis de ambiente, que têm prioridade sobre o `.env`.

| Variável | Padrão | Descrição |
|---|---|---|
| `FIPE_API_BASE_URL` | `https://fipe.api.br/api/v2` | URL base da API |
| `FIPE_API_TOKEN` | *(vazio)* | Token gratuito em [fipe.api.br/register](https://fipe.api.br/register), enviado no header `X-Subscription-Token`. Sem token: 500 requisições/dia; com token: 1.000 |
| `FIPE_CONNECT_TIMEOUT` | `5s` | Timeout de conexão, que inclui o handshake TLS (medido em ~3,1s em conexões "frias") |
| `FIPE_READ_TIMEOUT` | `5s` | Timeout de leitura da resposta |
| `FIPE_MAX_TENTATIVAS` | `2` | Novas tentativas em falhas temporárias (timeout, conexão, 502, 503, 504). Nunca em 4xx nem 429 |
| `FIPE_ESPERA_INICIAL` | `300ms` | Espera antes da 1ª nova tentativa (dobra a cada tentativa) |
| `FIPE_CACHE_CATALOGO_TTL` | `24h` | Expiração do cache de marcas, modelos e anos |
| `FIPE_CACHE_PRECO_TTL` | `6h` | Expiração do cache de preços |
| `FIPE_CACHE_TAMANHO_MAXIMO` | `10000` | Máximo de entradas por cache |
| `SERVER_PORT` | `8080` | Porta HTTP da aplicação |

Exemplo no PowerShell:

```powershell
$env:FIPE_API_TOKEN = "seu-token"
.\mvnw.cmd spring-boot:run
```

## Como rodar

```bash
./mvnw spring-boot:run        # Windows: .\mvnw.cmd spring-boot:run
```

Acesse **http://localhost:8080**. O health check fica em http://localhost:8080/actuator/health.

Para gerar o jar: `./mvnw clean package`, que cria `target/consulta-fipe-0.0.1-SNAPSHOT.jar`, e depois `java -jar target/consulta-fipe-0.0.1-SNAPSHOT.jar`.

## Como testar

```bash
./mvnw test                                   # todos (125 testes)
./mvnw test -Dtest=FipeServiceTest            # uma classe
./mvnw test -Dtest=FipeServiceTest#deve...    # um método
```

**Nenhum teste acessa a internet.** Há um tipo de teste por camada:

| Camada | Testes | Como |
|---|---|---|
| Model, client | `FipeClientTest`, `FipeClientConfigTest` | `MockRestServiceServer` com **respostas reais da API** salvas em `src/test/resources/fixtures/`. Cobre 200, 400, 401, 403, 404, 429, 500, 502/503, timeout, novas tentativas e o token no header. O timeout real é testado com um servidor HTTP local |
| Model, domínio e regras | `FipeServiceTest`, `FipeServiceCacheTest`, `TipoVeiculoTest`, `PrecoFipeTest` | Mockito no lugar do client: conversão, Zero km, ordenação e erros. O cache é testado com o proxy real do Spring |
| Controller | `PaginaControllerTest`, `CatalogoControllerTest` | `@WebMvcTest` + MockMvc: views, atributos do modelo, JSON, `ProblemDetail`, fragmento de erro e validação da entrada |
| View | `TemplatesTest`, `SegurancaDaViewTest` | Renderiza `index`, o card, o fragmento e a página de erro. Também garante que nenhum template usa `th:utext` e que o JS só insere como HTML o fragmento do servidor |

## Rotas

### Páginas (`PaginaController`)

| Método | Rota | Resposta |
|---|---|---|
| GET | `/` | Página inicial (`index`), com o select de tipos já preenchido |
| GET | `/consulta/{tipo}/{marcaId}/{modeloId}/{anoId}` | Fragmento HTML `fragments/resultado :: card` com o preço |

### JSON para os selects (`CatalogoController`)

| Método | Rota | Retorna |
|---|---|---|
| GET | `/api/{tipo}/marcas` | `[{"codigo":"21","nome":"Fiat"}, …]` |
| GET | `/api/{tipo}/marcas/{marcaId}/modelos` | `[{"codigo":"7965","nome":"ARGO DRIVE 1.0 6V Flex"}, …]` |
| GET | `/api/{tipo}/marcas/{marcaId}/modelos/{modeloId}/anos` | `[{"codigo":"32000-5","descricao":"Zero km Flex"}, {"codigo":"2026-5","descricao":"2026 Flex"}, …]` |

Os parâmetros são validados antes de qualquer chamada à FIPE:

- `{tipo}`: `carros`, `motos` ou `caminhoes`
- `marcaId` e `modeloId`: só dígitos, até 6
- `anoId`: no formato ano-combustível, por exemplo `2018-5`

Também existe `GET /actuator/health`.

### Erros

Mensagens em português, sem stack trace nem detalhes internos. O formato depende da rota:

- `/api/**` responde com `ProblemDetail` (RFC 9457);
- `/consulta/**` responde com o fragmento de erro, que a tela mostra no lugar do card;
- as demais rotas mostram a página `error.html`.

| Situação | Status |
|---|---|
| Parâmetro inválido | 400, indicando o campo (em `/api`, também no campo `campos` do `ProblemDetail`) |
| Veículo inexistente na FIPE | 404 |
| Cota diária da FIPE esgotada (429) | 503, com o header `Retry-After` e "tente novamente em cerca de X horas" |
| FIPE fora do ar, timeout ou token recusado | 503, "serviço indisponível no momento" |
| Erro inesperado | 500 (detalhes só no log do servidor) |

Exemplo:

```json
{"detail":"O campo 'marcaId' deve conter apenas números (até 6 dígitos).",
 "instance":"/api/carros/marcas/abc/modelos","status":400,"title":"Parâmetro inválido",
 "campos":{"marcaId":"deve conter apenas números (até 6 dígitos)"}}
```

## Cache e cota da API

A API libera 500 requisições por dia sem token. Uma consulta completa custa 4 chamadas (marcas, modelos, anos, preço) quando nada está em cache. Os caches Caffeine ficam no `FipeService`:

- **marcas**, **modelos** e **anos**: 24h, porque só mudam com a tabela mensal;
- **preços**: 6h.

Na prática, só combinações novas consomem a cota. Erros nunca ficam em cache. Acessos simultâneos à mesma chave geram uma única chamada (`sync = true`).

## Decisões

- **Header do token:** `X-Subscription-Token`, o header documentado pela FIPE.
- **Ano `32000`:** a FIPE usa esse ano para veículos zero km. O service o traduz para "Zero km".
- **Ordenação:** marcas e modelos em ordem alfabética pt-BR, sem diferenciar maiúsculas nem acentos. Anos do mais novo para o mais antigo, com o Zero km primeiro.
- **Preço:** a API já o devolve formatado em reais (`"R$ 48.136,00"`), e ele é exibido como veio.
- **`spring.mvc.problemdetails` desligado:** o handler central já responde `ProblemDetail` em `/api`, e o advice do Boot interceptaria os erros também nas rotas de página.
