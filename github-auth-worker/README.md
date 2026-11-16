# Serviço de autorização GitHub do GEIVIA

Este Worker mantém o Client Secret fora do APK e do repositório. Ele não armazena tokens: troca o código de autorização e devolve a resposta diretamente ao aplicativo.

## Pré-requisitos

- Conta GitHub do proprietário do GEIVIA.
- Conta Cloudflare própria, com acesso ao Workers.
- Node.js instalado no computador e acesso ao terminal.
- Não é necessário informar senhas, tokens ou segredos ao assistente.

## 1. Publicar primeiro o Worker

A publicação inicial cria a URL HTTPS que será usada como callback da GitHub App.

Na pasta `github-auth-worker`:

```bash
npm test
npm install --global wrangler
wrangler login
wrangler deploy
```

O Worker terá o nome `geivia-github-auth`. Anote a URL exibida pelo Wrangler, normalmente:

```text
https://geivia-github-auth.<sua-conta>.workers.dev
```

Teste a rota de saúde:

```text
https://geivia-github-auth.<sua-conta>.workers.dev/health
```

A resposta deve indicar `status: ok`. A rota de saúde não confirma a configuração OAuth; ela apenas confirma que o Worker está publicado e respondendo.

## 2. Criar a GitHub App

No GitHub, abra **Settings → Developer settings → GitHub Apps → New GitHub App**.

Configure:
- Nome: GEIVIA (ou outro nome disponível).
- Homepage URL: página do projeto GEIVIA.
- **Callback URL**: URL HTTPS do Worker terminada em `/callback`.
- Não marque **Request user authorization (OAuth) during installation**: o GEIVIA inicia o fluxo web OAuth manualmente e informa explicitamente o `redirect_uri`.
- Mantenha habilitada a expiração de tokens de usuário (User-to-server token expiration), para que o GEIVIA possa renovar tokens de forma segura.
- Permissões de repositório: **Metadata: Read-only**, **Contents: Read-only**, **Actions: Read-only**, **Pull requests: Read-only**.
- Não habilite permissões de escrita, administração, secrets ou workflows.
- Webhooks: desativados nesta primeira fase.

Instale a App somente nos repositórios que deseja disponibilizar ao GEIVIA.

Guarde o **Client ID**. Gere um **Client Secret** para configurar como segredo do Worker. Nunca coloque o Client Secret no APK, neste repositório, em prints ou no chat.

## 3. Configurar os dados secretos no Worker

No Cloudflare Dashboard, abra **Workers & Pages → geivia-github-auth → Settings → Variables and Secrets**.

Configure:
- `GITHUB_CLIENT_ID`: Client ID da GitHub App (variável).
- `GITHUB_CALLBACK_URL`: URL HTTPS completa terminada em `/callback`, exatamente igual à cadastrada na GitHub App (variável).
- `GITHUB_CLIENT_SECRET`: Client Secret da GitHub App, criado como **Secret**, nunca como variável pública.

Também é possível cadastrar o segredo pelo terminal, dentro da pasta `github-auth-worker`:

```bash
npx wrangler secret put GITHUB_CLIENT_SECRET
```

Depois de configurar as variáveis e o segredo, publique novamente:

```bash
wrangler deploy
```

Teste novamente `/health`. Não envie o Client Secret ao GEIVIA nem a terceiros.

## 4. Configurar o aplicativo

Na tela GitHub do GEIVIA:
- **Client ID**: valor público da GitHub App.
- **URL HTTPS de callback**: `https://geivia-github-auth.<sua-conta>.workers.dev/callback`.
- **URL HTTPS do serviço**: `https://geivia-github-auth.<sua-conta>.workers.dev/exchange`.

Substitua `<sua-conta>` pelo identificador real da URL fornecida pelo Wrangler. As duas URLs precisam usar o mesmo domínio. Salve e toque em **Autorizar no GitHub**.

O app usa Authorization Code + PKCE S256 e valida state e validade de 10 minutos. O código volta por deep link; o código é trocado pelo Worker e o token é criptografado no Android Keystore. O Worker não persiste nem registra tokens. O endpoint `/refresh` renova tokens expirando e devolve o refresh token rotacionado para armazenamento cifrado no aparelho.

## 5. Validar a conexão real

Depois de concluir os passos anteriores:
1. Autorize a conta GitHub pelo próprio GEIVIA.
2. Confirme que o perfil conectado corresponde à sua conta.
3. Verifique se aparecem somente os repositórios permitidos na instalação da GitHub App.
4. Abra um repositório e confira branches, commits, pull requests e execuções do Actions.
5. Confirme que a desconexão remove a sessão local e que uma nova autorização funciona.

Se ocorrer erro, registre apenas a mensagem não sensível e a etapa em que ocorreu. Nunca compartilhe Client Secret, tokens, códigos OAuth, cookies ou arquivos de credenciais.

## Limitações da primeira entrega

- A lista percorre até 10 páginas de 100 repositórios (limite operacional de 1.000 por atualização).
- O GEIVIA mostra branches, até 20 commits, 15 execuções recentes do Actions, jobs da execução selecionada, metadados dos artefatos e até 20 pull requests abertos por repositório. O download de artefatos ainda é feito no site do GitHub.
- Desconectar no GEIVIA apaga o token local e a chave Android Keystore. Para revogação completa, remova a autorização/instalação nas configurações GitHub.
- Se a autorização for revogada, o GitHub retorna 401/403 e o usuário deverá reconectar ou corrigir as permissões.
- Tokens de usuário GitHub App expiram após 8 horas; o GEIVIA renova automaticamente pelo endpoint `/refresh` e salva a nova resposta cifrada. O refresh token também expira (atualmente após 6 meses); se a renovação for recusada, reconecte a conta.

## Segurança

- Não solicite senha ou Personal Access Token.
- Não cole Client Secret em campos do Android.
- Não faça commit de secrets.
- O Worker não guarda tokens.
- O aplicativo não executa commits, push, merge, deploy nem alterações no repositório.
- A primeira fase é somente leitura.
