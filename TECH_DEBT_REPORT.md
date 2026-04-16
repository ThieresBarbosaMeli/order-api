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

## ⏳ O que ficou pendente e por quê

### 1. Validação completa do CPF
- **Motivo:** A validação atual verifica apenas o tamanho (11 caracteres).
- **Impacto:** CPFs inválidos como 00000000000 são aceitos.
- **Recomendação:** Implementar validação com dígitos verificadores.

### 2. Autenticação e autorização
- **Motivo:** Fora do escopo do projeto.
- **Impacto:** Qualquer pessoa pode acessar e modificar qualquer pedido.
- **Recomendação:** Implementar Spring Security com JWT.
