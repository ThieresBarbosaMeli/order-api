# Tech Debt Report — Order API

---

## 🔍 Problemas encontrados e corrigidos (iteração 1 — legado inicial)

### 1. Versão inexistente do Spring Boot
- **Problema:** `pom.xml` declarava Spring Boot 4.0.5, que não existe.
- **Impacto:** Projeto não compilava.
- **Correção:** Atualizado para 3.4.5 (estável, compatível com Java 21).

### 2. Bug na validação de transição de status
- **Problema:** `isValidNext()` usava `>` em vez de `==`, permitindo pular etapas.
- **Impacto:** Pedido podia ir de CREATED direto para DELIVERED.
- **Correção:** Alterado para `next.ordinal() == this.ordinal() + 1`.

### 3. Rota incorreta `/order` → `/orders`
- **Problema:** Controller usava `/order` (singular) em vez de `/orders`.
- **Impacto:** Inconsistência com a spec e convenções REST.
- **Correção:** Renomeado para `/orders`.

### 4. Boilerplate excessivo nas entidades
- **Problema:** Getters/setters escritos manualmente.
- **Correção:** Adicionado Lombok (`@Getter`, `@Setter`, `@NoArgsConstructor`).

### 5. Atualização manual de `dateUpdated`
- **Problema:** Campo atualizado manualmente no serviço.
- **Correção:** `@PreUpdate` na entidade — gerenciado automaticamente.

### 6. Cache manual verboso
- **Problema:** Cache gerenciado via `CacheManager` manualmente.
- **Correção:** Substituído por `@Cacheable` / `@CacheEvict`.

### 7. Ausência de constraints no banco
- **Problema:** Campos obrigatórios sem `@Column(nullable = false)`.
- **Correção:** Adicionado `nullable = false` onde necessário.

### 8. Validações manuais no serviço
- **Problema:** Validações misturadas com regras de negócio.
- **Correção:** Bean Validation (`@NotBlank`, `@NotNull`, `@Positive`) nos DTOs.

### 9. `GlobalExceptionHandler` incompleto
- **Problema:** Erros de Bean Validation sem handler.
- **Correção:** Adicionado handler para `MethodArgumentNotValidException`.

### 10. Endpoints faltantes
- **Problema:** Faltavam `POST /orders/{id}/pay`, `PATCH /orders/{id}/status`, `GET /orders`.
- **Correção:** Todos os endpoints implementados conforme spec.

### 11. Ausência de testes
- **Problema:** Zero cobertura.
- **Correção:** Testes unitários com Mockito + testes de integração com MockMvc/H2.

---

## 🔍 Problemas encontrados e corrigidos (iteração 2 — evolução para produção)

### 12. Testes unitários mockavam método errado
- **Problema:** `OrderServiceTest` mockava `repository.findById()`, mas `pay()` chama `repository.findByIdWithLock()`. O mock nunca era ativado — testes de `pay()` executavam contra o mock padrão (retornava `Optional.empty()`), falhando com `OrderNotFoundException`.
- **Impacto:** Testes passavam em verde falso ou falhavam silenciosamente.
- **Correção:** Todos os stubs de `pay()` corrigidos para `when(repository.findByIdWithLock(...))`.
- **Arquivos:** `OrderServiceTest.java`

### 13. Testes de integração falhavam com HTTP 401
- **Problema:** `SecurityConfig` protege todos os endpoints com JWT. Os testes de integração não enviavam token — todas as chamadas resultavam em 401.
- **Impacto:** Nenhum cenário de negócio era testado; cobertura real era zero.
- **Correção:** Adicionado `@WithMockUser` no nível da classe. O Spring Security Test pré-popula o `SecurityContext` com um usuário autenticado sem passar pelo `JwtFilter`, isolando o teste de negócio da infraestrutura de autenticação.
- **Arquivos:** `OrderControllerIntegrationTest.java`

### 14. Idempotência cross-order não validava `orderId`
- **Problema:** Em `OrderService.pay()`, se a `Idempotency-Key` já existia para um **pedido diferente**, o código retornava o pedido atual sem lançar exceção. O teste `postOrdersPay_deveRetornar409ComChaveUsadaParaOutroPedido` esperava 409 mas recebia 200.
- **Impacto:** Uma mesma chave de idempotência podia ser reutilizada para pagar pedidos diferentes, violando a garantia de unicidade da operação.
- **Correção:** Verificação adicionada: se `existing.getOrderId() != id` → lança `IdempotencyConflictException` (409). Se for o mesmo order → retorno idempotente (200).
- **Arquivos:** `OrderService.java`

