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
9. Se o projeto já existir, associá-lo à operação original e continuar. Se a operação ainda estiver em andamento, aguardar/consultar. Se houver falha definitiva confirmada, mostrar o erro e oferecer nova tentativa somente no mesmo Project ID após nova verificação.
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

Na versão atual do aplicativo, a criação de projetos está deliberadamente desabilitada: a interface não fornece comando de criação. O usuário pode importar a lista de projetos existentes. A criação só deverá ser reintroduzida quando o fluxo persistente de estados, pré-verificação, confirmação explícita e reconciliação de operações estiver implementado e testado. A lista importada e o ambiente GEIVIA devem permanecer salvos ao reiniciar o app; nenhum reinício pode limpar silenciosamente o projeto selecionado.
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
- o comando consulta primeiro o mesmo ID com `gcloud projects describe`;
- somente uma resposta reconhecível de `NOT_FOUND` permite uma chamada de criação;
- falhas de permissão, rede ou qualquer erro não reconhecido encerram o script sem criar;
- após a solicitação, o aplicativo mantém o ID pendente e não oferece outra criação;
- a reconciliação consulta apenas o ID original e gera um inventário JSON para importação;
- o estado local só é reconciliado quando o inventário importado contém o mesmo Project ID.

O comando é copiado para execução manual no Cloud Shell. O aplicativo não executa operações de nuvem remotamente. Repetir manualmente o mesmo comando também é protegido pela pré-consulta: se o projeto já existir e estiver acessível, nenhuma segunda criação é enviada.

## Limites conhecidos

A saída de erro do Google Cloud CLI pode variar entre versões e idiomas. Se o texto não for reconhecido como ausência explícita, o comando falha de forma conservadora e não cria. Nesse caso, o usuário deve verificar o Project ID no Console e não alterar o identificador para contornar o erro.
