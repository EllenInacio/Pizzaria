# Dashboard Administrativo — PediuPatrão

Documentação da atividade **Dashboard Administrativo**: requisitos, projeto da API, documentação dos endpoints e evidências de testes.

- **Tecnologias:** Java 21, Spring Boot 3.4, Spring Security, Spring Data MongoDB, Thymeleaf e Chart.js 4.4.1 (gráficos).
- **Tela:** `GET /dashboard`, no menu lateral **Dashboard**.
- **API:** `GET /api/dashboard/*`, somente leitura.

---

## 1. Contexto e objetivo

O dashboard permite que o responsável pela pizzaria (perfis **GERENTE** e **ADMIN**) acompanhe o desempenho das vendas, a situação dos pedidos e os produtos mais vendidos, apoiando a tomada de decisão.

Todos os números vêm de uma API REST criada para o painel. Ela lê os pedidos gravados no MongoDB pelo próprio sistema (coleção `pedidos`). O navegador só exibe os dados: **todos os cálculos são feitos no servidor**.

---

## 2. Requisitos funcionais

| ID | Requisito | Como foi atendido |
|---|---|---|
| **RF01** | Indicadores gerais de vendas | Cartões com faturamento, pedidos válidos/total/em andamento, ticket médio, itens vendidos, descontos concedidos e cancelamentos com taxa (%). Endpoint `GET /api/dashboard/resumo`. |
| **RF02** | Análise de vendas (gráficos) | Gráfico de barras de vendas por **dia** ou **mês**, com seletor de métrica (**faturamento** ou **quantidade de pedidos**). Dias sem venda aparecem com zero. Endpoint `GET /api/dashboard/vendas-por-periodo`. |
| **RF03** | Situação dos pedidos | Gráfico com a quantidade e o percentual de pedidos em cada status, na ordem do fluxo (Recebido → … → Finalizado / Cancelado), mais a tabela dos pedidos mais recentes. Endpoints `GET /api/dashboard/situacao-pedidos` e `GET /api/dashboard/pedidos-recentes`. |
| **RF04** | Produtos mais vendidos | Ranking com posição, quantidade vendida e faturamento por produto. Endpoint `GET /api/dashboard/produtos-mais-vendidos`. |
| **RF05** | Filtros de consulta | Data inicial, data final, status e agrupamento (dia/mês), além de atalhos "7 dias", "30 dias" e "Limpar". Os filtros são enviados à API e validados no servidor. |
| **RF06** | Integração com a API | A tela chama os 5 endpoints em paralelo (`fetch` + `Promise.all`), com a sessão autenticada do usuário. |
| **RF07** | Atualização e tratamento de dados | Botão **Atualizar**, atualização automática a cada 60 s (pode ser desligada) e horário da última atualização. Mensagens de erro claras, estados vazios ("Nenhuma venda no período") e regras de tratamento de dados (seção 4). |

## 3. Requisitos não funcionais

| ID | Categoria | Requisito |
|---|---|---|
| RNF01 | Segurança | Tela e API restritas aos perfis ADMIN e GERENTE, aplicadas no backend pelo Spring Security. Outros perfis recebem **403** e acessos sem login à API recebem **401**. |
| RNF02 | Segurança | A API é somente leitura (apenas `GET`); outros métodos em `/api/dashboard/**` são negados. Os dados de clientes expostos se limitam ao nome. |
| RNF03 | Confiabilidade | Indicadores calculados no servidor com `BigDecimal` (2 casas, arredondamento HALF_UP), sem depender do navegador. |
| RNF04 | Desempenho | O período é filtrado diretamente no MongoDB (consulta por `dataHoraEntrada`), trazendo apenas os pedidos do intervalo. |
| RNF05 | Usabilidade | Layout responsivo (Bootstrap), valores no padrão brasileiro (R$ e datas dd/mm), tooltips nos gráficos e o mesmo padrão visual do sistema. |
| RNF06 | Acessibilidade | Gráficos de uma única cor com rótulos nos eixos (a informação não depende só de cor), tabelas com os valores do ranking e gráficos com `aria-label`. |
| RNF07 | Manutenibilidade | Organização em camadas, igual ao resto do projeto: `DashboardController` (API), `DashboardService` (regras) e `PedidoRepository` (consulta). |
| RNF08 | Testabilidade | Regras de cálculo cobertas por testes unitários automatizados (JUnit 5 + Mockito). |
| RNF09 | Compatibilidade | Mantém Java, Spring Boot, Thymeleaf e MongoDB. A única dependência nova é a Chart.js, carregada via CDN só na tela do dashboard. |

