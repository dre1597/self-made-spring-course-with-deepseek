# AGENTS.md

Regras do curso de Spring.

## Comunicação

OBRIGATÓRIO — sem proatividade. Vale pra qualquer ação: editar aula, editar código, rodar comando, gravar em labs/. NUNCA execute sem pedido explícito do usuário. Pergunta ou comentário → responda SÓ. Pedido no imperativo → execute e pronto, sem "quer que eu aplique?". Na dúvida se foi pedido → foi pergunta. Pedido explícito = verbo de ação no imperativo dirigido a você; pergunta ou relato de problema NÃO é pedido. EXEMPLO PROIBIDO: usuário diz "fiz a aula e não compila" → responda o diagnóstico e PARE. Nada de editar o build.gradle.kts.

OBRIGATÓRIO — passo a passo. Vale pra qualquer trabalho com mais de um passo: edição de aula, código, resposta longa. Ler tudo pra contextualizar é ok. O que não pode é elaborar a resposta ou a mudança inteira de uma vez e ficar re-pensando o conjunto. Assim que tiver claro o PRIMEIRO passo, execute-o já (a edição, o comando, a primeira parte da resposta). Não escreva tudo primeiro. Depois de executar, defina e execute o segundo, e assim por diante. O próximo passo se define a partir do que acabou de ficar pronto, não de re-planejar o todo. Exemplo (aula): pediu "arruma as aulas 1, 2 e 3" → lê as três, edita a 1, fecha, depois a 2. Exemplo (código): pediu uma mudança com vários arquivos → edita o primeiro arquivo até o fim antes de abrir o segundo, em vez de escrever o diff mental de todos.

Antes de editar uma aula, leia o arquivo físico atual e faça um mapa curto dos conceitos, projetos, arquivos, comandos, rotas e dependências envolvidos. Use esse mapa para verificar a ordem dos exemplos durante a edição.

Depois de cada parte editada, confira se o próximo trecho da aula ainda faz sentido. Não trate a alteração como concluída só porque o snippet isolado parece correto.

- Responda em português brasileiro, tom informal carioca. Nada de formalidade.
- Siga o AGENTS.md global (em `~/.config/opencode/AGENTS.md`).
- Não fique perguntando se pode seguir pro próximo módulo. Responda cru e direto.
- Quando o usuário pedir pra fazer algo, faça e pronto. Não devolva "quer que eu aplique?" / "quer que eu faça X?" — a resposta é sempre sim. Se o usuário quiser mudar algo, ele fala. Só pergunte quando houver ambiguidade real que muda o resultado.
- Use a skill stop-slop em todo texto que escrever.
- Curso individual, só pro usuário. Nada de "turma", "alunos" ou linguagem de turma.

## Edição de arquivos
- Sempre leia o arquivo antes de editar. O usuário edita junto, o arquivo pode ter mudado desde a última leitura. Pegue sempre a versão atualizada.
- Mantenha o padrão do curso definido no README.md.

## Código
- Código em inglês. Textos de interface e mensagens podem ser em português.
- Stack fixa: Java 25 LTS, Gradle, Spring Boot 4.1 / Spring Framework 7.
- Siga boas práticas e convenções do Java.
- Nomenclatura descritiva. Nada de nome de variável genérico.
- Código descritivo vale mais que código curto. Não abrevia nome nem economiza linha à custa de clareza.
- Prefira `var` quando o tipo é óbvio do lado direito (`new X()`, `List.of(...)`, `Map.of(...)`). Mantenha o tipo explícito quando ele não é evidente do lado direito ou é parte de contrato. `var` nunca em campo de classe, parâmetro ou retorno.
- Nome de tabela no plural (convenção SQL). O JPA não pluraliza sozinho: o nome de tabela vai sempre explícito na entidade, ex. `@Table(name = "orders")`. Coluna fica no singular.
- Prefira código descritivo a comentários. Explicação de aula pode ter comentário, código solto não.
- Todo exemplo de código pertence a um projeto (ou lab) que roda. Nada de snippet solto.
- Cada aula usa projetos próprios. Não reaproveite projetos das aulas anteriores. Quando uma aula precisar de mais de um processo, cada processo deve ter projeto, classe principal, package, dependências, configuração e instruções de execução próprios. Não troque silenciosamente o projeto, a porta ou a configuração no meio da sequência.

## Labs
- Existe a pasta `labs/` para exemplos de código.
- Só grave código em `labs/` quando o usuário pedir explicitamente.

## Conteúdo
- Nada de artifício de cursinho pra iniciante.
- Sem pontas soltas. Todo tópico mencionado na aula precisa ser explicado.
- Sem pergunta retórica, sem storytelling, direto ao ponto.

## Definition of Done de uma aula

Uma aula só está concluída quando:

- o conceito aparece antes do código que o demonstra;
- cada dependência usada no código aparece no projeto correspondente;
- cada arquivo de configuração simples é mostrado completo quando criado ou alterado;
- cada classe, rota, comando e arquivo é apresentado antes de ser usado;
- cada request aponta para um endpoint implementado;
- cada request explica o resultado esperado;
- cada comando informa o diretório e os processos necessários;
- cada projeto mantém package, porta e configuração coerentes durante toda a aula;
- exemplos posteriores não dependem de uma etapa ainda não apresentada;
- a árvore de arquivos corresponde aos arquivos realmente usados;
- a aula pode ser seguida na ordem escrita sem depender de inferências do leitor.

Antes de concluir uma edição de aula, faça uma revisão final procurando:

- referências a classes ou rotas ainda não apresentadas;
- dependências faltantes ou declaradas em outro projeto;
- configurações parciais ou contraditórias;
- portas, packages e nomes de projeto inconsistentes;
- comandos sem pré-requisito explicado;
- requests sem endpoint correspondente;
- afirmações no texto que o código não demonstra.

Não diga que uma aula foi validada se apenas o Markdown foi revisado. Diferencie claramente revisão textual, inspeção dos labs e execução de testes.
