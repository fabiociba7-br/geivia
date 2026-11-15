# Integração com GitHub no GEIVIA

## Objetivo

Conectar a conta GitHub do próprio usuário, listar os repositórios que a GitHub App pode acessar e, por etapas, acompanhar commits, pull requests e execuções do GitHub Actions. A integração não depende da conta GitHub do mantenedor do GEIVIA.

## Implementação já presente no repositório

- Tela GitHub no dashboard do Android.
- Autorização oficial GitHub App por Authorization Code + PKCE S256.
- Validação local do parâmetro `state` e prazo de 10 minutos para o retorno.
- Callback Android por `geivia://github/callback`.
- Serviço Cloudflare Worker em `github-auth-worker/src/index.js`, que guarda o Client Secret no ambiente Cloudflare e troca o código por token.
- O Worker não persiste nem registra tokens.
- O token recebido é criptografado com AES-GCM usando chave criada no Android Keystore.
- Consulta inicial de até 100 repositórios pelo endpoint `GET /user/repos`.
- Desconexão local remove o token cifrado e a chave do Android Keystore.
- CI valida sintaxe do Worker com `node --check`, executa testes Kotlin e gera APK.

A integração ainda depende de o proprietário registrar a GitHub App e publicar/configurar o Worker. Até isso ser feito, a conexão real não pode ser concluída. Consulte `github-auth-worker/README.md`.

## Arquitetura

1. O usuário registra uma GitHub App e instala a App somente nos repositórios que deseja autorizar.
2. A GitHub App habilita autorização do usuário (OAuth) e usa a URL HTTPS do Worker como callback.
3. O Android gera verifier e state aleatórios, calcula o challenge S256 e abre o navegador no GitHub.
4. O GitHub retorna um código ao Worker; o Worker redireciona apenas código e state para o deep link Android.
5. O app valida state e idade da solicitação, envia o código e verifier ao Worker.
6. O Worker troca o código no GitHub usando o Client Secret armazenado como Secret no Cloudflare.
7. O Worker devolve a resposta do token diretamente ao app sem persistir ou registrar credenciais.
8. O app cifra a resposta completa do token com Android Keystore e consulta a API do GitHub.

PKCE protege o código de autorização contra interceptação por outro aplicativo. O segredo da GitHub App nunca é distribuído no APK.

## Permissões da GitHub App

Para a primeira fase:
- Metadata: Read-only (obrigatória para consultar repositórios).
- Contents: Read-only (preparada para leitura de conteúdo na próxima etapa).
- Actions: Read-only (preparada para consultar workflows na próxima etapa).

Não habilitar escrita, administração, secrets, variables, deployments ou alteração de workflows. O usuário deve instalar a App somente nos repositórios selecionados, nunca em todos por padrão.

## Limitações atuais

- A tela lista até 100 repositórios por consulta; paginação ainda não foi implementada.
- Commits, pull requests, Actions, jobs e artefatos ainda não têm telas próprias.
- A primeira fase não altera arquivos, branches, workflows, configurações ou conteúdo dos repositórios.
- A renovação automática de tokens de usuário expirados ainda não foi implementada. Não habilite a opção de expiração de tokens de usuário na GitHub App até essa funcionalidade existir; se o token for revogado ou expirar, será necessário reconectar.
- O Worker usa endpoints públicos e não mantém banco de dados. O plano gratuito da Cloudflare pode atender o uso pessoal inicial, sujeito aos limites e termos vigentes.
- O usuário precisa criar a GitHub App, publicar o Worker e preencher Client ID e URLs HTTPS na tela do GEIVIA.

## Segurança obrigatória

- Nunca solicitar senha ou Personal Access Token.
- Nunca incluir Client Secret ou chave privada no APK, Git ou logs.
- Nunca copiar tokens para o backup do histórico do GEIVIA.
- Nunca registrar tokens ou códigos de autorização nos logs do Worker.
- Exigir HTTPS e mesmo domínio para callback e endpoint de troca.
- Tratar 401 como autorização expirada/revogada e 403 como permissão insuficiente/limite.
- Desconectar apaga as credenciais locais; a revogação completa é feita nas configurações GitHub pelo usuário.
- Não executar commits, push, merge, deploy ou alterações sem consentimento explícito e revisão do diff.

## Próximas entregas

1. Completar testes de segurança do fluxo OAuth e validação do Worker.
2. Adicionar paginação de repositórios.
3. Exibir perfil conectado, branches e commits recentes.
4. Exibir workflows, jobs, status e links de artefatos.
5. Exibir pull requests e detalhes de alterações.
6. Implementar renovação segura de tokens antes de habilitar expiração.
7. Só então avaliar criação de branches e pull requests. Escrita deverá continuar desativada por padrão e usar PR em vez de push direto em `main`.

## Referências oficiais

- https://docs.github.com/en/apps/creating-github-apps/about-creating-github-apps/deciding-when-to-build-a-github-app
- https://docs.github.com/en/apps/creating-github-apps/authenticating-with-a-github-app/authenticating-with-a-github-app-on-behalf-of-a-user
- https://docs.github.com/en/apps/creating-github-apps/authenticating-with-a-github-app/generating-a-user-access-token-for-a-github-app
- https://docs.github.com/en/apps/creating-github-apps/registering-a-github-app/choosing-permissions-for-a-github-app
- https://docs.github.com/en/apps/creating-github-apps/about-creating-github-apps/best-practices-for-creating-a-github-app
- https://github.blog/changelog/2025-07-14-pkce-support-for-oauth-and-github-app-authentication/
