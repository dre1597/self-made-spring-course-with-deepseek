# Aula 13 — Spring Batch

Objetivo: processar transações em lote com Job, Step e chunk processing, observar a execução e retomá-la depois de uma falha.

Domínio: conciliação de pagamentos. O serviço recebe um CSV do banco, normaliza cada transação e grava os pagamentos conciliados. O projeto é próprio desta aula e não depende das aulas anteriores.

## Base do projeto

```kotlin
implementation("org.springframework.boot:spring-boot-starter")
implementation("org.springframework.boot:spring-boot-starter-webmvc")
implementation("org.springframework.boot:spring-boot-starter-batch")
implementation("org.springframework.boot:spring-boot-starter-jdbc")
implementation("org.springframework.boot:spring-boot-starter-actuator")
runtimeOnly("com.h2database:h2")
```

O `spring-boot-starter-batch` fornece Job, Step e chunk. O `jdbc` dá acesso ao `DataSource` usado pelo `JobRepository` e pelo writer. O `webmvc` permite disparar e consultar o job pela API. O Actuator expõe a saúde da aplicação; o H2 mantém a aula executável sem infraestrutura externa.

A aplicação principal:

```java
package com.example.reconciliation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ReconciliationApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReconciliationApplication.class, args);
    }
}
```

## Conceitos

Uma requisição HTTP combina com uma resposta rápida e um volume pequeno. Conciliação bancária tem outro formato: o arquivo pode conter centenas de milhares de linhas, o processo pode durar minutos e uma falha no meio não pode obrigar o sistema a começar do zero. Batch organiza esse trabalho como uma execução controlada, com transações, estado e retomada.

O fluxo desta aula é:

1. O `JobLauncher` recebe o identificador de uma remessa.
2. O `Job` inicia o `reconciliationStep`.
3. O `ItemReader` lê 50 linhas do CSV.
4. O `ItemProcessor` normaliza e valida as linhas.
5. O `ItemWriter` grava o bloco numa única transação.
6. O `JobRepository` registra o resultado e o próximo ponto de leitura.

O Spring Batch estrutura esse fluxo em:

- **Job**: a unidade de execução.
- **Step**: uma etapa do job.
- **Chunk**: um bloco de itens lido, processado e gravado numa transação.
- **ItemReader / ItemProcessor / ItemWriter**: as três fases do chunk.
- **JobRepository**: persiste o estado da execução (o que rodou, onde parou).

O estado muda de acordo com o resultado. Um chunk confirmado avança o cursor; um chunk que falha sofre rollback. Se o processo cai depois do chunk 40 e antes de confirmar o 41, a retomada reprocessa o 41, não o arquivo inteiro. Por isso o writer precisa ser idempotente: o mesmo item pode ser tentado de novo.

Isso separa Batch de um loop simples no `@Scheduled`. Um loop sabe percorrer uma lista; o Batch sabe registrar uma execução, dividir transações, retomar e expor o resultado.

O `JobRepository` persiste `JobInstance`, `JobExecution`, `StepExecution` e os contextos usados para retomar. Essas tabelas não são os pagamentos; elas descrevem o processamento. Em produção, o metadata deve ficar num banco persistente, não no H2 em memória.

## Reader, processor, writer

Importar pagamentos de um CSV. O reader lê uma linha por vez e transforma as colunas no JavaBean `PaymentRow`:

```java
package com.example.reconciliation.payment;

import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class PaymentCsvReader {

    public FlatFileItemReader<PaymentRow> reader() {
        return new FlatFileItemReaderBuilder<PaymentRow>()
                .name("paymentCsvReader")
                .resource(new ClassPathResource("payments.csv"))
                .delimited()
                .names("transactionId", "customerEmail", "amount", "status")
                .targetType(PaymentRow.class)
                .build();
    }
}
```