## 4. Regras de negócio e tratamento de dados

1. **Período:** considera a data/hora de **entrada** do pedido. Sem filtro de datas, a tela usa os últimos 30 dias. Na API, sem filtro, considera todos os pedidos até hoje.
2. **Cancelados:** contam no total de pedidos, na situação dos pedidos e na taxa de cancelamento. **Não** entram em faturamento, ticket médio, itens vendidos, descontos, gráfico de vendas nem ranking de produtos.
3. **Faturamento:** soma do `valorTotal` (já com desconto) dos pedidos não cancelados.
4. **Ticket médio:** faturamento ÷ pedidos válidos (não cancelados). É zero quando não há pedidos.
5. **Taxa de cancelamento:** cancelados ÷ total de pedidos do período × 100.
6. **Pedidos antigos:** registros sem data de entrada são desconsiderados, e valores ou itens ausentes são tratados como zero.
7. **Ranking:** ordenado por quantidade vendida. Em caso de empate, por faturamento e depois por nome. Itens de pedidos cancelados não contam.
8. **Situação dos pedidos:** aplica o filtro de período, mas **ignora o filtro de status**, para sempre mostrar a distribuição completa.
9. **Períodos sem venda:** no agrupamento por dia, os dias sem venda aparecem com zero (até 366 dias), para o gráfico não "pular" datas.

---

## 5. Documentação da API

**URL base:** `http://localhost:8080/api/dashboard`. Todos os endpoints são `GET`, exigem login com perfil **ADMIN** ou **GERENTE** e respondem em JSON.

### 5.1 Parâmetros comuns (todos opcionais)

| Parâmetro | Tipo | Formato / valores | Descrição |
|---|---|---|---|
| `dataInicio` | data | `AAAA-MM-DD` | Primeiro dia do período (inclusive) |
| `dataFim` | data | `AAAA-MM-DD` | Último dia do período (inclusive) |
| `status` | texto | `RECEBIDO`, `EM_PREPARACAO`, `PRONTO`, `SAIU_PARA_ENTREGA`, `RETIRADO`, `FINALIZADO`, `CANCELADO` | Considera apenas pedidos nesse status (maiúsculas ou minúsculas) |

### 5.2 Erros possíveis (todos os endpoints)

| HTTP | Quando | Corpo da resposta |
|---|---|---|
| 400 | Data em formato inválido | `{"erro": "Data inválida em dataInicio. Use o formato AAAA-MM-DD."}` |
| 400 | `dataInicio` posterior a `dataFim` | `{"erro": "A dataInicio não pode ser posterior à dataFim."}` |
| 400 | Status inexistente | `{"erro": "Status inválido. Use: RECEBIDO, EM_PREPARACAO, PRONTO, SAIU_PARA_ENTREGA, RETIRADO, FINALIZADO, CANCELADO."}` |
| 400 | `limite` fora de 1–50 | `{"erro": "O limite deve estar entre 1 e 50."}` |
| 400 | `agrupamento` diferente de DIA/MES | `{"erro": "Agrupamento inválido. Use DIA ou MES."}` |
| 401 | Requisição sem login | Sem corpo |
| 403 | Perfil ATENDENTE (ou sem perfil) | `{"erro": "Acesso negado: operação não permitida para o seu perfil."}` |
| 403 | Método diferente de GET | `{"erro": "Acesso negado: operação não permitida para o seu perfil."}` |

