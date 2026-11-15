# Configuração OAuth do GEIVIA

## Decisão atual

O GEIVIA deve integrar com a conta Google escolhida por cada usuário, mas não pode depender de uma conta Google pessoal do desenvolvedor. O cliente OAuth pertence ao produto/publicador e é separado das contas pessoais dos usuários.

O usuário final não precisa criar projeto Google Cloud. A configuração OAuth institucional do produto, entretanto, precisa existir antes que o Google possa identificar o aplicativo e emitir tokens.

## Estado do código neste momento

A versão offline permanece disponível. A integração Google foi retirada temporariamente do APK enquanto a identidade OAuth institucional não estiver configurada. Não exibir login falso nem simular uma conta conectada.

## Antes de ativar OAuth real

O responsável institucional pelo produto deverá:
1. Definir a organização/publicador responsável pelo GEIVIA.
2. Manter um projeto Google Cloud de publicação/OAuth sob essa responsabilidade (não um projeto pessoal de cliente).
3. Configurar a tela de consentimento com nome, marca, domínios verificados, política de privacidade e termos.
4. Registrar o cliente OAuth Android com o package `com.geivia.app` e a impressão digital SHA-1 do certificado de assinatura de distribuição.
5. Habilitar apenas as APIs necessárias ao escopo aprovado.
6. Verificar as exigências de verificação OAuth do Google para os escopos sensíveis/restritos usados pelo produto.
7. Publicar política de privacidade que explique dados coletados, finalidade, retenção, compartilhamento, exclusão e contato.

## Autorização progressiva

Seguir a matriz e as etapas de `docs/GOOGLE_AUTHORIZATION_ARCHITECTURE.md`. Não solicitar todos os escopos no primeiro login. A conexão inicial pede apenas identidade básica. Firebase, Cloud Resource Manager, Service Usage e Cloud Billing são solicitados em momentos separados, quando o usuário iniciar a função correspondente.

## Assinatura Android

O OAuth Android vincula o cliente ao package e ao certificado SHA-1. Não usar uma chave debug efêmera do GitHub Actions para produção. Definir a assinatura de distribuição estável e protegê-la fora do repositório antes de registrar o cliente.

## Segurança

- Nunca solicitar senha Google dentro do app.
- Nunca commitar client secrets, service-account keys, tokens ou keystores.
- Nunca usar credenciais pessoais do desenvolvedor para acessar projetos de usuários.
- Nunca solicitar `cloud-platform` por conveniência.
- Não confundir OAuth scope com permissão IAM no projeto do usuário.
- Nenhum consentimento de leitura autoriza operações de escrita ou faturamento.
- Modo custo zero bloqueia operações que exigem faturamento.
