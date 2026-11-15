# Integração com GitHub no GEIVIA

## Objetivo

Conectar a conta GitHub do próprio usuário, listar os repositórios autorizados e acompanhar commits, pull requests e execuções do GitHub Actions. A integração não depende da conta GitHub do mantenedor do GEIVIA.

## Implementação no repositório

- Tela GitHub acessível pelo dashboard Android.
- Authorization Code + PKCE S256, validação de `state` e expiração da solicitação em 10 minutos.
- Callback Android `geivia://github/callback`.
- Cloudflare Worker em `github-auth-worker/src/index.js` guarda o Client Secret como segredo e troca o código por token.
- Worker não persiste nem registra tokens.
- Token e dados temporários PKCE criptografados com AES-GCM e chave do Android Keystore.
- Consulta de até 100 repositórios via `GET /user/repos`.
- Detalhes por repositório: até 20 commits, 15 execuções recentes do Actions e 20 pull requests abertos.
- Desconexão local remove token, dados PKCE e chave do Android Keystore.
- CI executa testes Node do Worker, testes Kotlin, build APK e upload do artefato.

A conexão real depende do proprietário registrar a GitHub App e publicar/configurar o Worker. Consulte `github-auth-worker/README.md`.

## Arquitetura

1. O usuário registra uma GitHub App e instala a App somente nos repositórios que deseja autorizar.
2. A GitHub App habilita autorização do usuário (OAuth) e usa a URL HTTPS do Worker como callback.
3. O Android gera verifier e state aleatórios, calcula o challenge S256 e abre o navegador no GitHub.
4. O GitHub retorna um código ao Worker; o Worker redireciona apenas código e state para o deep link Android.
5. O app valida state e idade da solicitação e envia o código e verifier ao Worker.
6. O Worker troca o código no GitHub usando o Client Secret armazenado como Secret no Cloudflare.
7. O Worker devolve a resposta do token diretamente ao app sem persistir ou registrar credenciais.
8. O app cifra a resposta do token com Android Keystore e consulta a API do GitHub.

PKCE protege o código de autorização contra interceptação por outro aplicativo. O segredo da GitHub App nunca é distribuído no APK.

## Permissões da GitHub App

Configure somente:
- Metadata: Read-only (listar repositórios).
- Contents: Read-only (consultar commits e conteúdo).
- Actions: Read-only (consultar execuções).
- Pull requests: Read-only (consultar PRs abertos).

Não habilite escrita, administração, secrets, variables, deployments ou alteração de workflows. Instale a App somente nos repositórios selecionados.

## Limitações atuais

- Lista até 100 repositórios; paginação ainda não foi implementada.
- Exibe commits, pull requests abertos e execuções recentes; jobs individuais, artefatos, perfil e branches ainda não têm telas próprias.
- Não altera arquivos, branches, workflows, configurações ou conteúdo dos repositórios.
- Renovação automática de tokens expirados ainda não foi implementada. Não habilite expiração opcional de tokens de usuário até essa funcionalidade existir.
- O Worker não mantém banco de dados. O plano gratuito da Cloudflare pode atender o uso pessoal inicial, sujeito aos limites e termos vigentes.
- O proprietário precisa criar a GitHub App, publicar o Worker e preencher Client ID e URLs HTTPS no GEIVIA.

## Segurança obrigatória

- Nunca solicitar senha ou Personal Access Token.
- Nunca incluir Client Secret ou chave privada no APK, Git ou logs.
- Nunca copiar tokens para o backup do histórico do GEIVIA.
- Nunca registrar tokens ou códigos de autorização nos logs do Worker.
- Exigir HTTPS e mesmo domínio para callback e endpoint de troca.
- Tratar 401 como autorização expirada/revogada e 403 como permissão insuficiente/limite.
- Desconectar apaga credenciais locais; revogação completa é feita nas configurações GitHub pelo usuário.
- Não executar commits, push, merge, deploy ou alterações sem consentimento explícito e revisão do diff.

## Próximas entregas

1. Validar fluxo completo com uma GitHub App real e Worker publicado.
2. Adicionar paginação de repositórios e perfil conectado.
3. Exibir branches, jobs individuais e artefatos.
4. Implementar renovação segura de tokens antes de habilitar expiração.
5. Só então avaliar criação de branches e pull requests. Escrita deverá continuar desativada por padrão e usar PR em vez de push direto em `main`.

## Referências oficiais

- https://docs.github.com/en/apps/creating-github-apps/about-creating-github-apps/deciding-when-to-build-a-github-app
- https://docs.github.com/en/apps/creating-github-apps/authenticating-with-a-github-app/authenticating-with-a-github-app-on-behalf-of-a-user
- https://docs.github.com/en/apps/creating-github-apps/authenticating-with-a-github-app/generating-a-user-access-token-for-a-github-app
- https://docs.github.com/en/apps/creating-github-apps/registering-a-github-app/choosing-permissions-for-a-github-app
- https://docs.github.com/en/apps/creating-github-apps/about-creating-github-apps/best-practices-for-creating-a-github-app
- https://github.blog/changelog/2025-07-14-pkce-support-for-oauth-and-github-app-authentication/
