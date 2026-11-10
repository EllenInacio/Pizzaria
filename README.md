# PediuPatrão 

Link do video: https://drive.google.com/drive/folders/1o6DCyqEAi9hpik0sDanfuNb7yb99oen-?usp=drive_link

Sistema de pedidos de pizzaria (Java 21, Spring Boot 3.4, Spring Security, MongoDB e Thymeleaf), evoluído na Atividade Prática M1 — Parte 2 com perfis de acesso, controle de entrada e saída dos pedidos, descontos, cancelamentos e auditoria.

## Como executar

Pré-requisitos: **JDK 21** e acesso ao MongoDB configurado em `src/main/resources/application.properties` (`spring.data.mongodb.uri` / `spring.data.mongodb.database`).

```bash
./mvnw spring-boot:run        # Linux/macOS
mvnw.cmd spring-boot:run      # Windows
```

Acesse `http://localhost:8080` e entre com o administrador criado automaticamente na primeira execução:

| Usuário | Senha    | Perfil |
|---------|----------|--------|
| admin   | teste123 | ADMIN  |

Com o ADMIN, crie os demais usuários em **Configurações → Usuários** (por exemplo `gerente01` com perfil GERENTE e `atendente01` com perfil ATENDENTE).

### Testes automatizados

```bash
./mvnw test
```

Os testes unitários (JUnit 5 + Mockito, sem necessidade de banco) cobrem cálculo do total no servidor, transições válidas e inválidas, registro de saída, desconto (limite de 20%, exclusivo do GERENTE e antes da saída), cancelamento (justificativa, exclusivo do GERENTE e antes da saída), auditoria de clientes com valores anteriores/novos e as proteções de perfis de usuário.

## Requisitos implementados

### RF01 — Usuários e permissões

Perfis: **ADMIN**, **GERENTE** e **ATENDENTE**. A role de cada usuário é atribuída na autenticação conforme o perfil salvo (`UsuarioDetailsService`). As permissões são aplicadas no backend pelo Spring Security (`SecurityConfig`) e, para desconto e cancelamento, também na camada de serviço (`PedidoService`).

| Funcionalidade | ADMIN | GERENTE | ATENDENTE |
|---|:-:|:-:|:-:|
| Administrar usuários e atribuir perfis | ✅ | ❌ | ❌ |
| Consultar pedidos | ✅ | ✅ | ✅ |
| Registrar pedidos e avançar status | ❌ | ✅ | ✅ |
| Aplicar desconto / cancelar pedido | ❌ | ✅ | ❌ |
| Consultar clientes | ❌ | ✅ | ✅ |
| Cadastrar, alterar e excluir clientes | ❌ | ❌ | ✅ |
| Consultar auditoria | ✅ | ✅ | ❌ |
| Gerenciar produtos | ✅ | ✅ | ❌ |

Proteções da administração de perfis:
- `/usuarios/**` e `/api/usuarios/**` são exclusivos do ADMIN (antes `/api/usuarios` era público).
- Somente os perfis ADMIN, GERENTE e ATENDENTE são aceitos.
- O usuário logado não pode alterar o próprio perfil nem excluir a si mesmo.
- O ID enviado na criação é ignorado (impede sobrescrever outro usuário) e a senha (hash) nunca é devolvida pela API.
- CSRF habilitado em todos os formulários e chamadas `fetch` (cabeçalho `X-CSRF-TOKEN`).
- Acesso negado responde **403** (JSON nas APIs) e é registrado em log; APIs sem login respondem **401**.

### RF02 — Cadastro de pedidos

O pedido registra itens (produto, quantidade, preço unitário e subtotal), subtotal, percentual e valor de desconto, valor total, responsável pelo registro e data/hora de entrada. Da requisição são aproveitados **somente o cliente, os produtos e as quantidades**: preços vêm do cadastro de produtos e o total é sempre calculado no servidor.

### RF03 — Fluxo do pedido

```
RECEBIDO → EM_PREPARACAO → PRONTO → SAIU_PARA_ENTREGA ou RETIRADO → FINALIZADO
```

