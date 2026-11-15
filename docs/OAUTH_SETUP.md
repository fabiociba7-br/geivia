# Decisão de arquitetura: GEIVIA sem conta pessoal do desenvolvedor

## Decisão confirmada

O GEIVIA não será vinculado a nenhuma conta Google pessoal do desenvolvedor. Não será criado um projeto Google Cloud pessoal para viabilizar o aplicativo, e credenciais, tokens, chaves ou projetos pessoais do desenvolvedor não serão utilizados.

## Estado atual do aplicativo

- Não há fluxo de login ou autorização Google.
- Não há solicitação de escopos OAuth.
- Não há chamadas à Firebase Management API.
- Não há permissão de acesso à internet declarada no AndroidManifest.
- A seleção antiga de projeto Firebase armazenada localmente é apagada ao abrir esta versão.
- O questionário, a análise e as preferências de custo funcionam localmente no aparelho.
- Nenhuma conta Google é solicitada ou vinculada.

## Limite funcional desta versão

Sem uma integração autorizada com a conta do próprio usuário, o aplicativo não pode listar, criar ou alterar projetos Firebase, ativar APIs ou publicar Hosting em nome dele. Essas operações ficam indisponíveis nesta versão independente.

## Possível integração futura

Se futuramente houver integração direta com Firebase, ela deverá usar uma identidade OAuth institucional do produto, independente de contas pessoais do desenvolvedor e dos projetos dos clientes. Essa identidade e sua política de privacidade precisam ser definidas antes de implementar a conexão.

Qualquer autorização será feita pelo próprio usuário na tela oficial do Google, com escopos mínimos e explicação clara das operações. Nunca solicitar senha Google dentro do GEIVIA.

## Custos e segurança

- Não criar ou associar faturamento automaticamente.
- O modo de custo zero deve bloquear operações que exijam faturamento.
- Não incluir service account, chaves privadas, tokens ou segredos no APK ou no repositório.
- Não usar contas pessoais do desenvolvedor para operar projetos dos clientes.
