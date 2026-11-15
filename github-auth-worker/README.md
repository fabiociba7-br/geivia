# Serviço de autorização GitHub do GEIVIA

Este Worker mantém o Client Secret fora do APK e do repositório. Ele não armazena tokens: troca o código de autorização e devolve a resposta diretamente ao aplicativo.

## 1. Criar a GitHub App

No GitHub, abra **Settings → Developer settings → GitHub Apps → New GitHub App**.

Configure:
- Nome: GEIVIA (ou outro nome disponível).
- Homepage URL: página do projeto GEIVIA.
- Callback URL: URL HTTPS do Worker terminada em `/callback`.
- Configure o campo **Callback URL** com a URL HTTPS do Worker.\n- Não marque **Request user authorization (OAuth) during installation**: o GEIVIA inicia o fluxo web OAuth manualmente e informa explicitamente o `redirect_uri` para esse callback.
- Permissões de repositório: **Metadata: Read-only**, **Contents: Read-only**, **Actions: Read-only**, **Pull requests: Read-only**.
- Não habilite permissões de escrita, administração, secrets ou workflows.
- Webhooks: desativados nesta primeira fase.
- Instale a App somente nos repositórios que deseja disponibilizar ao GEIVIA.

Guarde o **Client ID**. Gere um Client Secret para configurar como segredo do Worker. Nunca coloque o Client Secret no APK, neste repositório, em prints ou no chat.

## 2. Publicar o Worker

É necessário ter Node.js e Wrangler instalados e uma conta Cloudflare. O plano gratuito do Workers pode ser suficiente para o tráfego pessoal inicial, sujeito aos limites vigentes da Cloudflare.

Na pasta `github-auth-worker`:

```bash
npm test
npm install --global wrangler
wrangler login
wrangler deploy
```

Depois configure as variáveis no Worker (Cloudflare Dashboard → Workers & Pages → geivia-github-auth → Settings → Variables and Secrets):
- `GITHUB_CLIENT_ID`: Client ID da GitHub App.
- `GITHUB_CALLBACK_URL`: URL HTTPS completa terminada em `/callback`, por exemplo `https://SEU-WORKER.workers.dev/callback`.
- `GITHUB_CLIENT_SECRET`: crie como **Secret**, nunca como variável pública.

A URL do callback cadastrada na GitHub App deve ser exatamente igual a `GITHUB_CALLBACK_URL`.

Teste `https://SEU-WORKER.workers.dev/health`. A resposta deve indicar `status: ok`.

## 3. Configurar o aplicativo

Na tela GitHub do GEIVIA:
- Client ID: valor público da GitHub App.
- URL HTTPS de callback: `https://SEU-WORKER.workers.dev/callback`.
- URL HTTPS do serviço: `https://SEU-WORKER.workers.dev/exchange`.

As duas URLs precisam usar o mesmo domínio. Salve e toque em **Autorizar no GitHub**.

O app usa Authorization Code + PKCE S256 e valida state e validade de 10 minutos. O código volta por deep link; o código é trocado pelo Worker e o token é criptografado no Android Keystore. O Worker não persiste nem registra tokens.

## 4. Limitações da primeira entrega

- A lista percorre até 10 páginas de 100 repositórios (limite operacional de 1.000 por atualização).
- O GEIVIA mostra branches, até 20 commits, 15 execuções recentes do Actions, jobs da execução selecionada, metadados dos artefatos e até 20 pull requests abertos por repositório. O download de artefatos ainda é feito no site do GitHub.
- Desconectar no GEIVIA apaga o token local e a chave Android Keystore. Para revogação completa, remova a autorização/instalação nas configurações GitHub.
- Se a autorização for revogada, o GitHub retorna 401/403 e o usuário deverá reconectar ou corrigir as permissões.
- Tokens de usuário GitHub App expiram por padrão após 8 horas. O GEIVIA renova automaticamente pelo endpoint `/refresh` e salva a nova resposta cifrada. O refresh token também expira (atualmente após 6 meses); se a renovação for recusada, reconecte a conta.

## Segurança

- Não solicite senha ou Personal Access Token.
- Não cole Client Secret em campos do Android.
- Não faça commit de secrets.
- O Worker não guarda tokens.
- O aplicativo não executa commits, push, merge, deploy nem alterações no repositório.
- A primeira fase é somente leitura.