### 15. Problema N+1 em listagens + `LazyInitializationException`
- **Problema:** `Order.items` é `@OneToMany` com `FetchType.LAZY`. Com `spring.jpa.open-in-view=false`, a sessão Hibernate fecha antes da serialização Jackson. Resultado: `LazyInitializationException` em `GET /orders/{id}` e `GET /orders`; e em listagens, N queries extras para carregar items de cada pedido.
- **Impacto:** Endpoints GET podiam retornar 500 em produção; listagens com 100 pedidos gerariam 101 queries ao banco.
- **Correção:** `@EntityGraph(attributePaths = {"items"})` adicionado em `findById`, `findByIdWithLock`, `findAll(Pageable)` e `findByStatus`. Isso força um `LEFT JOIN FETCH` em uma única query.
- **Arquivos:** `OrderRepository.java`

### 16. Recursão circular JSON (`Order → items → OrderItem → order`)
- **Problema:** `OrderItem.order` é um `@ManyToOne` para a entidade pai `Order`. Sem `@JsonIgnore`, Jackson tenta serializar `Order → items → [OrderItem] → order → Order → ...`, resultando em `StackOverflowError` ou resposta JSON corrompida.
- **Impacto:** Qualquer endpoint que retorne um `Order` com items carregados crashava.
- **Correção:** `@JsonIgnore` adicionado em `OrderItem.order`.
- **Arquivos:** `OrderItem.java`

### 17. `updateStatus()` e `create()` sem `@Transactional`
- **Problema:** `updateStatus()` tinha `@CacheEvict` mas sem `@Transactional`. Se `repository.save()` falhasse após o evict, o cache ficaria invalidado mas o banco desatualizado — inconsistência silenciosa. `create()` sem `@Transactional` podia salvar `Order` sem os `OrderItems` em caso de falha parcial.
- **Impacto:** Dados inconsistentes entre cache e banco em cenários de erro.
- **Correção:** `@Transactional` adicionado em `create()` e `updateStatus()`. `@Transactional(readOnly = true)` em `getById()` e `listAll()`.
- **Arquivos:** `OrderService.java`

### 18. `@ValidCPF` definida mas nunca aplicada
- **Problema:** `CPFValidator` e `@ValidCPF` foram implementados na iteração anterior, mas a annotation nunca foi adicionada ao campo `cpf_client` em `OrderRequestDTO`. Qualquer string de 11 dígitos era aceita como CPF válido.
- **Impacto:** CPFs fictícios como `00000000000` ou `11111111111` eram aceitos pela API.
- **Correção:** `@ValidCPF` adicionado ao campo `cpf_client`. CPFs de teste atualizados para usar um CPF válido (`11144477735`).
- **Arquivos:** `OrderRequestDTO.java`, `OrderControllerIntegrationTest.java`

---

## ✅ Itens analisados e descartados intencionalmente

### Publicação de eventos duplicada
- **Análise:** A aplicação não possui mensageria (Kafka, RabbitMQ). Cada operação realiza um único `repository.save()` atômico. Não há segundo destino onde um evento pudesse se perder.
- **Recomendação futura:** Se mensageria for adicionada, implementar o padrão **Outbox** para garantir atomicidade entre banco e broker.

---

## ⏳ O que ficou pendente e por quê

### 1. Validação completa de CPF — ✅ Implementado (iteração 1 + aplicado na iteração 2)

### 2. Autenticação e autorização — ✅ Implementado (iteração 1)

### 3. Paginação com `@EntityGraph` — aviso de performance aceito
- Com `@EntityGraph` em `findAll(Pageable)`, o Hibernate emite aviso `HHH90003004` (JOIN FETCH com paginação na memória para COUNT). Para o volume atual, é aceitável.
- **Recomendação futura:** Para listas com alto volume, separar a query de dados (JOIN FETCH) da query de contagem (`@Query(countQuery = "...")`) usando `@Query` explícito.

### 4. Soft delete / cancelamento de pedido
- Não há endpoint para cancelar pedido. O fluxo atual é linear (CREATED→PAID→SENT→DELIVERED) sem estado de erro ou cancelamento.
- **Recomendação futura:** Adicionar `CANCELLED` como estado terminal a partir de `CREATED` ou `PAID`.

### 5. Expiração de `IdempotencyRecord`
- Registros de idempotência crescem indefinidamente. Não há TTL ou limpeza programada.
- **Recomendação futura:** Adicionar `@Scheduled` para purgar registros com mais de X dias, ou usar Redis com TTL automático.

### 6. Rate limiting
- Endpoints de pagamento não têm proteção contra abuso (muitas requisições em curto período).
- **Recomendação futura:** Adicionar rate limiting por IP ou por usuário com Spring Cloud Gateway ou Bucket4j.