O `FlatFileItemReader` mantém o cursor da linha no `ExecutionContext`, que o `JobRepository` salva junto da execução. O nome `paymentCsvReader` identifica esse reader no metadata; não troque esse nome entre reinícios, porque o contexto precisa continuar associado ao mesmo componente. O arquivo fica no classpath para a aula; em produção, use um recurso externo e passe sua localização como parâmetro do job.

`PaymentRow` é um JavaBean com os campos do CSV; o mapper chama os setters:

```java
package com.example.reconciliation.payment;

public class PaymentRow {

    private String transactionId;
    private String customerEmail;
    private String amount;
    private String status;

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = customerEmail;
    }

    public String getAmount() {
        return amount;
    }

    public void setAmount(String amount) {
        this.amount = amount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
```

O objeto intermediário não é uma entidade de banco. Ele representa uma linha enquanto ela atravessa o pipeline. Separá-lo da tabela permite validar e transformar o arquivo sem carregar o modelo inteiro da aplicação.

O processor normaliza cada item e valida o valor antes de o writer abrir a transação do chunk:

```java
package com.example.reconciliation.payment;

import java.math.BigDecimal;

import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

@Component
public class PaymentProcessor implements ItemProcessor<PaymentRow, PaymentRow> {

    @Override
    public PaymentRow process(PaymentRow row) {
        row.setTransactionId(row.getTransactionId().trim());
        row.setCustomerEmail(row.getCustomerEmail().trim().toLowerCase());
        row.setAmount(row.getAmount().trim().replace(',', '.'));
        row.setStatus(row.getStatus().trim().toUpperCase());
        try {
            new BigDecimal(row.getAmount());
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Valor inválido na transação " + row.getTransactionId(), exception);
        }
        return row;
    }
}
```

O processor pode retornar outro tipo quando a transformação exigir, ou retornar `null` para filtrar um item deliberadamente. Aqui ele mantém `PaymentRow` e lança `IllegalStateException` para que o step aplique a política de `skip`. A validação fica antes da escrita, então o writer só recebe valores normalizados.

O writer grava no banco. `JdbcClient` resolve sem puxar JPA:

```java
package com.example.reconciliation.payment;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class PaymentWriter implements ItemWriter<PaymentRow> {

    private final JdbcClient jdbcClient;

    public PaymentWriter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public void write(Chunk<? extends PaymentRow> chunk) {
        for (PaymentRow row : chunk) {
            jdbcClient.sql("""
                    INSERT INTO reconciled_payments
                        (transaction_id, customer_email, amount, status, reconciled_at)
                    VALUES (:transactionId, :customerEmail, :amount, :status, :reconciledAt)
                    """)
                    .param("transactionId", row.getTransactionId())
                    .param("customerEmail", row.getCustomerEmail())
                    .param("amount", new BigDecimal(row.getAmount()))
                    .param("status", row.getStatus())
                    .param("reconciledAt", Instant.now())
                    .update();
        }
    }
}
```

O Batch entrega o `Chunk` inteiro ao writer dentro da transação do step. O `for` apenas percorre os itens; o commit acontece depois que o método termina sem exceção. A restrição `UNIQUE` em `transaction_id` protege contra duplicação quando uma execução é retomada. Em sistemas que recebem a mesma remessa mais de uma vez, trate a duplicata como regra explícita, não como efeito colateral escondido.

## Job e Step

O job amarra reader → processor → writer num step chunked:

```java
package com.example.reconciliation.job;

import com.example.reconciliation.payment.PaymentCsvReader;
import com.example.reconciliation.payment.PaymentRow;
import com.example.reconciliation.payment.PaymentWriter;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class ReconciliationJobConfiguration {

    @Bean
    Job reconciliationJob(JobRepository jobRepository, Step reconciliationStep) {
        return new JobBuilder("reconciliationJob", jobRepository)
                .start(reconciliationStep)
                .build();
    }

    @Bean
    Step reconciliationStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
            PaymentCsvReader paymentCsvReader,
            ItemProcessor<PaymentRow, PaymentRow> processor,
            ItemWriter<PaymentRow> writer) {
        FlatFileItemReader<PaymentRow> reader = paymentCsvReader.reader();
        return new StepBuilder("reconciliationStep", jobRepository)
                .<PaymentRow, PaymentRow>chunk(50, transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .faultTolerant()
                .skip(IllegalStateException.class)
                .skipLimit(10)
                .build();
    }
}
```

