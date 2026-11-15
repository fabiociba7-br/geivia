# Segurança contra projetos duplicados

## Regra inviolável

**Um erro nunca autoriza a criação de outro projeto.** O GEIVIA não deve gerar um novo Project ID, escolher outro projeto ou repetir uma criação com identificador diferente para contornar uma falha.

O padrão é selecionar e reutilizar um projeto existente. Criar projeto novo será uma ação separada, explícita, opcional e confirmada pelo usuário.

## Identidade fixa da operação

Antes de qualquer tentativa de criação, registrar localmente:
- identificador da operação;
- Project ID exato escolhido e validado;
- nome de exibição;
- data/hora de início;
- estado da operação;
- último erro e eventual nome da operação assíncrona retornada pelo Google.

O Project ID escolhido nunca muda automaticamente durante uma operação. O nome do ambiente GEIVIA não substitui nem gera automaticamente o Project ID do Google Cloud.

## Procedimento seguro de criação

1. **Pré-verificação:** consultar o Project ID exato com `gcloud projects describe PROJECT_ID`.
2. Se o projeto existir e estiver acessível à conta, selecionar esse projeto e continuar a configuração nele. Não criar outro.
3. Se existir, mas estiver inacessível ou sem permissão, interromper e explicar. Não tentar outro ID.
4. Se não existir, apresentar ao usuário o Project ID exato e pedir confirmação explícita de criação.
5. Antes de executar `gcloud projects create`, persistir o estado `CREATION_REQUESTED` e bloquear um segundo envio para a mesma operação.
6. Executar uma única solicitação com o mesmo Project ID escolhido. Guardar qualquer nome de operação retornado.
7. Se a resposta for timeout, conexão perdida ou resultado ambíguo, marcar `CREATION_STATUS_UNKNOWN`. **Não enviar outro comando de criação.**
8. Reconciliar consultando o mesmo Project ID com `gcloud projects describe PROJECT_ID` e, quando aplicável, consultar o estado da operação assíncrona original.
9. Se o projeto existir e estiver `ACTIVE`, associá-lo à operação original. Se a resposta ainda for ambígua, manter o bloqueio e reconciliar novamente o mesmo ID. A implementação atual só encerra uma tentativa sem projeto quando a consulta `describe` do mesmo ID produz o marcador exato `NOT_FOUND:` e o usuário importa a evidência `NOT_FOUND_CONFIRMED`; uma nova tentativa exige nova confirmação explícita e novo identificador de operação. Erros de permissão, rede ou formato não liberam a pendência.
10. Um Project ID diferente só pode ser escolhido pelo usuário, em uma nova ação explícita, depois de explicar por que o ID anterior não pode ser usado.

## Comportamento obrigatório diante de erros

| Situação | Comportamento |
|---|---|
| Internet caiu / timeout | Não criar de novo; verificar o mesmo Project ID |
| Resposta HTTP 5xx ou resposta desconhecida | Marcar resultado desconhecido; reconciliar antes de qualquer nova tentativa |
| Permissão insuficiente | Parar e explicar a permissão ausente; não trocar de projeto |
| API desabilitada | Informar qual API falta; não criar projeto substituto |
| Projeto já existe | Selecionar/reutilizar se acessível; não duplicar |
| Projeto em estado de exclusão ou indisponível | Parar e pedir decisão do usuário; não gerar outro ID |
| Erro em etapa posterior (Firebase, API, Hosting, regras) | Retomar a etapa que falhou no mesmo Project ID |
| App fechado ou aparelho reiniciado | Recuperar o estado salvo e retomar/reconciliar a mesma operação |

## Proteções de interface

- Mostrar sempre o nome e o Project ID do alvo antes de cada alteração.
- Exibir uma única ação **Tentar novamente neste projeto** quando for seguro.
- Não oferecer botão genérico de “corrigir criando outro projeto”.
- Não gerar IDs aleatórios para novas tentativas.
- Manter histórico de etapas concluídas para não repeti-las desnecessariamente.
- Separar **Selecionar projeto existente** de **Criar projeto novo**.
- Exigir confirmação específica para criar; confirmação para habilitar API ou alterar faturamento não vale como confirmação de criação.
- No modo custo zero, nunca associar conta de faturamento nem habilitar plano pago.

## Critério de aceite

Um teste automatizado deve simular timeout logo após a solicitação de criação. Ao retomar, o GEIVIA deve consultar o mesmo Project ID e não pode emitir uma segunda solicitação de criação nem inventar um novo ID.

Esta política deve ser aplicada a toda a cadeia de configuração, não apenas à tela inicial de criação.
## Estado da versão atual

A criação/reutilização assistida está disponível no app, mas o comando é executado manualmente pelo usuário no Cloud Shell. Antes de copiar o comando, o GEIVIA persiste o Project ID pendente e registra a etapa como em andamento; enquanto houver esse ID pendente, a interface bloqueia outra criação. O comando é gerado por `GoogleCloudProjectCommands` e tem testes unitários. A execução real na conta Google e a recuperação após interrupção ainda precisam ser validadas em dispositivo/conta de teste.
## Seleção persistente de projeto existente

