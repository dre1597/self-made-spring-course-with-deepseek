# Aula 13 — Spring Batch

Objetivo: processar grande volume em lote com Job, Step e chunk processing, retomando de onde parou.

## Conceitos

Batch processa volume em lote, fora de requisição HTTP. O Spring Batch estrutura isso em:

- **Job**: a unidade de execução.
- **Step**: uma etapa do job.
- **Chunk**: um bloco de itens lido, processado e gravado numa transação.
- **ItemReader / ItemProcessor / ItemWriter**: as três fases do chunk.
- **JobRepository**: persiste o estado da execução (o que rodou, onde parou).

A gravação do estado permite retomar: se o job cai no meio, reinicia do item onde parou, não do zero. Isso separa batch de um loop simples no `@Scheduled` (aula 06).

## Dependências

```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-batch")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    runtimeOnly("com.h2database:h2")
}
```

O Batch precisa de um `DataSource` pro `JobRepository` (tabelas de metadata da execução). O H2 em memória cobre a aula. Em produção, o metadata vai pro Postgres junto do dado.

## Reader, processor, writer

Importar livros de um CSV. O reader lê linha a linha:

```java
package com.example.importbatch.book;

import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class BookCsvReader {

    public FlatFileItemReader<BookRow> reader() {
        return new FlatFileItemReaderBuilder<BookRow>()
                .name("bookCsvReader")
                .resource(new ClassPathResource("books.csv"))
                .delimited()
                .names("title", "author")
                .targetType(BookRow.class)
                .build();
    }
}
```

`BookRow` é um JavaBean com os campos do CSV; o mapper chama os setters:

```java
package com.example.importbatch.book;

public class BookRow {

    private String title;
    private String author;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }
}
```

O processor normaliza cada item:

```java
package com.example.importbatch.book;

import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

@Component
public class BookProcessor implements ItemProcessor<BookRow, BookRow> {

    @Override
    public BookRow process(BookRow row) {
        row.setTitle(row.getTitle().trim());
        row.setAuthor(row.getAuthor().trim());
        return row;
    }
}
```

O writer grava no banco. `JdbcClient` (aula 04) resolve sem puxar JPA:

```java
package com.example.importbatch.book;

import java.time.Instant;

import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class BookWriter implements ItemWriter<BookRow> {

    private final JdbcClient jdbcClient;

    public BookWriter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public void write(Chunk<? extends BookRow> chunk) {
        for (BookRow row : chunk) {
            jdbcClient.sql("""
                    INSERT INTO books (title, author, created_at)
                    VALUES (:title, :author, :createdAt)
                    """)
                    .param("title", row.getTitle())
                    .param("author", row.getAuthor())
                    .param("createdAt", Instant.now())
                    .update();
        }
    }
}
```

## Job e Step

O job amarra reader → processor → writer num step chunked:

```java
package com.example.importbatch.job;

import com.example.importbatch.book.BookCsvReader;
import com.example.importbatch.book.BookProcessor;
import com.example.importbatch.book.BookRow;
import com.example.importbatch.book.BookWriter;

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
public class BookImportJobConfiguration {

    @Bean
    Job bookImportJob(JobRepository jobRepository, Step bookImportStep) {
        return new JobBuilder("bookImportJob", jobRepository)
                .start(bookImportStep)
                .build();
    }

    @Bean
    Step bookImportStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
            BookCsvReader bookCsvReader,
            ItemProcessor<BookRow, BookRow> processor,
            ItemWriter<BookRow> writer) {
        FlatFileItemReader<BookRow> reader = bookCsvReader.reader();
        return new StepBuilder("bookImportStep", jobRepository)
                .<BookRow, BookRow>chunk(50, transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .build();
    }
}
```

`chunk(50)` lê e processa em blocos de 50 itens por transação: se um bloco falha, só ele volta, e o metadata registra o progresso. O Boot auto-configura `JobRepository`, `JobLauncher` e `PlatformTransactionManager` a partir do `DataSource`.

## Rodando o job

O `JobLauncher` dispara o job sob demanda:

```java
package com.example.importbatch.job;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.stereotype.Service;

@Service
public class BookImportRunner {

    private final JobLauncher jobLauncher;
    private final Job bookImportJob;

    public BookImportRunner(JobLauncher jobLauncher, Job bookImportJob) {
        this.jobLauncher = jobLauncher;
        this.bookImportJob = bookImportJob;
    }

    public void run() throws Exception {
        jobLauncher.run(bookImportJob, new JobParameters());
    }
}
```

Pra o job não rodar sozinho na subida, desligue o auto-run:

```yaml
spring:
  batch:
    jdbc:
      initialize-schema: embedded
    job:
      enabled: false
```

O schema:

```sql
CREATE TABLE books (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    author VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
```

## Retomada e tolerância a falha

O `JobRepository` grava quantos itens de cada chunk já passaram. Se o processo cai no meio, o mesmo job com os mesmos `JobParameters` reinicia do ponto. Parâmetros idempotentes (data, id de lote) identificam a execução: rodar de novo com os mesmos parâmetros retoma; com parâmetros novos, cria execução nova.

Pra falha dentro de um chunk, `faultTolerant()` entra no step:

```java
return new StepBuilder("bookImportStep", jobRepository)
        .<BookRow, BookRow>chunk(50, transactionManager)
        .reader(reader)
        .processor(processor)
        .writer(writer)
        .faultTolerant()
        .skip(IllegalStateException.class)
        .skipLimit(10)
        .build();
```

`skip` pula o item ruim e segue; `retry` insiste no mesmo item. O batch inteiro não morre por um registro quebrado: pula, registra, e o restante continua.

## Estrutura

```
src/main/java/com/example/importbatch/
├── ImportBatchApplication.java
├── book/
│   ├── BookRow.java
│   ├── BookCsvReader.java
│   ├── BookProcessor.java
│   └── BookWriter.java
└── job/
    ├── BookImportJobConfiguration.java
    └── BookImportRunner.java
src/main/resources/
├── application.yaml
├── schema.sql
└── books.csv
```
