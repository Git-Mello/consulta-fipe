# Consulta FIPE

Aplicação web para consultar o preço de veículos pela **Tabela FIPE**. O usuário escolhe o tipo de veículo → marca → modelo → ano e vê o preço FIPE com os detalhes do veículo.

Os dados vêm da API pública da FIPE ([fipe.api.br](https://fipe.api.br/docs/api)). O navegador **nunca** chama essa API direto: todas as consultas passam pela aplicação, e o token (quando houver) fica só no servidor.

> 🚧 Em construção. Este README será completado ao longo das etapas de implementação.

## Stack

- Java 25 (LTS)
- Spring Boot 4.1 (Spring MVC + Thymeleaf), Maven Wrapper
- Cache com Caffeine
- HTML, CSS e JavaScript puros (sem framework de frontend)

## Arquitetura (MVC)

```
View (Thymeleaf + JS)  ⇄  Controller  →  Service  →  Client  →  API da FIPE
```

| Camada | Pacote | Responsabilidade |
|---|---|---|
| Model | `model.domain` | Records e enums do domínio (`TipoVeiculo`, `Marca`, `Modelo`, `AnoModelo`, `PrecoFipe`) |
| Model | `model.service` | Regras, conversão DTO → domínio e cache |
| Model | `model.client` | Única classe que conhece a API da FIPE (+ DTOs externos) |
| Controller | `controller` | `PaginaController` (páginas/fragmentos) e `CatalogoController` (JSON dos selects) |
| View | `templates/`, `static/` | Templates Thymeleaf, CSS e JS |

## Pré-requisitos

- JDK 25 (`java -version`)
- Não é preciso instalar o Maven: o projeto usa o Maven Wrapper (`mvnw`)

## Configuração

Todas as variáveis são opcionais. Veja [`.env.example`](.env.example): copie para `.env` (não versionado) ou defina como variáveis de ambiente.

| Variável | Padrão | Descrição |
|---|---|---|
| `FIPE_API_BASE_URL` | `https://fipe.api.br/api/v2` | URL base da API |
| `FIPE_API_TOKEN` | *(vazio)* | Token gratuito em [fipe.api.br/register](https://fipe.api.br/register). Sem token: 500 req/dia; com token: 1.000 |
| `FIPE_CONNECT_TIMEOUT` / `FIPE_READ_TIMEOUT` | `3s` / `5s` | Timeouts das chamadas à FIPE |
| `FIPE_MAX_TENTATIVAS` / `FIPE_ESPERA_INICIAL` | `2` / `300ms` | Novas tentativas em falhas temporárias |
| `FIPE_CACHE_CATALOGO_TTL` | `24h` | Expiração do cache de marcas, modelos e anos |
| `FIPE_CACHE_PRECO_TTL` | `6h` | Expiração do cache de preços |
| `FIPE_CACHE_TAMANHO_MAXIMO` | `10000` | Máximo de entradas por cache |
| `SERVER_PORT` | `8080` | Porta HTTP |

## Como rodar

```bash
./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
```

Acesse http://localhost:8080.

## Como testar

```bash
./mvnw test
```

Nenhum teste acessa a internet.
