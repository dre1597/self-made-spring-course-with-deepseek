# Aula 14 — Spring AI

Objetivo: montar um assistente com `ChatClient`, prompt de sistema, tool calling com `@Tool`, RAG com embeddings e vector store, advisors de memória e `QuestionAnswerAdvisor`, e uma ferramenta exposta por outro processo via MCP.

Domínio: atendimento da livraria Página Viva. O assistente responde dúvidas de clientes, consulta status de pedido, busca políticas internas e checa o estoque, que fica num processo separado. Os dois projetos desta aula são próprios e não reaproveitam nada das aulas anteriores.

## Base dos projetos

Esta aula usa dois projetos próprios:

- `support-assistant`: processo HTTP MVC na porta `8080`, no pacote `com.example.supportassistant`. Roda contra o Ollama, guarda embeddings em memória e consome o servidor MCP.
- `inventory-mcp-server`: processo HTTP MVC na porta `8081`, no pacote `com.example.inventorymcpserver`. Expõe uma ferramenta de estoque via MCP sobre Streamable HTTP.

Os dois usam Java 25 e Gradle com Kotlin DSL. Cada um fica no seu próprio diretório.

### Ollama

A aula inteira roda contra um servidor Ollama local em `http://localhost:11434`. Sem chave de API e sem serviço pago.

Instale o Ollama pelo site oficial e garanta que o daemon esteja de pé. Em muitas distribuições ele sobe sozinho na instalação; se não estiver rodando, suba em um terminal:

```bash
ollama serve
```

Em outro terminal, baixe os dois modelos usados:

```bash
ollama pull qwen2.5:7b
ollama pull nomic-embed-text
```

O `qwen2.5:7b` atende o chat e o tool calling. O Ollama precisa ser 0.2.8 ou mais novo para ferramentas. O `nomic-embed-text` gera os embeddings do RAG.

Confirme que os modelos aparecem na lista:

```bash
curl http://localhost:11434/api/tags
```

O retorno é um JSON com os modelos instalados, incluindo `qwen2.5:7b` e `nomic-embed-text`.

### Projeto do assistente

No IntelliJ, crie um projeto **Gradle** com **Kotlin** como DSL da build e nome `support-assistant`. O projeto-base já traz `repositories { mavenCentral() }`.

No `build.gradle.kts`, acrescente o BOM do Spring AI e as dependências:

```kotlin
dependencies {
    implementation(platform("org.springframework.ai:spring-ai-bom:2.0.1"))

    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.ai:spring-ai-starter-model-ollama")
    implementation("org.springframework.ai:spring-ai-vector-store")
    implementation("org.springframework.ai:spring-ai-vector-store-advisor")
    implementation("org.springframework.ai:spring-ai-starter-mcp-client")
}
```

O BOM `spring-ai-bom:2.0.1` fixa a versão dos artefatos do Spring AI. Essa linha é o que casa o Spring AI 2.0.1 com o Spring Boot 4.1. As versões dos starters não aparecem no build; o BOM resolve cada uma.

O `spring-ai-starter-model-ollama` cria o `OllamaChatModel`, o `OllamaEmbeddingModel`, um `ChatMemory` em memória e o `ChatClient.Builder`, todos ligados pelas propriedades `spring.ai.ollama.*`. O `spring-ai-vector-store` traz o `SimpleVectorStore`, o `Document` e o `SearchRequest`. O `spring-ai-vector-store-advisor` traz o `QuestionAnswerAdvisor`. O `spring-ai-starter-mcp-client` conecta nos servidores MCP.

### Projeto do servidor MCP

No IntelliJ, crie outro projeto **Gradle** com **Kotlin** e nome `inventory-mcp-server`. No `build.gradle.kts`, acrescente:

```kotlin
dependencies {
    implementation(platform("org.springframework.ai:spring-ai-bom:2.0.1"))

    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.ai:spring-ai-starter-mcp-server-webmvc")
}
```

O `spring-ai-starter-mcp-server-webmvc` sobe o servidor MCP sobre Streamable HTTP e registra os métodos anotados com `@McpTool`. A anotação vem junto com o starter, sem dependência extra.

### Ordem de execução

Três processos precisam estar de pé, nesta ordem:

1. O daemon do Ollama, escutando em `http://localhost:11434`.
2. O `inventory-mcp-server`, diretório `inventory-mcp-server`, com `./gradlew bootRun`. Sobe na porta `8081`.
3. O `support-assistant`, diretório `support-assistant`, com `./gradlew bootRun`. Sobe na porta `8080`.

