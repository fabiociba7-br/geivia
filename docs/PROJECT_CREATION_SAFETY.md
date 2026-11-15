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