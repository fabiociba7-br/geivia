# Arquitetura de autorização Google do GEIVIA

## Decisão

O GEIVIA terá integração Google completa e progressiva. A conta conectada será sempre escolhida pelo usuário final. Nenhuma conta pessoal do desenvolvedor, projeto Firebase pessoal ou credencial pessoal será usada para operar projetos de clientes.

O usuário nunca informa a senha Google dentro do GEIVIA. Login e consentimento acontecem nas telas oficiais do Google.

## Princípios obrigatórios

1. Solicitar cada escopo somente quando o usuário iniciar uma função que dependa dele (autorização incremental).
2. Antes do consentimento, explicar os dados, finalidade, benefício e consequência de negar.
3. Consentimento de leitura nunca autoriza escrita, criação, exclusão, ativação de API, alteração IAM, faturamento ou publicação.
4. Verificar os escopos realmente concedidos. Se um for negado, desabilitar somente a função dependente.
5. Guardar somente dados necessários; nunca registrar tokens, códigos OAuth ou conteúdo sensível nos logs.
6. Oferecer desconexão, limpeza de dados locais de sessão e instruções para revogar o acesso na Conta Google.
7. Nenhuma operação que possa gerar custo será executada sem confirmação específica imediatamente antes dela.

## Fluxo do usuário

### Etapa 0 — Sem conexão
Questionário e pré-análise funcionam sem login. Os dados permanecem no aparelho.

### Etapa 1 — Identidade básica
Ao tocar em "Conectar Google", explicar que a conta identifica o usuário e personaliza a sessão.

Escopos:
- `openid`
- `https://www.googleapis.com/auth/userinfo.email`
- `https://www.googleapis.com/auth/userinfo.profile`

Exibir nome, e-mail e foto apenas quando disponíveis e úteis. Não solicitar Gmail, Drive, Agenda ou contatos.

### Etapa 2 — Projetos Firebase
Explicar que o GEIVIA vai listar projetos Firebase acessíveis à conta, sem alterar nada.

Escopo:
- `https://www.googleapis.com/auth/firebase.readonly`

API: Firebase Management API `projects.list`, incluindo paginação.

### Etapa 3 — Projetos Google Cloud
Explicar que o GEIVIA vai listar projetos Google Cloud aos quais a conta tem acesso, inclusive os que ainda não têm Firebase.

Escopo proposto:
- `https://www.googleapis.com/auth/cloudplatformprojects.readonly`

API: Cloud Resource Manager `projects.list` e, se necessário, `projects.get`.

Se o método exato exigir outro escopo, não ampliar silenciosamente: explicar e solicitar autorização contextual adicional.

### Etapa 4 — Serviços e APIs
Após o usuário selecionar um projeto, explicar que o GEIVIA consultará quais serviços/APIs estão habilitados para identificar o que falta.

Escopo proposto:
- `https://www.googleapis.com/auth/service.management.readonly`

API: Service Usage API, somente leitura. Validar o escopo exigido pelo método REST exato antes da implementação. Nunca substituir automaticamente por `cloud-platform`.

### Etapa 5 — Situação de faturamento
Somente quando o usuário pedir diagnóstico de custos, explicar que serão consultados dados de faturamento que a conta já tem permissão para visualizar.

Escopo:
- `https://www.googleapis.com/auth/cloud-billing.readonly`

API: Cloud Billing, somente métodos de leitura necessários para verificar associação e acesso. Não ler cartões, credenciais de pagamento ou documentos financeiros desnecessários.

A interface deve distinguir: faturamento associado; não associado; sem permissão para verificar; informação indisponível/erro. Falta de acesso não significa ausência de faturamento.

## Escrita — autorização independente

Nenhum escopo de escrita será pedido na conexão inicial ou no diagnóstico.

Quando o usuário iniciar uma operação (criar projeto, ativar Firebase/API, registrar aplicativo, criar banco ou publicar Hosting), o GEIVIA deverá:
1. Mostrar projeto e alteração exata.
2. Explicar pré-requisitos, impacto e riscos.
3. Informar se pode exigir faturamento.
4. Aplicar a escolha de custo: modo custo zero bloqueia operações que exijam faturamento; modo pago permitido ainda exige autorização individual.
5. Pedir o escopo de escrita estritamente necessário à operação, com justificativa e consentimento Google.
6. Mostrar confirmação final dentro do GEIVIA.
7. Executar somente após ambas as autorizações.
8. Registrar localmente resultado, horário, projeto e ação, sem token.
9. Em falha ou falta de permissão, explicar e parar; nunca contornar permissões.

Não pedir `https://www.googleapis.com/auth/cloud-platform` por conveniência. Se uma operação indispensável não puder ser feita com escopo menor, explicar o motivo e pedir autorização adicional antes de prosseguir.

## Matriz inicial

| Fase | Finalidade | Escopo | Acesso |
|---|---|---|---|
| Identidade | Nome, e-mail e perfil básico | openid, userinfo.email, userinfo.profile | Identidade |
| Firebase | Listar projetos Firebase | firebase.readonly | Leitura |
| Google Cloud | Listar projetos acessíveis | cloudplatformprojects.readonly | Leitura |
| Serviços | Consultar APIs/serviços ativos | service.management.readonly | Leitura |
| Custos | Consultar faturamento visível à conta | cloud-billing.readonly | Leitura |
| Alterações | Criar/configurar/publicar | Escopo específico validado por operação | Escrita |

A matriz é uma proposta. Confirmar na documentação oficial o escopo aceito pelo método REST exato antes de codificar cada integração. OAuth scope e papel IAM são controles diferentes; possuir um escopo não significa ter permissão no projeto.

## Dados e tokens

Por conta conectada, guardar localmente apenas o identificador estável da conta (sub), nome/e-mail exibidos, escopos concedidos, data da última sincronização, projetos descobertos, estado do diagnóstico e preferências de custo.

Tokens de acesso ficam em memória pelo menor tempo possível. Nunca gravar tokens em SharedPreferences, arquivos, logs ou exportações. Não armazenar refresh token no APK. Se acesso offline vier a ser necessário, desenhar e aprovar separadamente o fluxo seguro antes de habilitá-lo.

Exportações não podem conter tokens, IDs de sessão ou detalhes privados de faturamento.

## Identidade OAuth do produto

Para o Google identificar o app como GEIVIA e emitir tokens, o produto precisa de um cliente OAuth registrado sob responsabilidade do publicador/produto. Essa identidade é independente da conta pessoal do desenvolvedor e das contas dos usuários. O usuário final não precisa criar projeto Google Cloud.

O responsável pelo produto precisará manter a configuração OAuth, marca, política de privacidade, termos e realizar a verificação Google aplicável. OAuth real não pode funcionar sem cliente OAuth registrado. Até essa identidade institucional existir, manter o modo offline e não simular conexão.

## Critérios de aceite

- Questionário e pré-análise continuam disponíveis sem login.
- Conexão inicial solicita somente identidade básica.
- Firebase, projetos Cloud, serviços e faturamento pedem consentimento separadamente e no contexto.
- Negar uma permissão não bloqueia recursos independentes.
- Interface mostra permissões concedidas e limitações.
- Leitura nunca dispara mutação.
- Modo custo zero bloqueia operações que exigem faturamento.
- Escrita exige consentimento Google e confirmação no GEIVIA separados.
- Desconectar limpa tokens e dados da sessão.
- Nenhuma conta pessoal do desenvolvedor é usada.