O assistente conecta no servidor MCP durante a subida. Se o MCP ainda não estiver de pé, o assistente não sobe.

## Do modelo ao ChatClient

Um LLM de chat não guarda estado. Cada chamada recebe a lista de mensagens e devolve uma mensagem nova. O Spring AI separa isso em duas camadas: o `ChatModel` é a porta de baixo nível, com `call` e `stream`; o `ChatClient` é a fachada fluente que monta o prompt, encadeia advisors e registra ferramentas.

O prompt de sistema é a mensagem que define o comportamento do modelo: idioma, tom e limites. Ela acompanha toda chamada, antes da mensagem do usuário.

O starter do Ollama lê a configuração e monta o `ChatClient.Builder`. Você injeta o builder e constrói o cliente.

Mantenha este arquivo completo em `support-assistant/src/main/resources/application.yaml`:

```yaml
# support-assistant/src/main/resources/application.yaml
server:
  port: 8080
spring:
  application:
    name: support-assistant
  ai:
    ollama:
      base-url: http://localhost:11434
      chat:
        model: qwen2.5:7b
        temperature: 0.3
```

O `base-url` aponta para o Ollama local. O `chat.model` escolhe o modelo. A `temperature` baixa deixa a resposta mais estável, o que ajuda nas chamadas de ferramenta. Repare que o nome do modelo fica direto em `chat.model`; o formato antigo com `chat.options.model` está deprecado no Spring AI 2.0.

A classe principal só inicia o contexto:

```java
package com.example.supportassistant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SupportAssistantApplication {

    public static void main(String[] args) {
        SpringApplication.run(SupportAssistantApplication.class, args);
    }
}
```

O `ChatClient` nasce numa configuração própria. O `defaultSystem` vale para toda requisição feita a partir desse cliente:

```java
package com.example.supportassistant.assistant;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AssistantConfiguration {

    @Bean
    ChatClient supportChatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem("""
                        Você é o assistente da livraria Página Viva.
                        Responda em português, de forma curta e direta.
                        Use as ferramentas disponíveis quando precisar de dados internos.
                        Quando houver contexto recuperado, baseie a resposta nele.
                        Se não souber a resposta, diga que não sabe.
                        """)
                .build();
    }
}
```

O serviço faz a chamada. `.prompt()` abre a requisição, `.user(...)` define a mensagem do usuário, `.call()` executa e `.content()` extrai o texto da resposta:

```java
package com.example.supportassistant.assistant;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class AssistantService {

    private final ChatClient chatClient;

    public AssistantService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public String chat(String message) {
        return chatClient.prompt()
                .user(message)
                .call()
                .content();
    }
}
```

O controlador expõe a rota. Os records de request e resposta ficam aninhados porque só são usados aqui:

```java
package com.example.supportassistant.assistant;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    private final AssistantService assistantService;

    public AssistantController(AssistantService assistantService) {
        this.assistantService = assistantService;
    }

    @PostMapping("/chat")
    public AssistantAnswer chat(@RequestBody ChatRequest request) {
        return new AssistantAnswer(assistantService.chat(request.message()));
    }

    public record ChatRequest(String message) {
    }

    public record AssistantAnswer(String answer) {
    }
}
```

O endpoint devolve JSON com o campo `answer`. O `conversationId` entra na seção de advisors, quando o chat passar a manter histórico.

## Tool calling com @Tool

O modelo decide quando chamar uma função, mas não executa nada. Você declara as ferramentas em Java, o Spring AI descreve cada uma para o modelo, roda a que ele pedir e devolve o resultado. O laço continua até o modelo responder sem pedir ferramenta. Quem conduz esse laço é o `ToolCallingAdvisor`, que o `ChatClient` registra sozinho.

O `@Tool` marca o método. A `description` é o texto que o modelo lê para decidir quando usar a ferramenta; sem ela, ele não sabe para que serve. O `@ToolParam` descreve cada argumento. O retorno precisa ser conversível para texto, então use tipos simples ou records.

A ferramenta de pedidos guarda um mapa fixo de status. Em um sistema real, ela chamaria o repositório:

