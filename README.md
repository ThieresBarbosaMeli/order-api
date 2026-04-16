# Order API

API REST para gerenciamento de pedidos, desenvolvida com Java 21 e Spring Boot 3.4.5.

---

## 🚀 Como rodar o projeto localmente

### Pré-requisitos
- Java 21
- Maven
- MySQL rodando na porta 3306

### Passos

1. Clone o repositório:
```bash
git clone https://github.com/ThieresBarbosaMeli/order-api.git
cd order-api
```

2. Configure o banco de dados no arquivo `src/main/resources/application.properties`:
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

## 📦 Endpoints

| Método | Rota                | Descrição                            |
|--------|---------------------|--------------------------------------|
| POST   | /orders             | Cria um novo pedido                  |
| POST   | /orders/{id}/pay    | Paga um pedido (com idempotência)    |
| PATCH  | /orders/{id}/status | Atualiza o status do pedido          |
| GET    | /orders/{id}        | Busca pedido por ID                  |
| GET    | /orders             | Lista pedidos com paginação e filtro |

---

## 📋 Exemplos de uso

### Criar pedido
```http
POST /orders
Content-Type: application/json

{
  "cpf_client": "12345678901",
  "payment": {
    "type": "PIX",
    "price": 99.90
  },
  "id_produto": 1
}
```

### Pagar pedido
```http
POST /orders/1/pay
Idempotency-Key: chave-unica-123
```

### Atualizar status
```http
PATCH /orders/1/status
Content-Type: application/json

{
  "status": "SENT"
}
```

### Listar pedidos com filtro e paginação
```http
GET /orders?status=PAID&page=0&size=10&sortBy=dateBuild&direction=desc
```

---

## 🔄 Fluxo de Status

```
CREATED → PAID → SENT → DELIVERED
```

As transições devem ser sequenciais. Não é permitido pular etapas ou retroceder.

---

## 💳 Tipos de pagamento aceitos

| Tipo    | Descrição     |
|---------|---------------|
| PIX     | Pagamento Pix |
| BOLETO  | Boleto bancário |
| CARTAO  | Cartão de crédito/débito |

---

## 🛡️ Idempotência

O endpoint `POST /orders/{id}/pay` é protegido por idempotência. Envie o header `Idempotency-Key` com uma chave única por operação. Chamadas repetidas com a mesma chave retornam o resultado original sem processar novamente.

---

## 🧰 Tecnologias utilizadas

- Java 21
- Spring Boot 3.4.5
- Spring Data JPA
- Spring Cache (Caffeine)
- Bean Validation
- Lombok
- MySQL
- Maven

---

## ✅ Como validar a aplicação

### Rodar todos os testes
```bash
./mvnw test
```

### Testar manualmente com curl

**Criar pedido:**
```bash
curl -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -d '{"cpf_client":"12345678901","payment":{"type":"PIX","price":100.00},"id_produto":1}'
```

**Pagar pedido:**
```bash
curl -X POST http://localhost:8080/orders/1/pay \
  -H "Idempotency-Key: chave-unica-001"
```

**Atualizar status:**
```bash
curl -X PATCH http://localhost:8080/orders/1/status \
  -H "Content-Type: application/json" \
  -d '{"status":"SENT"}'
```

**Buscar pedido:**
```bash
curl http://localhost:8080/orders/1
```

**Listar pedidos:**
```bash
curl "http://localhost:8080/orders?status=PAID&page=0&size=10&sortBy=dateBuild&direction=desc"
```

---

## 🧠 Decisões técnicas

| Decisão | Justificativa |
|---------|--------------|
| **Caffeine Cache** | Cache local em memória, rápido e simples para o escopo do projeto |
| **@PrePersist / @PreUpdate** | Garante que as datas sejam gerenciadas pela entidade, não pelo serviço |
| **Idempotency-Key no header** | Padrão de mercado para evitar pagamentos duplicados |
| **Bean Validation nos DTOs** | Separa validação do serviço, tornando o código mais limpo |
| **Lombok** | Reduz boilerplate de getters, setters e construtores |
| **MySQL** | Banco relacional já configurado no ambiente de desenvolvimento |
