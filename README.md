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