- RECEBIDO registra data/hora de entrada e o usuário responsável.
- SAIU_PARA_ENTREGA / RETIRADO registram obrigatoriamente data/hora e responsável pela saída.
- Transições fora dessa ordem são recusadas (ex.: finalizar um pedido em preparação).
- CANCELADO só ocorre pela operação de cancelamento; depois dele nenhum status pode ser alterado.

### RF04 — Cancelamentos e descontos (exclusivos do GERENTE)

- **Desconto:** percentual, maior que 0% e de no máximo 20%, somente antes da saída (RECEBIDO, EM_PREPARACAO ou PRONTO).
- **Cancelamento:** exige justificativa, somente antes da saída, preserva os dados do pedido e bloqueia alterações posteriores.
- Ambos são registrados na auditoria com usuário, perfil, data/hora e valores anteriores e novos.

### RF05 — Auditoria

Coleção `auditoria` no MongoDB (`Auditoria`, `AuditoriaRepository`, `AuditoriaService`). Cada registro guarda usuário, perfil, data/hora, operação, entidade e ID, campos alterados (valor anterior → novo) e justificativa quando exigida.

Operações auditadas: inclusão de pedido, alteração de status, desconto, cancelamento, inclusão, alteração e exclusão de cliente (campo a campo, incluindo endereço e telefone).

- Consulta em **Auditoria** (menu lateral), exclusiva para ADMIN e GERENTE.
- O repositório expõe apenas gravação e consulta: não há alteração ou exclusão por telas ou APIs.
- Senhas e credenciais nunca são registradas. Na inclusão e exclusão de clientes, apenas o nome é guardado (minimização de dados pessoais).

## Principais APIs

| Método | Rota | Perfis |
|---|---|---|
| GET | `/api/pedidos` | ADMIN, GERENTE, ATENDENTE |
| POST | `/api/pedidos` — `{"clienteId": "...", "itens": [{"produtoId": "...", "quantidade": 2}]}` | GERENTE, ATENDENTE |
| PUT | `/api/pedidos/{id}/status?status=EM_PREPARACAO` | GERENTE, ATENDENTE |
| POST | `/api/pedidos/{id}/desconto` — `{"percentual": "10"}` | GERENTE |
| POST | `/api/pedidos/{id}/cancelar` — `{"justificativa": "..."}` | GERENTE |
| GET | `/api/clientes` | GERENTE, ATENDENTE |
| POST/PUT/DELETE | `/api/clientes` | ATENDENTE |
| GET/POST/PUT/DELETE | `/api/usuarios` | ADMIN |

Erros de regra de negócio retornam JSON `{"erro": "mensagem"}` com status 400, 404 ou 409.

## Demonstrando o bloqueio no backend

Os botões de desconto e cancelamento aparecem só para o GERENTE, mas a regra está no servidor. Logado como **ATENDENTE** (ou ADMIN), abra a tela **Pedidos**, pressione F12 → Console e execute, trocando o ID por um pedido real:

```js
fetch('/api/pedidos/ID_DO_PEDIDO/cancelar', {
  method: 'POST',
  headers: csrfHeaders({'Content-Type': 'application/json'}),
  body: JSON.stringify({justificativa: 'teste'})
}).then(r => r.json().then(j => console.log(r.status, j)));
// → 403 {erro: "Acesso negado: operação não permitida para o seu perfil."}
```

## Principais alterações

- `SecurityConfig`: autorização por perfil em todas as rotas, CSRF habilitado, respostas 401/403 e log de tentativas indevidas.
- `UsuarioDetailsService`, `UsuarioService`, `UsuarioController`, `Usuario`, `DataInitializer`: três perfis, validação de perfil, proteção contra autoelevação e senha oculta na API.
- `Pedido` e novo `ItemPedido`; `PedidoService` e `PedidoController`: itens, cálculo no servidor, fluxo de status, saída, desconto e cancelamento.
- `ClienteService`: auditoria campo a campo e preservação do campo legado `endereco` na edição.
- Novos `Auditoria`, `AuditoriaRepository`, `AuditoriaService`, `ApiExceptionHandler` e tela `auditoria.html`.
- Templates: formulário de pedidos com itens e ações por status, menu lateral por perfil com usuário logado e botão Sair, token CSRF no `layout.html`, correções na tela de produtos (não carregava dentro do layout e o formulário não salvava).