```java
package com.example.supportassistant.orders;

import java.util.Map;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class OrderTools {

    private static final Map<String, String> STATUS_BY_ORDER_ID = Map.of(
            "PV-1001", "EM_TRANSITO",
            "PV-1002", "EM_SEPARACAO",
            "PV-1003", "ENTREGUE");

    @Tool(description = "Consulta o status de um pedido da livraria pelo identificador")
    public String orderStatus(
            @ToolParam(description = "Identificador do pedido, no formato PV-1000", required = true) String orderId) {
        String status = STATUS_BY_ORDER_ID.get(orderId);
        if (status == null) {
            return "Pedido não encontrado: " + orderId;
        }
        return "O pedido " + orderId + " está com status " + status + ".";
    }
}
```

O serviço registra a ferramenta só nesta chamada, via `.tools(...)`. O objeto passado pode ser um POJO com métodos `@Tool`; o Spring AI monta os callbacks por reflexão:

```java
package com.example.supportassistant.orders;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class OrderAssistantService {

    private final ChatClient chatClient;

    private final OrderTools orderTools;

    public OrderAssistantService(ChatClient chatClient, OrderTools orderTools) {
        this.chatClient = chatClient;
        this.orderTools = orderTools;
    }

    public String askOrders(String message) {
        return chatClient.prompt()
                .user(message)
                .tools(orderTools)
                .call()
                .content();
    }
}
```

O controlador expõe a rota de pedidos:

```java
package com.example.supportassistant.orders;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assistant")
public class OrderController {

    private final OrderAssistantService orderAssistantService;

    public OrderController(OrderAssistantService orderAssistantService) {
        this.orderAssistantService = orderAssistantService;
    }

    @PostMapping("/orders")
    public OrderAnswer orders(@RequestBody OrderRequest request) {
        return new OrderAnswer(orderAssistantService.askOrders(request.message()));
    }

    public record OrderRequest(String message) {
    }

    public record OrderAnswer(String answer) {
    }
}
```

Quando o modelo pede `orderStatus("PV-1001")`, o Spring AI executa o método e devolve o texto ao modelo, que então formula a resposta final. O modelo nunca toca no mapa; ele só vê a descrição da ferramenta e o resultado.

## RAG com embeddings e vector store

O modelo não conhece as políticas internas da loja. Colar todos os documentos no prompt não escala. RAG resolve isso em três passos: guardar os documentos como embeddings num vector store, buscar os trechos parecidos com a pergunta e enviar só esses trechos no prompt.

Embedding é um vetor que posiciona o texto num espaço onde textos parecidos ficam próximos. Quem gera os vetores é o `EmbeddingModel`. O `SimpleVectorStore` guarda os vetores em memória e compara por similaridade de cosseno. Ele serve para estudo e demonstração; em produção você usa um banco vetorial.

O modelo de embedding entra na configuração. Mantenha este arquivo completo:

```yaml
# support-assistant/src/main/resources/application.yaml
server:
  port: 8080
spring:
  application:
    name: support-assistant
  ai:
    ollama:
      base-url: http://localhost:11434
      chat:
        model: qwen2.5:7b
        temperature: 0.3
      embedding:
        model: nomic-embed-text
```

O vector store é criado no start da aplicação. Cada `Document` recebe o texto e um metadado `source` para identificar a origem. O `add` chama o `EmbeddingModel` para cada documento, então o Ollama precisa estar de pé nessa hora:

```java
package com.example.supportassistant.knowledge;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KnowledgeConfiguration {

    @Bean
    SimpleVectorStore supportVectorStore(EmbeddingModel embeddingModel) {
        SimpleVectorStore vectorStore = SimpleVectorStore.builder(embeddingModel).build();
        vectorStore.add(List.of(
                Document.builder()
                        .text("O frete é grátis para compras acima de R$ 150. Abaixo disso, custa R$ 12 para todo o Brasil.")
                        .metadata("source", "politica-de-frete")
                        .build(),
                Document.builder()
                        .text("O prazo de entrega é de 3 a 7 dias úteis, dependendo da região.")
                        .metadata("source", "prazo-de-entrega")
                        .build(),
                Document.builder()
                        .text("O cliente pode devolver um livro em até 30 dias após o recebimento, sem custo, desde que não haja sinais de uso.")
                        .metadata("source", "politica-de-devolucao")
                        .build(),
                Document.builder()
                        .text("A Página Viva fica na Rua das Letras, 42, e abre de segunda a sábado, das 9h às 18h.")
                        .metadata("source", "loja-fisica")
                        .build()));
        return vectorStore;
    }
}
```

O resultado da busca vira um record próprio. O `score` é a similaridade entre a pergunta e o documento:

```java
package com.example.supportassistant.knowledge;

public record KnowledgeMatch(String text, String source, Double score) {
}
```

O serviço de busca monta um `SearchRequest` com a pergunta e o `topK`, e devolve os trechos mais parecidos. Nenhuma chamada de chat acontece aqui:

```java
package com.example.supportassistant.knowledge;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeService {

    private final VectorStore supportVectorStore;

    public KnowledgeService(VectorStore supportVectorStore) {
        this.supportVectorStore = supportVectorStore;
    }

    public List<KnowledgeMatch> search(String question) {
        SearchRequest searchRequest = SearchRequest.builder()
                .query(question)
                .topK(3)
                .build();
        return supportVectorStore.similaritySearch(searchRequest).stream()
                .map(KnowledgeService::toMatch)
                .toList();
    }

    private static KnowledgeMatch toMatch(Document document) {
        return new KnowledgeMatch(
                document.getText(),
                String.valueOf(document.getMetadata().get("source")),
                document.getScore());
    }
}
```

A rota de busca expõe o resultado cru, útil para enxergar o RAG funcionando antes de colocar o modelo no meio:

```java
package com.example.supportassistant.knowledge;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assistant")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @GetMapping("/search")
    public List<KnowledgeMatch> search(@RequestParam("question") String question) {
        return knowledgeService.search(question);
    }
}
```

Cada mudança no vector store fica em memória e some quando o processo cai. Ao subir de novo, a configuração recria e reindexa os documentos.

## Advisors

Advisor é um ponto de extensão em volta da chamada do modelo. Antes e depois de cada chamada, um advisor lê e altera o prompt ou a resposta. O `ChatClient` executa uma cadeia de advisors em ordem. Esta aula usa dois.

O `MessageChatMemoryAdvisor` guarda o histórico por `conversationId` e reenvia as mensagens anteriores junto da nova. A memória fica no `ChatMemory`, que aqui é em memória de processo. O `conversationId` é obrigatório em toda chamada com esse advisor; sem ele, o advisor lança exceção na hora.

O `QuestionAnswerAdvisor` roda o RAG inteiro: pega a pergunta, busca no vector store, injeta os trechos no prompt e chama o modelo. É o mesmo `similaritySearch` da seção anterior, agora dentro da cadeia.

Os dois advisors são criados uma vez e reusados. O serviço passa a receber o `conversationId` e ganha a rota de FAQ:

```java
package com.example.supportassistant.assistant;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

@Service
public class AssistantService {

    private final ChatClient chatClient;

    private final MessageChatMemoryAdvisor chatMemoryAdvisor;

    private final QuestionAnswerAdvisor questionAnswerAdvisor;

    public AssistantService(ChatClient chatClient, ChatMemory chatMemory, VectorStore supportVectorStore) {
        this.chatClient = chatClient;
        this.chatMemoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
        this.questionAnswerAdvisor = QuestionAnswerAdvisor.builder(supportVectorStore).build();
    }

    public String chat(String conversationId, String message) {
        return chatClient.prompt()
                .user(message)
                .advisors(chatMemoryAdvisor)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();
    }

    public String askFaq(String question) {
        return chatClient.prompt()
                .user(question)
                .advisors(questionAnswerAdvisor)
                .call()
                .content();
    }
}
```

O controlador muda: o request do chat ganha o `conversationId` e a rota de FAQ entra:

```java
package com.example.supportassistant.assistant;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    private final AssistantService assistantService;

    public AssistantController(AssistantService assistantService) {
        this.assistantService = assistantService;
    }

    @PostMapping("/chat")
    public AssistantAnswer chat(@RequestBody ChatRequest request) {
        return new AssistantAnswer(assistantService.chat(request.conversationId(), request.message()));
    }

    @PostMapping("/faq")
    public AssistantAnswer faq(@RequestBody FaqRequest request) {
        return new AssistantAnswer(assistantService.askFaq(request.question()));
    }

    public record ChatRequest(String conversationId, String message) {
    }

    public record FaqRequest(String question) {
    }

    public record AssistantAnswer(String answer) {
    }
}
```

O `chat` agora continua a conversa quando o mesmo `conversationId` reaparece. O `faq` responde com base nos documentos recuperados. A rota de pedidos usa o mesmo `ChatClient` e passa a ferramenta que precisa, sem advisor de memória.

## MCP

