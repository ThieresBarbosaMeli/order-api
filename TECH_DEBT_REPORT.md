# Tech Debt Report — Order API

## 🔍 Problemas encontrados e corrigidos

### 1. Versão inexistente do Spring Boot
- **Problema:** pom.xml declarava a versão 4.0.5 do Spring Boot, que não existe.
- **Impacto:** O projeto não compilava nem subia.
- **Correção:** Atualizado para a versão 3.4.5, estável e compatível com Java 21.

### 2. Bug na validação de transição de status
- **Problema:** O método isValidNext() usava > em vez de ==, permitindo pular etapas.
- **Impacto:** Um pedido podia ir de CREATED direto para DELIVERED, violando a regra de negócio.
- **Correção:** Alterado para next.ordinal() == this.ordinal() + 1.

### 3. Rota incorreta
- **Problema:** O controller usava /order em vez de /orders.
- **Impacto:** Inconsistência com a especificação e com as convenções REST.
- **Correção:** Renomeado para /orders.

### 4. Boilerplate excessivo na entidade Order
- **Problema:** Getters e setters escritos manualmente.
- **Impacto:** Código difícil de manter.
- **Correção:** Adicionado Lombok (@Getter, @Setter, @NoArgsConstructor).

### 5. Atualização manual de dateUpdated
- **Problema:** Campo dateUpdated era atualizado manualmente no serviço.
- **Impacto:** Risco de esquecer a atualização em novos métodos.
- **Correção:** Adicionado @PreUpdate na entidade.

### 6. Cache manual verboso
- **Problema:** Cache gerenciado manualmente via CacheManager.
- **Impacto:** Código verboso e propenso a erros.
- **Correção:** Substituído por @Cacheable e @CacheEvict.

### 7. Ausência de constraints no banco
- **Problema:** Campos obrigatórios sem @Column(nullable = false).
- **Impacto:** Banco aceitaria registros inválidos.
- **Correção:** Adicionado nullable = false nos campos obrigatórios.

### 8. Validações manuais no serviço
- **Problema:** Validações de entrada feitas manualmente no OrderService.
- **Impacto:** Código misturado com regras de negócio.
- **Correção:** Substituído por Bean Validation nos DTOs.

### 9. GlobalExceptionHandler incompleto
- **Problema:** Erros de Bean Validation não eram tratados.
- **Impacto:** Respostas de erro genéricas e sem informação útil.
- **Correção:** Adicionado handler para MethodArgumentNotValidException.

### 10. Endpoints faltantes
- **Problema:** Faltavam POST /orders/{id}/pay, PATCH /orders/{id}/status e GET /orders.
- **Impacto:** API incompleta em relação à especificação.
- **Correção:** Todos os endpoints foram implementados.

---

### 11. Ausência de testes de integração
- **Problema:** Projeto entregue sem testes de integração.
- **Impacto:** Cobertura de testes limitada.
- **Correção:** Implementados testes de integração com MockMvc e banco H2 em memória.

---

### Observação: Inconsistência no documento original
- O documento original especifica HTTP 204 para o POST /orders, porém também
  exibe um body na resposta. Como 204 não permite body, foi mantido o 201 Created,
  que é o padrão REST para criação de recursos com retorno de dados.

---

## ✅ Itens analisados e descartados intencionalmente

### 1. Publicação de eventos duplicada
- **Análise:** O problema ocorre quando uma operação salva no banco e publica um evento para outro sistema sem garantia de atomicidade — se um falhar, os sistemas ficam inconsistentes.
- **Por que não se aplica:** A aplicação não possui mensageria (Kafka, RabbitMQ) nem qualquer mecanismo de publicação de eventos. Cada operação realiza um único `repository.save()`, que já é atômico por natureza. Não há segundo destino onde um evento pudesse se perder.
- **Recomendação futura:** Caso mensageria seja introduzida, implementar o padrão Outbox para garantir atomicidade.

### 2. Problema N+1 em listagens
- **Análise:** O N+1 ocorre quando uma listagem gera uma query para cada item da lista ao buscar dados de uma relação lazy.
- **Por que não se aplica:** `Payment` é mapeado com `@Embedded`, ou seja, seus campos (`type` e `price`) ficam na mesma tabela que `Order`. Uma única query já retorna todos os dados — não há relação lazy nem tabela separada que pudesse gerar consultas extras.
- **Recomendação futura:** Se `Payment` ou outro relacionamento for extraído para tabela própria, revisar com `@EntityGraph` ou `JOIN FETCH`.

---

## ⏳ O que ficou pendente e por quê

### 1. Validação completa do CPF — ✅ Implementado
- **Problema:** A validação anterior verificava apenas o tamanho (11 caracteres), aceitando CPFs como `00000000000`.
- **Correção:** Criado `@ValidCPF` com validador customizado (`CPFValidator`) que aplica o algoritmo oficial dos dígitos verificadores da Receita Federal.
- **Arquivos:** `validation/ValidCPF.java`, `validation/CPFValidator.java`, `dto/OrderRequestDTO.java`

### 2. Autenticação e autorização — ✅ Implementado
- **Problema:** Qualquer pessoa podia acessar e modificar qualquer pedido sem autenticação.
- **Correção:** Adicionado Spring Security com JWT. O endpoint `POST /auth/login` recebe usuário e senha e retorna um token. Todos os demais endpoints exigem o token no header `Authorization: Bearer <token>`.
- **Arquivos:** `security/JwtUtil.java`, `security/JwtFilter.java`, `security/SecurityConfig.java`, `controller/AuthController.java`
- **Credenciais padrão:** `admin` / `admin123` (configurável via `application.properties`)