> Os exemplos abaixo usam um conjunto pequeno de dados: 3 pedidos entre 01/11 e 03/11/2026, sendo 1 finalizado (R$ 90,00), 1 cancelado (R$ 12,00) e 1 em preparação (R$ 69,00 com 10% de desconto = R$ 62,10). É o mesmo cenário do teste automatizado `DashboardServiceTest`.

### 5.3 `GET /api/dashboard/resumo` — RF01

Indicadores gerais do período.

**Requisição**
```
GET /api/dashboard/resumo?dataInicio=2026-11-01&dataFim=2026-11-03
```

**Resposta 200**
```json
{
  "totalPedidos": 3,
  "pedidosValidos": 2,
  "pedidosEmAndamento": 1,
  "pedidosFinalizados": 1,
  "pedidosCancelados": 1,
  "taxaCancelamento": 33.33,
  "faturamento": 152.10,
  "ticketMedio": 76.05,
  "totalDescontos": 6.90,
  "itensVendidos": 5
}
```

| Campo | Descrição |
|---|---|
| `totalPedidos` | Todos os pedidos do período/filtro |
| `pedidosValidos` | Pedidos não cancelados |
| `pedidosEmAndamento` | Recebido, em preparação, pronto, saiu para entrega ou retirado |
| `taxaCancelamento` | Percentual de cancelados sobre o total |
| `faturamento` | Soma do valor total dos pedidos válidos (R$) |
| `ticketMedio` | Faturamento ÷ pedidos válidos (R$) |
| `totalDescontos` | Descontos concedidos nos pedidos válidos (R$) |
| `itensVendidos` | Unidades vendidas nos pedidos válidos |

### 5.4 `GET /api/dashboard/vendas-por-periodo` — RF02

Faturamento e quantidade de pedidos válidos por dia ou por mês.

| Parâmetro extra | Valores | Padrão |
|---|---|---|
| `agrupamento` | `DIA` ou `MES` | `DIA` |

**Requisição**
```
GET /api/dashboard/vendas-por-periodo?dataInicio=2026-11-01&dataFim=2026-11-03&agrupamento=DIA
```

**Resposta 200**
```json
[
  { "periodo": "2026-11-01", "quantidadePedidos": 1, "faturamento": 90.00 },
  { "periodo": "2026-11-02", "quantidadePedidos": 0, "faturamento": 0.00 },
  { "periodo": "2026-11-03", "quantidadePedidos": 1, "faturamento": 62.10 }
]
```

Com `agrupamento=MES`, o `periodo` vem no formato `AAAA-MM` (ex.: `"2026-11"`).

### 5.5 `GET /api/dashboard/situacao-pedidos` — RF03

Quantidade e percentual por status, sempre com os 7 status na ordem do fluxo.

**Requisição**
```
GET /api/dashboard/situacao-pedidos?dataInicio=2026-11-01&dataFim=2026-11-03
```

**Resposta 200**
```json
[
  { "status": "RECEBIDO", "quantidade": 0, "percentual": 0.00 },
  { "status": "EM_PREPARACAO", "quantidade": 1, "percentual": 33.33 },
  { "status": "PRONTO", "quantidade": 0, "percentual": 0.00 },
  { "status": "SAIU_PARA_ENTREGA", "quantidade": 0, "percentual": 0.00 },
  { "status": "RETIRADO", "quantidade": 0, "percentual": 0.00 },
  { "status": "FINALIZADO", "quantidade": 1, "percentual": 33.33 },
  { "status": "CANCELADO", "quantidade": 1, "percentual": 33.33 }
]
```

### 5.6 `GET /api/dashboard/produtos-mais-vendidos` — RF04

Ranking de produtos por quantidade vendida (pedidos não cancelados).

| Parâmetro extra | Valores | Padrão |
|---|---|---|
| `limite` | 1 a 50 | 5 |

**Requisição**
```
GET /api/dashboard/produtos-mais-vendidos?dataInicio=2026-11-01&dataFim=2026-11-03&limite=5
```