MCP (Model Context Protocol) padroniza como uma aplicação de IA descobre e chama ferramentas, recursos e prompts expostos por outro processo. A separação importa: o estoque vira um servidor MCP independente, com processo e porta próprios. O assistente descobre as ferramentas desse servidor em tempo de execução, e o modelo as enxerga como se fossem locais.

O Spring AI cobre os dois lados. No servidor, o `@McpTool` marca os métodos e o starter registra tudo. No cliente, o starter conecta por Streamable HTTP e entrega um `SyncMcpToolCallbackProvider`, que converte as ferramentas remotas em `ToolCallback` do Spring AI.

Mantenha esta configuração completa em `inventory-mcp-server/src/main/resources/application.yaml`:

```yaml
# inventory-mcp-server/src/main/resources/application.yaml
server:
  port: 8081
spring:
  application:
    name: inventory-mcp-server
  ai:
    mcp:
      server:
        protocol: STREAMABLE
        name: inventory-mcp-server
        version: 1.0.0
        type: SYNC
```

O `protocol: STREAMABLE` habilita o servidor sobre Streamable HTTP. O endpoint padrão é `/mcp`, então a ferramenta fica exposta em `http://localhost:8081/mcp`.

A classe principal do servidor:

```java
package com.example.inventorymcpserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class InventoryMcpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryMcpServerApplication.class, args);
    }
}
```

A ferramenta usa `@McpTool` e `@McpToolParam`, com a descrição que o modelo lê:

```java
package com.example.inventorymcpserver.inventory;

import java.util.Map;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

@Service
public class InventoryTools {

    private static final Map<String, Integer> STOCK_BY_TITLE = Map.of(
            "O Cortiço", 4,
            "Dom Casmurro", 0,
            "Grande Sertão: Veredas", 7);

    @McpTool(description = "Consulta a quantidade em estoque de um livro pelo título")
    public String stockByTitle(
            @McpToolParam(description = "Título exato do livro", required = true) String title) {
        Integer quantity = STOCK_BY_TITLE.get(title);
        if (quantity == null) {
            return "Livro não encontrado no estoque: " + title;
        }
        return "Estoque de \"" + title + "\": " + quantity + " unidades.";
    }
}
```

No lado do assistente, a configuração ganha o cliente MCP. Mantenha este arquivo completo:

```yaml
# support-assistant/src/main/resources/application.yaml
server:
  port: 8080
spring:
  application:
    name: support-assistant
  ai:
    ollama:
      base-url: http://localhost:11434
      chat:
        model: qwen2.5:7b
        temperature: 0.3
      embedding:
        model: nomic-embed-text
    mcp:
      client:
        enabled: true
        name: support-assistant
        version: 1.0.0
        type: SYNC
        initialized: true
        request-timeout: 30s
        streamable-http:
          connections:
            inventory:
              url: http://localhost:8081
              endpoint: /mcp
```

O bloco `streamable-http.connections.inventory` aponta para o servidor na porta `8081`. O cliente conecta na subida e descobre as ferramentas disponíveis.

O serviço de estoque injeta o provider e passa as ferramentas remotas para a chamada:

```java
package com.example.supportassistant.mcp;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.stereotype.Service;

@Service
public class StockAssistantService {

    private final ChatClient chatClient;

    private final SyncMcpToolCallbackProvider mcpToolCallbacks;

    public StockAssistantService(ChatClient chatClient, SyncMcpToolCallbackProvider mcpToolCallbacks) {
        this.chatClient = chatClient;
        this.mcpToolCallbacks = mcpToolCallbacks;
    }

    public String askStock(String message) {
        return chatClient.prompt()
                .user(message)
                .tools(mcpToolCallbacks)
                .call()
                .content();
    }
}
```

O controlador expõe a rota de estoque:

```java
package com.example.supportassistant.mcp;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assistant")
public class StockController {

    private final StockAssistantService stockAssistantService;

    public StockController(StockAssistantService stockAssistantService) {
        this.stockAssistantService = stockAssistantService;
    }

    @PostMapping("/stock")
    public StockAnswer stock(@RequestBody StockRequest request) {
        return new StockAnswer(stockAssistantService.askStock(request.message()));
    }

    public record StockRequest(String message) {
    }

    public record StockAnswer(String answer) {
    }
}
```

O assistente não conhece o código do `InventoryTools`. Ele recebe a descrição da ferramenta pelo protocolo, chama e usa o resultado. Trocar o servidor MCP por outro não exige mudar o assistente.

