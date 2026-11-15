# Continuidade do fluxo GEIVIA

## Checkpoint central de navegação

A aplicação persiste localmente:
- a tela/etapa atual (`workflow_checkpoint`);
- o índice da pergunta atual (`workflow_question_index`);
- o horário da última atualização (`workflow_checkpoint_updated_at`).

Ao reabrir o aplicativo, a tela inicial oferece **Continuar de onde parei** e restaura a última etapa válida. O índice do questionário também é restaurado. A gravação ocorre automaticamente quando a tela ou o índice muda.

O botão de reiniciar questionário limpa o checkpoint anterior e inicia um novo percurso pela configuração do ambiente. Não remove o projeto Google Cloud selecionado nem recursos na nuvem.

## Limite importante

Este checkpoint cobre a continuidade da navegação/onboarding. Ele não significa que todas as operações de nuvem já tenham um motor universal de estados. Cada operação externa ainda precisa de seu próprio estado persistente, pré-verificação, confirmação, reconciliação e histórico antes de ser considerada retomável com segurança.

Em particular:
- nunca repetir criação de projeto ou banco por timeout;
- não considerar uma etapa externa concluída apenas porque o comando foi copiado;
- exigir novo diagnóstico para confirmar o resultado no Google Cloud;
- manter o mesmo Project ID durante a recuperação;
- não ativar faturamento nem recursos pagos sem autorização separada.

## Estado atual da integração

O registro central está integrado às etapas de criação/reconciliação de projeto, habilitação individual de APIs, Firestore, Authentication e Hosting. As verificações específicas de cada serviço continuam obrigatórias: o estado central organiza o ciclo de vida, mas não substitui diagnósticos, pré-condições ou evidências próprias de cada recurso. A interface distingue conclusão comprovada por diagnóstico (`COMPLETED`) de declaração manual do usuário (`USER_CONFIRMED`).

## Registro central de operações (v1)

O arquivo `WorkflowProgress.kt` adiciona um registro persistente por combinação de Project ID + identificador da etapa. Cada registro guarda estado, horário, identificador da operação, mensagem e evidência de reconciliação. O histórico local é limitado às últimas 200 transições.

Regras da máquina de estados:

- `NOT_STARTED` pode aguardar confirmação; não executa nada por si só.
- `WAITING_CONFIRMATION` só passa a `IN_PROGRESS` quando o chamador registra uma confirmação explícita e um identificador de operação novo, diferente do identificador da tentativa anterior.
- `IN_PROGRESS` pode terminar em `COMPLETED` somente com evidência não vazia de diagnóstico externo; ou em `FAILED` quando a falha é conhecida.
- Timeout, perda de conexão ou resposta ambígua devem resultar em `UNKNOWN`.
- `UNKNOWN` não pode ser iniciado novamente. Só pode ser reconciliado com diagnóstico recente do mesmo Project ID: recurso encontrado = `COMPLETED`; recurso explicitamente ausente + confirmação do usuário = `FAILED`. A etapa não dispara nova operação automaticamente.
- `COMPLETED` é terminal para essa etapa. Uma alteração futura deve ser modelada como nova operação/etapa, não apagando o registro anterior.
- Se o JSON persistido de progresso estiver corrompido, o GEIVIA falha de forma fechada: bloqueia a leitura/início das operações e não interpreta o histórico como vazio. Isso evita que uma criação anterior pareça nunca ter sido iniciada e seja repetida por engano.



## Integração inicial com a interface

A tela Dashboard agora exibe o catálogo inicial de etapas e o estado persistido para o Project ID selecionado. Cada projeto mantém seu próprio conjunto de registros.

A etapa de criação do Firestore foi conectada ao motor central:
1. Ao tocar em “Revisar criação do banco”, a etapa passa para `WAITING_CONFIRMATION`.
2. Ao confirmar e copiar o comando manual, passa para `IN_PROGRESS`, com identificador local da operação.
3. A cópia do comando não significa que o banco foi criado.
4. Um diagnóstico posterior, importado para o mesmo Project ID e contendo `(default)`, reconcilia a etapa como `COMPLETED`.
5. Se o banco não aparecer, a pendência continua bloqueada; não se oferece repetição automática.

A reconciliação é tolerante com instalações que tinham uma pendência Firestore anterior à introdução do registro central. O histórico legado continua preservado.