`chunk(50)` lê e processa em blocos de 50 itens por transação: se um bloco falha, só ele volta, e o metadata registra o progresso. O Boot auto-configura `JobRepository`, `JobLauncher` e `PlatformTransactionManager` a partir do `DataSource`.

O tamanho do chunk é uma decisão operacional. Chunks pequenos confirmam progresso com mais frequência e usam menos memória, mas aumentam commits e chamadas ao banco. Chunks grandes reduzem overhead, mas mantêm mais dados na transação e repetem mais trabalho quando falham. Comece com um tamanho que caiba na memória e meça antes de aumentar.

O `Job` representa a conciliação inteira; o `Step` representa o pipeline que executa essa conciliação. Um job pode ter vários steps, como importar, validar e gerar um relatório. Esta aula usa um único step para deixar a transação do chunk visível.

## Rodando o job

O `JobLauncher` dispara o job sob demanda:

```java
package com.example.reconciliation.job;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.stereotype.Service;

@Service
public class ReconciliationRunner {

    private final JobLauncher jobLauncher;
    private final Job reconciliationJob;

    public ReconciliationRunner(JobLauncher jobLauncher, Job reconciliationJob) {
        this.jobLauncher = jobLauncher;
        this.reconciliationJob = reconciliationJob;
    }

    public JobExecution run(String runId) throws Exception {
        return jobLauncher.run(reconciliationJob, new JobParametersBuilder()
                .addString("runId", runId)
                .toJobParameters());
    }
}
```

`runId` identifica a remessa, não a hora da chamada. Se a aplicação cair, envie o mesmo `runId` para permitir que o Batch localize a execução interrompida. Para uma nova remessa, use outro identificador. O `JobRepository` rejeita uma nova execução com os mesmos parâmetros quando a anterior terminou com sucesso; isso evita importar o mesmo arquivo duas vezes por acidente.

O endpoint dispara uma execução e devolve o id para consulta:

```java
package com.example.reconciliation.web;

import com.example.reconciliation.job.ReconciliationRunner;
import org.springframework.batch.core.JobExecution;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reconciliation")
public class ReconciliationController {

    private final ReconciliationRunner runner;

    public ReconciliationController(ReconciliationRunner runner) {
        this.runner = runner;
    }

    @PostMapping("/runs/{runId}")
    public ResponseEntity<JobStatus> run(@PathVariable String runId) throws Exception {
        JobExecution execution = runner.run(runId);
        return ResponseEntity.ok(JobStatus.from(execution));
    }

    public record JobStatus(Long executionId, String status) {

        static JobStatus from(JobExecution execution) {
            return new JobStatus(execution.getId(), execution.getStatus().name());
        }
    }
}
```

O `200 OK` indica que o `JobLauncher` terminou a execução antes de responder. O `executionId` não é o `runId`: ele identifica a execução técnica persistida pelo Batch. Consulte esse id para confirmar `STARTING`, `STARTED`, `COMPLETED` ou `FAILED`. Para responder antes do fim, configure um `TaskExecutor` no launcher e trate a execução como assíncrona; não misture os dois modelos sem decidir como o cliente acompanhará o status.

Consulte o estado salvo pelo `JobRepository`:

```java
package com.example.reconciliation.web;

import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reconciliation/runs")
public class ReconciliationStatusController {

    private final JobExplorer jobExplorer;

    public ReconciliationStatusController(JobExplorer jobExplorer) {
        this.jobExplorer = jobExplorer;
    }

    @GetMapping("/{executionId}")
    public String status(@PathVariable Long executionId) {
        return jobExplorer.getJobExecution(executionId).getStatus().name();
    }
}
```

