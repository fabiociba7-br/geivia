# Configuração OAuth do GEIVIA

## Decisão atual

O GEIVIA deve integrar com a conta Google escolhida por cada usuário, mas não pode depender de uma conta Google pessoal do desenvolvedor. O cliente OAuth pertence ao produto/publicador e é separado das contas pessoais dos usuários.

O usuário final não precisa criar projeto Google Cloud. A configuração OAuth institucional do produto, entretanto, precisa existir antes que o Google possa identificar o aplicativo e emitir tokens. Esse cadastro não é uma publicação na Play Store.

## Estado do código neste momento

O APK contém a tela de integração e o fluxo de autorização incremental baseado no Google Play services Authorization API, além das chamadas de leitura para perfil, projetos Firebase, projetos Cloud, serviços habilitados e estado de faturamento. A autorização real depende de um cliente OAuth Android registrado para o package e a SHA-1 da assinatura do APK. Até esse cadastro existir, o modo offline permanece disponível e a tela informa a pendência sem simular uma conta conectada.

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

## Distribuição fora da Play Store e estado OAuth

O status OAuth `Testing` ou `In production` é independente da publicação do APK na Google Play. O GEIVIA pode ser distribuído diretamente por APK e, ainda assim, usar OAuth em produção.

- Em `Testing`, usuários que precisem dos escopos adicionais devem estar na lista de testadores (até 100). Autorizações de teste para escopos além de identidade básica têm tokens de atualização com validade limitada, normalmente 7 dias.
- A exceção de identidade básica (`openid`, `email`, `profile`) não exige lista de testadores.
- Em `In production`, o acesso pode ser oferecido a contas Google em geral. Porém, escopos sensíveis/restritos não aprovados podem gerar avisos e limite de 100 usuários no total. Para uso público sem esse limite, submeter marca e escopos exigidos à verificação Google.
- `In production` não significa publicar o APK na Play Store.

A política do Google recomenda projetos OAuth separados para desenvolvimento/teste e produção.

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