**Limitação atual:** diagnóstico e execução de comandos no Google Cloud continuam assistidos e manuais. O app não recebe a sessão do Cloud Shell e não executa alterações remotas. Storage permanece bloqueado no modo custo zero. Cada etapa ainda depende de diagnóstico/evidência específica; não existe validação automática de todas as configurações em tempo real.


## Integração das etapas manuais (Authentication e Hosting)

Firebase Authentication e Firebase Hosting agora usam o registro central de progresso:
- Ao iniciar, o GEIVIA registra o estado IN_PROGRESS e abre o painel do projeto existente no Console Firebase.
- Depois da configuração, o usuário pode registrar uma confirmação manual.
- Essa confirmação recebe o estado USER_CONFIRMED, distinto de COMPLETED.
- O painel deixa explícito que a configuração foi declarada pelo usuário e não verificada automaticamente pelo Google.
- Se o estado estiver UNKNOWN ou FAILED, o fluxo não reinicia automaticamente.

O Hosting declarado configurado ainda não significa que o código foi publicado nem que o endereço público foi testado. O deploy e a validação do site são etapas separadas.

## Cloud Storage e custo zero

O Storage permanece bloqueado no modo custo zero. A documentação oficial do Firebase informa que o plano Blaze é necessário para provisionar novos buckets padrão e para manter acesso aos recursos do Cloud Storage for Firebase; o uso sem custo dentro de cotas não elimina a exigência de vincular uma conta de faturamento. Portanto, o GEIVIA não oferece criação de bucket nem upgrade automático no modo custo zero.

Referência oficial: https://firebase.google.com/docs/storage/faqs-storage-changes-announced-sept-2024


## Pré-requisitos das etapas de produto

A configuração de Authentication e Hosting agora é liberada somente depois que um diagnóstico novo, do Project ID selecionado, confirmar respectivamente:
- `identitytoolkit.googleapis.com` habilitada antes de abrir os provedores de login;
- `firebasehosting.googleapis.com` habilitada antes de abrir a configuração do Hosting.

Habilitar a API é apenas um pré-requisito técnico. Authentication ainda exige ativar os provedores desejados no Console Firebase. Hosting ainda exige criar/configurar o site, fazer o deploy do conteúdo e testar a URL pública. A confirmação manual do usuário não substitui essas verificações independentes.


## Criação de projeto, regras e Hosting

A tela Cloud Shell inclui criação/reutilização assistida de projeto com Project ID informado pelo usuário. O script consulta o ID exato antes de criar, não interpreta erros desconhecidos como ausência e bloqueia uma segunda criação enquanto a operação aguarda reconciliação. O usuário executa o comando manualmente e importa o inventário para confirmar o mesmo ID.

### Regras iniciais do Firestore

Quando o diagnóstico confirma o banco `(default)`, a etapa de regras oferece um comando que consulta a API Firebase Rules antes de publicar. Se já existir uma release `cloud.firestore`, o script interrompe sem sobrescrever. Se não existir, publica um conjunto inicial deny-all (`allow read, write: if false`). Essa regra é deliberadamente restritiva: nenhum cliente pode ler ou gravar até que regras específicas por coleção e usuário sejam modeladas, testadas e publicadas. A confirmação no aplicativo continua sendo uma declaração manual, não uma verificação automática do conteúdo ativo.

### Firebase Hosting

Quando a API Firebase Hosting está ativa, a etapa de publicação prepara um site estático demonstrativo no site padrão do mesmo Project ID. O script consulta o site pela API REST, cria somente quando a resposta é HTTP 404, interrompe em outros erros e não sobrescreve pastas locais que não tenham marcador GEIVIA. Antes de qualquer criação remota, também valida que o `firebase.json` local aponta para o site padrão do mesmo Project ID e para a pasta `public`; se o destino estiver alterado, interrompe. A etapa de regras Firestore aplica proteção equivalente, exigindo que `firebase.json` aponte para `firestore.rules`. O deploy é limitado a `hosting`, sem publicar Functions ou outros serviços. A página é um placeholder e precisa ser substituída pelo site real antes da divulgação.

Os comandos são copiados para o Cloud Shell e executados pelo usuário. Eles não são executados pelo APK nem usam credenciais armazenadas no aplicativo.