A tela de acesso ao Google Cloud importa a lista JSON e permite selecionar explicitamente um projeto existente. Ao selecionar, o app persiste localmente `selected_project_id`, `selected_project_name` e `selected_project_number`. Reimportar a lista não altera a seleção. Se o projeto selecionado não aparecer na lista mais recente, o app mantém o ID e alerta o usuário; nunca escolhe outro automaticamente. O painel mostra o destino atual ou informa que nenhum projeto foi selecionado.

A seleção persistente identifica o destino escolhido, mas ainda não significa que o GEIVIA esteja executando operações de provisionamento diretamente. Antes de qualquer alteração futura no Cloud, a rotina deverá validar o acesso ao mesmo Project ID e usar esse ID em todas as chamadas; nenhuma etapa poderá inferir um novo projeto a partir do nome do ambiente GEIVIA.

## Diagnóstico somente leitura do projeto selecionado

Depois da seleção, o app gera um comando Cloud Shell usando o Project ID exato salvo. O comando valida o formato do ID, executa `gcloud projects describe` e `gcloud services list --enabled --project=...`, e reúne as respostas em `geivia-diagnostic.json`. Ele não cria projetos, não habilita APIs e não altera faturamento. Se a consulta falhar por inexistência ou falta de permissão, `set -e` interrompe o comando; o app não troca o ID nem sugere criar substituto.

Ao importar o relatório, o app exige que `project.projectId` corresponda exatamente ao projeto selecionado. Relatórios de outro projeto são rejeitados. Ao trocar de projeto, o resumo do diagnóstico anterior deixa de ser exibido, evitando associar resultados antigos ao novo destino.

Esta etapa é diagnóstico assistido pelo usuário no Cloud Shell. Ainda não é automação direta de alterações na nuvem.


## Implementação assistida no aplicativo

A etapa de criação/reutilização está disponível na tela Cloud Shell:
- o usuário escolhe e confirma explicitamente o Project ID e o nome;
- antes de copiar o comando, o app grava o ID pendente e registra a operação como `IN_PROGRESS`;
- o comando consulta primeiro o ID exato com `gcloud projects describe`;
- somente o marcador explícito `NOT_FOUND:` permite enviar uma única chamada de criação; mensagens genéricas como "not found" não são aceitas;
- falhas de permissão, rede, resposta desconhecida ou qualquer erro não reconhecido encerram o script sem criar;
- se a solicitação de criação falhar ou ficar ambígua, o script manda não repetir e encerra; o app mantém o mesmo ID pendente;
- após a criação, o script exige `lifecycleState == ACTIVE` antes de exportar o inventário;
- a reconciliação consulta somente o ID original e não contém comando de criação;
- a importação só conclui a etapa quando o mesmo Project ID aparece com estado `ACTIVE`;
- se a consulta de reconciliação retornar o marcador explícito `NOT_FOUND:`, o comando exporta `NOT_FOUND_CONFIRMED`; a importação encerra a operação anterior como falha reconciliada, remove o bloqueio pendente e permite uma nova tentativa somente após outra confirmação explícita do usuário;
- ausência em uma lista genérica de projetos nunca libera nova tentativa: somente a consulta `describe` do mesmo ID com erro explícito é aceita.

O comando é copiado para execução manual no Cloud Shell. O aplicativo não executa operações de nuvem remotamente. Mesmo que o usuário execute novamente o comando por engano, a pré-consulta reutiliza o projeto existente e não envia nova criação se ele estiver acessível.

## Testes automatizados

`GoogleCloudProjectCommandsTest` verifica a validação do ID, a exigência de marcador exato `NOT_FOUND:`, a presença de apenas uma chamada de criação, a interrupção em resultado ambíguo, a exigência de projeto `ACTIVE` e a reconciliação somente leitura. `WorkflowProgressStoreTest` verifica que uma operação desconhecida não pode ser reiniciada antes de reconciliação e que as etapas manuais permanecem distintas das conclusões por diagnóstico.

## Limites conhecidos

A saída de erro do Google Cloud CLI pode variar entre versões e idiomas. Se o texto não for reconhecido como ausência explícita, o comando falha de forma conservadora e não cria. Nesse caso, o usuário deve verificar o Project ID no Console e não alterar o identificador para contornar o erro.

## Proveniência dos arquivos importados

Os JSONs importados pelo aplicativo são arquivos fornecidos pelo próprio usuário. O GEIVIA valida a estrutura e compara o Project ID com a operação pendente, mas **não consegue comprovar criptograficamente que o arquivo foi produzido pelo Google Cloud**. Um JSON pode ser editado ou forjado fora do aplicativo.

Por isso, o marcador `NOT_FOUND_CONFIRMED` não deve ser descrito como prova autenticada do Google. Ao importar esse marcador para o mesmo ID pendente, o app exige uma confirmação adicional: o usuário declara que acabou de gerar o arquivo executando o comando de reconciliação do GEIVIA no Cloud Shell, na conta Google correta. Se não tiver certeza da origem, deve cancelar e manter o bloqueio.

Essa confirmação reduz liberações acidentais, mas continua sendo uma declaração do usuário, não uma validação criptográfica. A criação real permanece protegida pelo pré-teste do comando, que consulta novamente o ID exato antes de qualquer solicitação de criação.
