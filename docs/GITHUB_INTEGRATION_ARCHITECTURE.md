# Integração com GitHub no GEIVIA

## Objetivo

Permitir que o usuário conecte a própria conta GitHub e escolha quais repositórios o GEIVIA pode consultar para acompanhar código, commits, branches, pull requests e execuções do GitHub Actions. A conexão não deve depender da conta GitHub do desenvolvedor do GEIVIA.

## Decisão técnica

A integração deve usar uma **GitHub App**, não um Personal Access Token compartilhado e não credenciais pessoais do mantenedor.

Para autenticação em nome do usuário, a arquitetura de produção recomendada é:
1. Registrar uma GitHub App pertencente à operação do GEIVIA.
2. Habilitar autorização do usuário (OAuth) e fluxo Authorization Code com PKCE S256.
3. Usar um serviço mínimo de backend para guardar o Client Secret e realizar a troca do código por tokens. O segredo nunca pode estar no APK, no Git ou em arquivos de configuração distribuídos.
4. O usuário instala/autoriza a GitHub App e escolhe explicitamente os repositórios permitidos.
5. O aplicativo Android recebe apenas o token de usuário necessário, armazena-o com proteção baseada no Android Keystore e nunca o grava em logs, relatórios, backups de workflow ou preferências em texto puro.
6. O usuário pode desconectar a conta; a desconexão apaga os tokens locais e orienta a revogação da autorização na página oficial do GitHub.

O GitHub recomenda GitHub Apps por oferecer permissões granulares e tokens de curta duração. A documentação também recomenda PKCE S256. O Device Flow existe, mas não é a primeira escolha para o GEIVIA porque o aplicativo Android dispõe de navegador; a própria documentação alerta para riscos de phishing quando Device Flow é habilitado sem necessidade.

## Permissões mínimas por fase

### Fase 1 — somente leitura (primeira entrega)
- Repository metadata: read
- Repository contents: read
- Actions: read
- Pull requests: read, somente se a tela de PRs entrar nesta fase

Com isso, o GEIVIA pode:
- listar apenas repositórios que o usuário autorizou e consegue acessar;
- mostrar nome, descrição, branch padrão e atividade recente;
- consultar commits e arquivos sem modificá-los;
- mostrar status e histórico de GitHub Actions e links para os logs/artifacts no GitHub;
- acompanhar o build do próprio GEIVIA e de outros projetos sem iniciar execuções.

Não solicitar permissões de escrita, administração, secrets, variables, deployments ou workflows nesta fase.

### Fase 2 — alterações controladas (somente após a fase de leitura)
- Contents: write, apenas se necessário para criar uma branch e preparar alterações.
- Pull requests: write, para propor mudanças por pull request.
- Workflows: write, somente se houver requisito explícito de criar ou alterar arquivos em `.github/workflows`.

O padrão será criar uma branch e abrir um pull request. Não fazer push direto em `main`, não alterar configurações do repositório e não iniciar workflows sem confirmação específica do usuário.

## Segurança obrigatória

- Nunca solicitar ou aceitar senha do GitHub.
- Nunca pedir que o usuário cole Personal Access Tokens no chat ou em campos de texto do GEIVIA.
- Nunca incluir Client Secret, chave privada de GitHub App, token ou refresh token no APK, no repositório ou nos logs.
- Não usar permissões `repo` amplas de OAuth quando uma GitHub App com permissões granulares resolve o caso.
- Não pedir acesso a todos os repositórios por padrão; selecionar repositórios específicos.
- Não copiar tokens para o backup do histórico do GEIVIA.
- Tratar HTTP 401 como sessão expirada/revogada e 403 como falta de permissão ou limite; nenhum desses erros autoriza ações alternativas ou mudanças automáticas.
- A desconexão deve limpar credenciais locais. A revogação no GitHub continua sendo controlada pelo usuário.
- Não executar comandos, fazer commits, criar repositórios ou publicar código sem consentimento explícito e revisão do diff.

## Telas planejadas

1. **Conectar GitHub** — explica o acesso solicitado e abre a autorização oficial.
2. **Selecionar repositórios** — lista somente os repositórios disponíveis para a instalação/autorização.
3. **Painel do repositório** — branch padrão, commits recentes, estado do último workflow e links para GitHub.
4. **Histórico de builds** — workflows, jobs, conclusão e data; não baixa artefatos automaticamente.
5. **Alterações propostas** — fase futura; mostra diff, branch e pull request antes de enviar.

## Dependências e bloqueios antes de codificar OAuth

A integração não pode ser ativada de ponta a ponta até que existam:
- GitHub App registrada e configurada;
- Client ID e URLs de callback públicas;
- backend HTTPS mínimo para guardar o Client Secret e trocar o authorization code;
- política de privacidade e tela clara de permissões;
- configuração segura de armazenamento e revogação no Android.

Esses itens não podem ser simulados com um botão "Conectar". Até estarem prontos, a interface deve mostrar a integração como planejada, sem declarar conta conectada.

## Entrega incremental recomendada

1. Manter o GitHub Actions atual como CI do GEIVIA.
2. Melhorar os testes e a validação do APK no repositório.
3. Implementar o modelo local de repositórios e estados de conexão sem credenciais reais.
4. Registrar e configurar a GitHub App e o backend.
5. Implementar OAuth com PKCE, armazenamento seguro e desconexão.
6. Liberar leitura de repositórios e Actions.
7. Só depois avaliar criação de branches e pull requests.

## Referências oficiais

- https://docs.github.com/en/apps/creating-github-apps/about-creating-github-apps/deciding-when-to-build-a-github-app
- https://docs.github.com/en/apps/creating-github-apps/authenticating-with-a-github-app/authenticating-with-a-github-app-on-behalf-of-a-user
- https://docs.github.com/en/apps/creating-github-apps/authenticating-with-a-github-app/generating-a-user-access-token-for-a-github-app
- https://docs.github.com/en/apps/creating-github-apps/registering-a-github-app/choosing-permissions-for-a-github-app
- https://docs.github.com/en/apps/creating-github-apps/about-creating-github-apps/best-practices-for-creating-a-github-app
- https://github.blog/changelog/2025-07-14-pkce-support-for-oauth-and-github-app-authentication/
