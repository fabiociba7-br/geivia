# Acesso do GEIVIA ao Google Cloud — arquitetura híbrida

## Decisão de produto

O GEIVIA não usará OAuth registrado na conta pessoal do desenvolvedor, nem pedirá que cada usuário crie um cliente OAuth para conseguir começar.

O primeiro acesso cria um ambiente local com:
- nome escolhido pelo usuário;
- identificador exclusivo gerado no aparelho;
- preferências e dados do projeto mantidos localmente.

Para acessar os recursos do Google Cloud, o GEIVIA abre o Console e o Cloud Shell no navegador. O usuário entra diretamente com a própria conta Google. Os comandos executados no Cloud Shell usam a sessão autenticada daquele usuário e as permissões IAM que ele já possui.

Essa arquitetura evita colocar client secret, chave de conta de serviço ou token OAuth no APK e não exige backend pago.

## Por que não criar um OAuth por usuário dentro do APK

- O Google exige criação e alteração manual de clientes OAuth no Cloud Console; essa criação não pode ser automatizada por API.
- Clientes Android são vinculados ao nome do pacote e à impressão digital SHA-1 do certificado.
- A documentação de OAuth para apps instalados alerta que o fluxo de app para computador não deve ser reaproveitado como se fosse um fluxo Android e que autorização incremental não é compatível com esse fluxo.
- O uso da API AuthorizationClient do Google Play services depende de configuração de identidade Android apropriada ao aplicativo. Não presumir que cada usuário possa trocar livremente o projeto OAuth usado pelo mesmo APK.
- Portanto, não apresentar a criação de um projeto/cliente OAuth pessoal como requisito do GEIVIA até que exista uma implementação Android suportada e comprovada para esse modelo.

## Fluxo híbrido

1. O usuário instala o APK.
2. Na primeira abertura, escolhe o nome do ambiente.
3. O GEIVIA gera um identificador local único, por exemplo `GEIVIA-A1B2C3D4E5`.
4. O usuário toca em **Abrir Google Cloud** e autentica no navegador, fora do GEIVIA.
5. O usuário abre o Cloud Shell, que executa comandos na sessão Google já autenticada.
6. O GEIVIA fornece um comando de diagnóstico somente leitura para listar projetos em JSON.
7. O usuário executa e revisa o comando no terminal.
8. O comando usa `cloudshell download geivia-projects.json` para baixar o resultado ao aparelho.
9. O usuário seleciona o JSON no GEIVIA; o app valida e importa apenas ID, nome, número e estado dos projetos.
10. O usuário seleciona explicitamente um projeto existente; o GEIVIA persiste o Project ID exato e mantém a escolha mesmo após nova importação ou reinício.
11. Para o projeto selecionado, o GEIVIA gera um diagnóstico somente leitura (`gcloud projects describe` e `gcloud services list --enabled`) e importa o relatório apenas se o Project ID corresponder exatamente ao destino salvo.
12. A criação de projetos permanece desabilitada nesta versão. Só será reintroduzida como ação separada, confirmada e com reconciliação segura de operações ambíguas.
13. Para futuras operações de escrita, o GEIVIA deverá sempre mostrar o comando exato, projeto, impacto e custo possível, exigindo confirmação do usuário antes de copiar/executar.

## Limites e segurança

- É proibido criar um projeto substituto como reação a erro. A política completa de pré-verificação, persistência de operação, reconciliação após timeout e retomada no mesmo Project ID está em [PROJECT_CREATION_SAFETY.md](PROJECT_CREATION_SAFETY.md).

- O GEIVIA não recebe automaticamente a sessão do navegador nem o token do Cloud Shell.
- Nunca pedir ao usuário para copiar tokens de acesso, cookies, senha, arquivos de credenciais ou chaves JSON de conta de serviço para dentro do app.
- Não usar `gcloud auth print-access-token` para transferir token ao GEIVIA. O comando pode ser usado internamente pelo próprio Cloud Shell quando necessário, mas o token não deve sair da sessão.
- O Cloud Shell opera com a identidade e permissões do usuário autenticado; falta de IAM deve ser mostrada como falta de permissão, sem contornar.
- A primeira etapa de diagnóstico é somente leitura: `gcloud auth list` e `gcloud projects list`.
- O comando de diagnóstico inicial é somente leitura e não cria projetos, ativa APIs, altera IAM, associa faturamento ou publica serviços. A criação de projeto é uma ação opcional separada, apresentada com comando próprio e executada somente se o usuário decidir copiá-lo e executá-lo no Cloud Shell.
- Modo custo zero bloqueia instruções que exijam ativar faturamento. A escolha de permitir custos não autoriza cobrança automaticamente.

