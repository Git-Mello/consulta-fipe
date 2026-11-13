# Consulta FIPE

Aplicação web para consultar o preço de veículos pela **Tabela FIPE**. O usuário escolhe tipo de veículo → marca → modelo → ano e vê o preço FIPE com os detalhes do veículo, sem recarregar a página. Marca e modelo são caixas de busca: basta digitar parte do nome (ex.: "argo 1.0") e escolher na lista. O ano aparece só como ano ("2018", "Zero km"), e o combustível fica no resultado.

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
└── static/          css/styles.css, js/consulta.js, fonts/ (Barlow, licença OFL), favicon.svg
```

## Pré-requisitos

- **JDK 25** (`java -version`). Nas versões anteriores ao 25 o projeto não compila. Se `java -version` mostrar outra versão, aponte o `JAVA_HOME` para o JDK 25 (no PowerShell: `$env:JAVA_HOME = "C:\caminho\do\jdk-25"`).
- Não é preciso instalar o Maven: o projeto usa o Maven Wrapper (`mvnw`).
- Acesso à internet para a aplicação falar com a API da FIPE. Os testes não precisam.

## Como rodar

```bash
./mvnw spring-boot:run        # Windows: .\mvnw.cmd spring-boot:run
./mvnw test                   # Windows: .\mvnw.cmd test
```

Acesse **http://localhost:8080**. Para parar, use `Ctrl + C`.

## Configuração

Tudo é opcional: a aplicação funciona sem token. Para mudar algo, copie o [`.env.example`](.env.example) para `.env` (que não é versionado) ou defina as variáveis de ambiente, que têm prioridade.

| Variável | Padrão | Para quê |
|---|---|---|
| `FIPE_API_TOKEN` | *(vazio)* | Token gratuito em [fipe.api.br/register](https://fipe.api.br/register): sobe a cota de 500 para 1.000 consultas por dia |
| `FIPE_API_BASE_URL` | `https://fipe.api.br/api/v2` | URL da API da FIPE |
| `FIPE_CONNECT_TIMEOUT` / `FIPE_READ_TIMEOUT` | `5s` / `5s` | Tempo máximo de conexão e de resposta da FIPE |
| `FIPE_MAX_TENTATIVAS` / `FIPE_ESPERA_INICIAL` | `2` / `300ms` | Novas tentativas quando a FIPE falha temporariamente |
| `FIPE_CACHE_CATALOGO_TTL` / `FIPE_CACHE_PRECO_TTL` | `24h` / `6h` | Por quanto tempo marcas, modelos e anos, e os preços, ficam em cache |
| `FIPE_CACHE_TAMANHO_MAXIMO` | `10000` | Máximo de itens em cada cache |
| `SERVER_PORT` | `8080` | Porta da aplicação |
