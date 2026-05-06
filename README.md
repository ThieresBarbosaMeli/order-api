# Order API

API REST para gerenciamento de pedidos, desenvolvida com Java 21 e Spring Boot 3.4.5.

---

## 🚀 Como rodar o projeto localmente

### Pré-requisitos
- Java 21+
- Maven
- MySQL rodando na porta 3306

### Passos

1. Clone o repositório:
```bash
git clone https://github.com/ThieresBarbosaMeli/order-api.git
cd order-api
```

2. Configure o banco de dados no `src/main/resources/application.properties`:
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/orderdb?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=UTC
spring.datasource.username=root
spring.datasource.password=
```

3. Execute a aplicação:
```bash
./mvnw spring-boot:run
```

A API estará disponível em `http://localhost:8080`.

---

## 🔐 Autenticação

Todos os endpoints (exceto `POST /auth/login`) exigem um JWT no header `Authorization`.

### Obter token

```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

Resposta:
```json
{ "token": "<jwt>" }
```

Use o token em todas as chamadas:
```
Authorization: Bearer <jwt>
```

> Credenciais configuráveis em `application.properties` via `auth.admin.username` e `auth.admin.password`.

---

## 📦 Endpoints

| Método | Rota                    | Descrição                            |
|--------|-------------------------|--------------------------------------|
| POST   | /auth/login             | Autentica e retorna JWT              |
| POST   | /orders                 | Cria um novo pedido                  |
| POST   | /orders/{id}/pay        | Paga um pedido (com idempotência)    |
| PATCH  | /orders/{id}/status     | Atualiza o status do pedido          |
| GET    | /orders/{id}            | Busca pedido por ID (com cache)      |
| GET    | /orders                 | Lista pedidos com paginação e filtro |

---

## 📋 Exemplos de uso

### Criar pedido
```bash
curl -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{
    "cpf_client": "11144477735",
    "payment": { "type": "PIX", "price": 99.90 },
    "items": [
      { "idProduct": 1, "quantity": 2, "price": 49.95 }
    ]
  }'
```

### Pagar pedido
```bash
curl -X POST http://localhost:8080/orders/1/pay \
  -H "Authorization: Bearer <token>" \
  -H "Idempotency-Key: chave-unica-abc123"
```

### Atualizar status
```bash
curl -X PATCH http://localhost:8080/orders/1/status \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{"status":"SENT"}'
```

### Buscar pedido
```bash
curl http://localhost:8080/orders/1 \
  -H "Authorization: Bearer <token>"
```

### Listar pedidos com filtro e paginação
```bash
curl "http://localhost:8080/orders?status=PAID&page=0&size=10&sortBy=id&direction=desc" \
  -H "Authorization: Bearer <token>"
```

---

## 🔄 Fluxo de Status

```
CREATED → PAID → SENT → DELIVERED
```

Transições devem ser sequenciais. Pular etapas ou retroceder retorna **409 Conflict**.

---

## 💳 Tipos de pagamento aceitos

| Tipo    | Descrição               |
|---------|-------------------------|
| PIX     | Pagamento Pix           |
| BOLETO  | Boleto bancário         |
| CARTAO  | Cartão de crédito/débito|

---

## 🛡️ Idempotência

O endpoint `POST /orders/{id}/pay` é protegido por idempotência via header `Idempotency-Key`.

- **Mesma chave + mesmo pedido**: retorna o resultado original sem reprocessar (200).
- **Mesma chave + pedido diferente**: retorna **409 Conflict** — a chave pertence a outro pedido.

---

## ✅ Como rodar os testes

```bash
./mvnw test
```

Os testes de integração usam banco H2 em memória (perfil `test`) e `@WithMockUser` para contornar a camada JWT sem comprometer a segurança em produção.

---

## 🧠 Decisões técnicas

| Decisão | Justificativa |
|---------|--------------|
| **@EntityGraph nas queries** | Carrega `items` em JOIN único, eliminando o problema N+1 com `open-in-view=false` |
| **@Transactional em todos os métodos de escrita** | Garante atomicidade entre cache evict e commit no banco |
| **@ValidCPF aplicado no DTO** | Valida dígitos verificadores do CPF via algoritmo da Receita Federal |
| **Idempotency-Key com validação de orderId** | Impede reutilização da mesma chave para pedidos diferentes (409) |
| **@JsonIgnore em OrderItem.order** | Evita recursão circular ao serializar Order → items → OrderItem → order |
| **@WithMockUser nos testes de integração** | Isola teste de negócio da infraestrutura de JWT sem desabilitar segurança |
| **Caffeine Cache** | Cache local em memória, rápido e simples para o escopo do projeto |
| **@PrePersist / @PreUpdate** | Datas gerenciadas pela entidade — sem risco de esquecer nos serviços |
| **Lock pessimista no pagamento** | Previne pagamento duplo em requisições concorrentes |
| **MySQL (prod) / H2 (test)** | Banco relacional robusto em prod; H2 em memória para testes rápidos e isolados |

---

## 🧰 Tecnologias utilizadas

- Java 21
- Spring Boot 3.4.5
- Spring Data JPA + Hibernate
- Spring Cache (Caffeine)
- Spring Security + JWT (jjwt 0.12.6)
- Bean Validation (Jakarta)
- Lombok
- MySQL / H2
- Maven