## Próximas fases

1. Consultar Firebase e APIs habilitadas com comandos de leitura, sempre no Cloud Shell.
2. Importar os resultados estruturados desses diagnósticos.
3. Gerar comandos específicos para cada configuração.
4. Permitir execução assistida de alterações somente após revisão e confirmação explícita.
5. Reavaliar OAuth Android direto somente se houver necessidade real e um fluxo compatível com os requisitos atuais do Google.

## Referências oficiais

- [Google OAuth — boas práticas e criação manual de clientes](https://developers.google.com/identity/protocols/oauth2/resources/best-practices)
- [OAuth 2.0 para apps instalados](https://developers.google.com/identity/protocols/oauth2/native-app)
- [Autorização no Android](https://developer.android.com/identity/authorization)
- [Google Cloud Shell](https://cloud.google.com/shell/docs)
## Diagnóstico de prontidão Firebase

Depois do diagnóstico do projeto, o app interpreta a lista de serviços habilitados e apresenta o estado de APIs comuns: Firebase Management, Cloud Firestore, Firebase Authentication (Identity Toolkit), Cloud Storage for Firebase, Firebase Hosting e Cloud Build.

Esse painel é informativo: uma API não listada não significa automaticamente que o projeto esteja incorreto, pois a necessidade depende das escolhas do questionário. A versão atual não ativa APIs, não altera IAM e não associa faturamento. O resultado é vinculado ao Project ID exato do relatório importado; ao trocar o projeto, o resumo anterior não é tratado como diagnóstico do novo destino.
## Cruzamento do questionário com APIs

A tela de análise agora deriva uma lista de APIs a partir das respostas: Authentication quando há login; Firestore quando há dados; Storage quando há arquivos; Hosting quando a plataforma inclui web; Cloud Build como possibilidade opcional para fluxos avançados de implantação/processamento. Firebase Management API aparece como requisito de gerenciamento do Firebase.

Quando existe diagnóstico importado do mesmo Project ID selecionado, cada item é comparado com os serviços habilitados no relatório e marcado como ativo ou pendente. Sem projeto selecionado ou sem diagnóstico correspondente, o app não presume o estado das APIs.

No modo custo zero, Storage e Cloud Build com risco de exigir faturamento são destacados como bloqueados para configuração automática. A classificação é conservadora e não substitui a verificação dos limites e preços vigentes do serviço. Nenhuma API é ativada por esta análise; ela apenas prepara a lista de trabalho.
## Plano de execução no painel

O painel pós-questionário organiza as etapas de configuração com base nas respostas e no projeto selecionado: confirmar o destino existente, importar diagnóstico vinculado ao mesmo Project ID, avaliar somente os serviços relacionados às respostas, revisar limites gratuitos e custos, e exigir autorização individual antes de qualquer alteração.

O plano exibido nesta etapa é informativo e não executa comandos nem habilita APIs. A implementação de alterações deve ser feita em uma etapa posterior, com confirmação por serviço, apresentação do comando exato, execução pelo usuário no Cloud Shell e registro verificável do resultado. No modo custo zero, qualquer serviço que exija faturamento deve ser bloqueado, sem ativar Blaze ou associar forma de pagamento.

O relatório de diagnóstico só é considerado correspondente quando o `project.projectId` é exatamente igual ao projeto selecionado no aparelho.

## Habilitação assistida de APIs — etapa implementada

Com um diagnóstico recente vinculado ao projeto selecionado, o GEIVIA cruza as respostas do questionário com as APIs necessárias e apresenta ações individuais. Para cada API pendente e permitida pelo modo de custo, o usuário pode abrir uma confirmação que mostra o nome da API, o Project ID e o comando exato:

```bash
gcloud services enable API --project=PROJECT_ID
```

O botão de confirmação apenas copia um comando por vez. O usuário continua responsável por revisar e executar o comando no Cloud Shell. O app não executa chamadas de escrita no Google Cloud e não habilita APIs em segundo plano.

Após a execução manual, o usuário deve gerar e importar um novo diagnóstico somente leitura. O GEIVIA só registra a API como confirmada quando o relatório pertence ao mesmo Project ID e lista o serviço como habilitado. Um comando copiado não é considerado sucesso.

No modo custo zero, Cloud Storage for Firebase e Cloud Build permanecem bloqueados nesta etapa por risco de limites/faturamento. O usuário não pode contornar esse bloqueio pela interface. A ativação de uma API não garante que o uso posterior do serviço seja gratuito; os limites e preços devem ser conferidos antes da utilização.

## Histórico local das ações de API

O app mantém no armazenamento local do aparelho um histórico limitado das últimas 30 ações preparadas e das confirmações recebidas por diagnóstico. Cada entrada registra o Project ID e o nome técnico da API. Uma ação preparada fica explicitamente como aguardando diagnóstico; só uma importação posterior que liste a API como habilitada, vinculada ao mesmo Project ID, gera uma entrada de confirmação.

Esse histórico é informativo e local: não é um log oficial do Google Cloud, não prova quem executou o comando e não é sincronizado com outros dispositivos. Trocar o projeto limpa os indicadores visuais do diagnóstico anterior, mas preserva o histórico para consulta, sempre identificado pelo Project ID.

O fluxo mantém apenas uma habilitação pendente por vez para cada aparelho/projeto. Enquanto houver uma ação aguardando diagnóstico para o projeto atual, o app não oferece a preparação de outra API. Isso evita sobrescrever o identificador da ação pendente antes de verificar seu resultado. Se o serviço não aparecer no diagnóstico, a ação permanece pendente e deve ser analisada antes de qualquer nova tentativa.

### Resolução explícita de acompanhamento pendente

Ao importar um diagnóstico atualizado do mesmo projeto, se a API pendente ainda não aparecer na lista de serviços habilitados, o GEIVIA mantém a pendência e informa que não repetirá o comando automaticamente. O usuário pode encerrar somente o acompanhamento local após uma confirmação explícita. Essa ação remove os metadados locais da pendência e registra o encerramento no histórico, mas não desabilita serviços, não exclui recursos, não altera o projeto Google Cloud e não afirma que o comando falhou. Se o comando já tiver sido executado, a orientação é importar um novo diagnóstico antes de encerrar o acompanhamento.

## Firestore Standard e Authentication Spark — fluxo assistido

O diagnóstico de projeto também consulta, em modo somente leitura, os bancos Firestore existentes (quando a API está habilitada) e o estado de faturamento do projeto. Se a consulta de faturamento falhar, o estado fica desconhecido e o fluxo de criação permanece bloqueado.

A criação do banco padrão é preparada somente quando a API está ativa, o diagnóstico do mesmo projeto não encontra `(default)`, o faturamento está confirmado como desabilitado e o usuário escolhe uma região. O comando verifica novamente faturamento, API e bancos existentes antes de criar. Ele cria apenas `(default)`, em edição Standard, com proteção contra exclusão. A execução é manual no Cloud Shell. Uma pendência só é reconciliada quando um novo diagnóstico do mesmo Project ID encontra o banco. Não repetir comandos após resultados ambíguos.

A localização é uma decisão de provisionamento e não pode ser alterada depois. A localização do banco padrão também pode depender da localização padrão de recursos Google Cloud previamente definida no projeto. O GEIVIA apresenta São Paulo (`southamerica-east1`), Iowa (`us-central1`) e multirregião dos Estados Unidos (`nam5`) como opções iniciais; a compatibilidade deve ser verificada antes da criação.

Para autenticação, o GEIVIA orienta a configuração dos provedores do Firebase Authentication básico pelo Console Firebase. O plano Spark inclui a maioria dos métodos de Authentication sem custo; telefone/SMS exige faturamento. O upgrade opcional para Identity Platform é separado e não deve ser feito no modo custo zero. Habilitar `identitytoolkit.googleapis.com` isoladamente não ativa provedores de login nem conclui a configuração de Authentication.
