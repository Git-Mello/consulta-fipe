# Fixtures da API da FIPE

Respostas **reais** de `https://fipe.api.br/api/v2`, coletadas com `curl` em 12/11/2026 (referência novembro/2026).
O exemplo é o Fiat ARGO DRIVE 1.0 6V Flex 2018 (marca `21`, modelo `7965`, ano `2018-5`).

| Arquivo | Requisição |
|---|---|
| `marcas-carros.json` | `GET /cars/brands` |
| `modelos-carros-marca-21.json` | `GET /cars/brands/21/models` |
| `anos-carros-marca-21-modelo-7965.json` | `GET /cars/brands/21/models/7965/years` |
| `preco-carros-marca-21-modelo-7965-ano-2018-5.json` | `GET /cars/brands/21/models/7965/years/2018-5` |
| `erro-404.json` | `GET /cars/brands/21/models/7965/years/1990-1` |
| `erro-400.json` | `GET /cars/brands/abc/models` |
| `erro-401.json` | qualquer rota com `X-Subscription-Token` inválido |
| `erro-429-sintetico.json` | **sintético**: não foi possível provocar um 429 real sem gastar a cota. Segue o formato `{"error": "..."}` documentado e observado nos outros erros. |

Os preços mudam todo mês: atualize os arquivos só se o formato da API mudar.