Se o id não existir, o controller deve transformar o resultado nulo em `404` numa aplicação de produção. O exemplo mantém a consulta curta para destacar o papel do `JobExplorer`, que lê metadata sem acessar diretamente as tabelas do Batch.

Pra o job não rodar sozinho na subida, desligue o auto-run:

```yaml
spring:
  batch:
    jdbc:
      initialize-schema: embedded
    job:
      enabled: false
management:
  endpoints:
    web:
      exposure:
        include: health,info
```

O schema:

```sql
CREATE TABLE reconciled_payments (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    transaction_id VARCHAR(100) NOT NULL UNIQUE,
    customer_email VARCHAR(255) NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    status VARCHAR(30) NOT NULL,
    reconciled_at TIMESTAMP WITH TIME ZONE NOT NULL
);
```

## Retomada e tolerância a falha

O `JobRepository` grava quantos itens de cada chunk já passaram. Se o processo cai no meio, o mesmo job com os mesmos `JobParameters` reinicia do ponto. Parâmetros idempotentes, como a data ou o id de uma remessa, identificam a execução: o mesmo id retoma uma execução interrompida; um id novo cria outra execução.

Para falha dentro de um chunk, o step usa `faultTolerant()`. `skip` pula o item ruim e segue; `retry` insiste no mesmo item. O batch inteiro não morre por um registro quebrado: pula, registra, e o restante continua. O mesmo `runId` só retoma uma execução interrompida; uma execução já concluída com os mesmos parâmetros não roda de novo.

Para executar o fluxo completo, inicie a aplicação, dispare uma execução e consulte o status. O `POST` devolve um `executionId`; substitua o placeholder do segundo request por esse valor:

```http
### Dispara a conciliação
POST http://localhost:8080/api/reconciliation/runs/2026-09-16

### Consulta a execução retornada pelo POST
GET http://localhost:8080/api/reconciliation/runs/{executionId}
```

O arquivo `payments.csv` precisa estar em `src/main/resources`:

```csv
transactionId,customerEmail,amount,status
bank-001,ana@example.com,120.50,paid
bank-002,bia@example.com,89,paid
bank-003,caio@example.com,42.75,pending
```

Para testar o skip, adicione uma linha com `amount` inválido. O processor lança `IllegalStateException`, o step descarta o item por causa do `skipLimit` e o restante do chunk continua. Consulte os logs, o status `COMPLETED` e as linhas gravadas em `reconciled_payments`; a transação não grava o item inválido.

Para testar retomada, remova temporariamente o `.skip(...)`, coloque uma linha inválida depois de mais de 50 linhas válidas e inicie a remessa. O job termina como `FAILED` depois de confirmar os chunks anteriores. Restaure o `skip`, reinicie com o mesmo `runId` e observe que o Batch usa o metadata para continuar da etapa interrompida. Em produção, o arquivo de entrada precisa continuar disponível e o writer precisa aceitar a repetição do último chunk sem duplicar dados.

O health endpoint confirma que a aplicação está de pé, mas não prova que o job terminou. Para acompanhar o job, use o `executionId` e o `JobExplorer`; para investigar a causa, consulte os logs do step e as tabelas de metadata.

## Estrutura

```
src/main/java/com/example/reconciliation/
├── ReconciliationApplication.java
├── payment/
│   ├── PaymentRow.java
│   ├── PaymentCsvReader.java
│   ├── PaymentProcessor.java
│   └── PaymentWriter.java
├── job/
│   ├── ReconciliationJobConfiguration.java
│   └── ReconciliationRunner.java
└── web/
    ├── ReconciliationController.java
    └── ReconciliationStatusController.java
src/main/resources/
├── payments.csv
├── requests.http
└── schema.sql
```