**Resposta 200**
```json
[
  { "posicao": 1, "produtoId": "6730a1...", "nomeProduto": "Pizza Calabresa", "quantidade": 3, "faturamento": 135.00 },
  { "posicao": 2, "produtoId": "6730a2...", "nomeProduto": "Refrigerante 2L", "quantidade": 2, "faturamento": 24.00 }
]
```

> O `faturamento` do ranking é a soma dos subtotais dos itens (preço × quantidade), antes do desconto do pedido.

### 5.7 `GET /api/dashboard/pedidos-recentes` — RF03

Últimos pedidos do período, do mais novo para o mais antigo.

| Parâmetro extra | Valores | Padrão |
|---|---|---|
| `limite` | 1 a 50 | 10 |

**Requisição**
```
GET /api/dashboard/pedidos-recentes?limite=2
```

**Resposta 200**
```json
[
  {
    "id": "6731b0...",
    "clienteNome": "João",
    "status": "EM_PREPARACAO",
    "quantidadeItens": 3,
    "valorTotal": 62.10,
    "dataHoraEntrada": "2026-11-03T21:00:00",
    "registradoPor": "atendente01"
  },
  {
    "id": "6731a9...",
    "clienteNome": "Maria",
    "status": "CANCELADO",
    "quantidadeItens": 1,
    "valorTotal": 12.00,
    "dataHoraEntrada": "2026-11-01T20:00:00",
    "registradoPor": "atendente01"
  }
]
```

Se o cliente foi excluído, `clienteNome` vem como `"Cliente não encontrado"`.

### 5.8 Exemplo de erro

```
GET /api/dashboard/resumo?dataInicio=2026-11-10&dataFim=2026-11-01
```
```
HTTP/1.1 400 Bad Request
{"erro": "A dataInicio não pode ser posterior à dataFim."}
```

---

## 6. Principais decisões de análise e projeto

| Decisão | Justificativa |
|---|---|
| Cinco endpoints separados por assunto (resumo, vendas, situação, produtos, recentes) | Cada endpoint atende um requisito e pode ser reutilizado ou testado sozinho. A tela chama todos em paralelo, então o tempo total é o do mais lento. |
| Mesmos filtros em todos os endpoints | Indicadores, gráficos e tabelas ficam sempre coerentes entre si para o mesmo período. |
| Cálculos no servidor (`DashboardService`) | Os números não podem ser manipulados pelo navegador, e a regra fica testável em JUnit. Segue o mesmo princípio do cálculo do total do pedido. |
| Cancelados fora do faturamento | Pedido cancelado não gera receita, mas o gestor precisa enxergar a taxa de cancelamento. |
| Filtro de período feito no MongoDB | Evita carregar a coleção inteira quando o período é informado. |
| Gráficos de barras de uma única cor, sem dois eixos Y | Faturamento (R$) e quantidade de pedidos têm escalas diferentes. Em vez de um gráfico com dois eixos, que confunde a leitura, há um seletor de métrica. |
| Acesso só para ADMIN e GERENTE | Indicadores de faturamento são informação gerencial. O ATENDENTE não precisa deles para a sua função. |
| Atualização automática a cada 60 s | Acompanha a operação em tempo quase real sem sobrecarregar o servidor. |

---

## 7. Fluxo de versionamento (Gitflow)

| Branch | Uso |
|---|---|
| `main` | Versão estável entregue |
| `develop` | Integração das funcionalidades |
| `feature/dashboard-api` | Implementação da API e do dashboard (esta atividade) |

Fluxo usado: `feature/dashboard-api` → *merge* em `develop` → *merge* em `main`.

---

## 8. Evidências de testes

### 8.1 Testes automatizados

Execute `.\mvnw.cmd test` (Windows) ou `./mvnw test` (Linux/macOS).

`src/test/java/com/umc/pediupatrao/service/DashboardServiceTest.java` — 11 testes do dashboard:

| Teste | Requisito | O que verifica |
|---|---|---|
| `resumoDesconsideraCanceladosNoFaturamento` | RF01 | Faturamento 152,10, ticket médio 76,05, descontos 6,90, 5 itens e taxa de cancelamento de 33,33% |
| `resumoSemPedidosRetornaZeros` | RF01/RF07 | Período sem pedidos não gera divisão por zero |
| `vendasPorDiaPreencheDiasSemVenda` | RF02 | Agrupamento diário com o dia sem venda zerado e cancelado fora |
| `vendasPorMesAgrupaNoMesmoPeriodo` | RF02 | Agrupamento mensal (aceita "mes" minúsculo) |
| `agrupamentoInvalidoEhRejeitado` | RF02/RF05 | Agrupamento inválido gera erro 400 |
| `situacaoRetornaTodosOsStatusNaOrdemDoFluxo` | RF03 | 7 status na ordem do fluxo, com percentuais |
| `pedidosRecentesVemDoMaisNovoParaOMaisAntigo` | RF03 | Ordenação, limite e cliente excluído |
| `rankingDeProdutosIgnoraCancelados` | RF04 | Ranking por quantidade, sem itens de pedidos cancelados |
| `filtroPorStatusRestringeOsIndicadores` | RF05 | Filtro de status aplicado aos indicadores |
| `filtrosInvalidosSaoRejeitados` | RF05 | Data inválida, período invertido, status e limite inválidos |
| `pedidosAntigosSemValoresSaoTratadosComoZero` | RF07 | Pedido antigo sem itens/valores não quebra os cálculos |

O projeto também mantém os 21 testes da atividade anterior (pedidos, clientes, usuários). **Total: 32 testes.**

**Evidência:** *(inserir print do terminal com `Tests run: 32, Failures: 0, Errors: 0` e `BUILD SUCCESS`)*

### 8.2 Testes manuais (roteiro executado na demonstração)

| # | Cenário | Passos | Resultado esperado | Evidência |
|---|---|---|---|---|
| CT01 | Acesso do GERENTE | Login como gerente → menu Dashboard | Dashboard carrega com indicadores e gráficos | *(print)* |
| CT02 | Bloqueio do ATENDENTE (tela) | Login como atendente → acessar `/dashboard` | Página 403 "Acesso negado" | *(print)* |
| CT03 | Bloqueio do ATENDENTE (API) | Como atendente, abrir `/api/dashboard/resumo` | HTTP 403 com JSON de erro | *(print)* |
| CT04 | Filtro de período | Clicar em "7 dias" e depois em "30 dias" | Indicadores e gráfico mudam conforme o período | *(print)* |
| CT05 | Filtro de status | Selecionar "Cancelado" → Aplicar | Faturamento zerado e ranking vazio (cancelados não faturam) | *(print)* |
| CT06 | Agrupamento mensal | Agrupar por "Mês" → Aplicar | Barras por mês | *(print)* |
| CT07 | Troca de métrica | Clicar em "Pedidos" no gráfico de vendas | Eixo passa a mostrar quantidade de pedidos | *(print)* |
| CT08 | Atualização com novo pedido | Registrar um pedido em outra aba → Atualizar | Total de pedidos e pedidos recentes incluem o novo pedido | *(print)* |
| CT09 | Erro de validação na API | Abrir `/api/dashboard/resumo?dataInicio=2026-11-10&dataFim=2026-11-01` | HTTP 400 com a mensagem de erro | *(print)* |

> Salve os prints na pasta `docs/evidencias/` e troque os textos *(print)* pelos links das imagens, por exemplo `![CT01](evidencias/ct01.png)`.

---

## 9. Como executar

```bash
.\mvnw.cmd spring-boot:run
```

Acesse `http://localhost:8080`, entre como **admin / teste123** (ou com um usuário GERENTE) e clique em **Dashboard**.

**Dados de demonstração (opcional):** para ver os gráficos com histórico de 30 dias, suba uma vez com:

```bash
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--pediupatrao.dados-demo=true"
```

Isso cria cerca de 100 pedidos fictícios nos últimos 30 dias (identificados por `registradoPor = "carga-demo"`), além de produtos e clientes caso ainda não existam. A carga roda só uma vez e não altera a auditoria. Sem essa opção, nada é inserido.