## Testando

Com os três processos de pé (Ollama, `inventory-mcp-server` em `8081` e `support-assistant` em `8080`), use o arquivo `requests.http` do assistente. As respostas do modelo variam de redação; o que importa é o conteúdo e a ferramenta acionada.

```http
# support-assistant/src/main/resources/requests.http

### Chat com prompt de sistema
POST http://localhost:8080/api/assistant/chat
Content-Type: application/json

{
  "conversationId": "cliente-ana",
  "message": "Meu nome é Ana. Recomende um livro de Machado de Assis."
}

### Mesma conversa, pergunta que depende do histórico
POST http://localhost:8080/api/assistant/chat
Content-Type: application/json

{
  "conversationId": "cliente-ana",
  "message": "Qual é o meu nome?"
}

### Status de pedido (tool calling local)
POST http://localhost:8080/api/assistant/orders
Content-Type: application/json

{
  "message": "Qual o status do pedido PV-1001?"
}

### Busca no vector store (RAG, sem modelo)
GET http://localhost:8080/api/assistant/search?question=Qual é o prazo de entrega?

### Pergunta sobre política (QuestionAnswerAdvisor)
POST http://localhost:8080/api/assistant/faq
Content-Type: application/json

{
  "question": "Posso devolver um livro 20 dias depois de receber?"
}

### Estoque via MCP
POST http://localhost:8080/api/assistant/stock
Content-Type: application/json

{
  "message": "Quantos exemplares de Dom Casmurro a loja tem em estoque?"
}
```

O primeiro request devolve uma recomendação em português. O segundo devolve o nome informado antes, `Ana`, porque o `MessageChatMemoryAdvisor` reenviou o histórico da mesma `conversationId`.

O request de pedidos faz o modelo chamar `orderStatus("PV-1001")`. A resposta cita o status `EM_TRANSITO`. Se o identificador não existir, a ferramenta devolve a mensagem de pedido não encontrado.

O request de busca devolve uma lista JSON com até três documentos. A pergunta sobre prazo deve trazer o documento de `source` igual a `prazo-de-entrega` no topo, com o maior `score`.

O request de FAQ passa pelo `QuestionAnswerAdvisor`. A resposta fala em 30 dias, o prazo do documento de devolução. Se o Ollama estiver fora do ar, todos os requests de chat e de FAQ falham; o de busca também falha, porque o embedding da pergunta é calculado na hora.

O request de estoque faz o modelo chamar a ferramenta remota `stockByTitle`. A resposta cita `Dom Casmurro` com `0` unidades. Se o `inventory-mcp-server` estiver fora do ar, o assistente não sobe, então o erro aparece na inicialização, não no request.

## Fechando

O modelo é uma peça trocável. O `ChatClient` e os advisors não sabem que o provedor é Ollama; trocar o starter do modelo muda a configuração, não o código de negócio.

Alguns pontos para produção. A memória em processo some no restart; use uma implementação persistente de `ChatMemory`, como a baseada em JDBC, quando a conversa precisar sobreviver. O `SimpleVectorStore` fica só em memória; em produção troque por um banco vetorial e carregue os documentos por um pipeline de ingestão, não no start. O MCP aceita autenticação e filtro de ferramentas; em rede aberta, não exponha um servidor sem controle. Por fim, resposta de modelo não é determinística: teste o fluxo, não o texto exato.

## Estrutura

```
inventory-mcp-server/
└── src/main/
    ├── java/com/example/inventorymcpserver/
    │   ├── InventoryMcpServerApplication.java
    │   └── inventory/InventoryTools.java
    └── resources/application.yaml
support-assistant/
└── src/main/
    ├── java/com/example/supportassistant/
    │   ├── SupportAssistantApplication.java
    │   ├── assistant/
    │   │   ├── AssistantConfiguration.java
    │   │   ├── AssistantService.java
    │   │   └── AssistantController.java
    │   ├── orders/
    │   │   ├── OrderTools.java
    │   │   ├── OrderAssistantService.java
    │   │   └── OrderController.java
    │   ├── knowledge/
    │   │   ├── KnowledgeConfiguration.java
    │   │   ├── KnowledgeService.java
    │   │   ├── KnowledgeMatch.java
    │   │   └── KnowledgeController.java
    │   └── mcp/
    │       ├── StockAssistantService.java
    │       └── StockController.java
    └── resources/
        ├── application.yaml
        └── requests.http
```
