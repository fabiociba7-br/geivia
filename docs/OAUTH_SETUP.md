# Configuração OAuth do GEIVIA

Este procedimento configura a identidade OAuth do aplicativo GEIVIA. Faça tudo no **projeto Google Cloud de desenvolvimento do GEIVIA**, nunca no projeto Firebase de um cliente e nunca reutilizando as credenciais do Zupi.

## Identidade do aplicativo

- Nome: GEIVIA
- Android package / applicationId: `com.geivia.app`
- Escopo solicitado nesta etapa: `https://www.googleapis.com/auth/firebase.readonly`
- Operação liberada nesta etapa: listar projetos Firebase acessíveis à conta.
- Ainda não há permissão para criar projetos, ativar APIs ou alterar configurações.

## 1. Preparar o projeto Google Cloud do GEIVIA

1. Abra o Google Cloud Console e selecione o projeto de desenvolvimento do GEIVIA.
2. Em **APIs e serviços > Biblioteca**, habilite **Firebase Management API**.
3. Confirme que o projeto de desenvolvimento não é um projeto de cliente e não associe faturamento para esta configuração de leitura.

## 2. Configurar a tela de consentimento

1. Abra **Google Auth Platform**.
2. Em **Branding**, informe o nome GEIVIA e um e-mail de suporte válido.
3. Em **Audience**, mantenha o aplicativo em modo de teste enquanto estiver em desenvolvimento.
4. Adicione como usuário de teste a conta Google que será usada para testar o GEIVIA.
5. Em **Data Access**, adicione somente o escopo Firebase de leitura listado acima, se o Console solicitar a declaração manual do escopo.

Não solicite senha Google dentro do aplicativo. A autorização deve acontecer na interface oficial do Google.

## 3. Criar o cliente OAuth Android

Crie um OAuth Client ID do tipo **Android** com:

- Package name: `com.geivia.app`
- SHA-1: impressão digital do certificado que assina o APK instalado.

**Atenção à assinatura:** os APKs atuais do GitHub Actions usam a chave de depuração padrão gerada no ambiente de build. Em runners hospedados, essa chave pode mudar entre execuções. Portanto, não trate a SHA-1 de um APK de CI como identidade permanente. Antes de uma configuração OAuth estável e antes da distribuição, precisamos definir uma chave de assinatura estável e protegê-la fora do repositório. A SHA-1 deve corresponder exatamente ao APK instalado.

Não publique nem envie a chave privada de assinatura, arquivos keystore ou senhas no repositório.

## 4. Teste

Depois de concluir as etapas acima:

1. Instale o APK correspondente à SHA-1 cadastrada.
2. Abra o GEIVIA e escolha **Conectar conta Google**.
3. Autorize a leitura dos projetos.
4. Confirme se a lista de projetos aparece.

Se houver erro, registre a mensagem completa e o código HTTP, sem compartilhar tokens de acesso.

## Limites de segurança desta versão

- O escopo atual é somente leitura (`firebase.readonly`).
- O GEIVIA não cria nem altera projetos nesta fase.
- O token de acesso é mantido em memória e não é gravado nas preferências do aplicativo.
- Nenhum service account, chave privada ou segredo de servidor deve ser incluído no APK ou no Git.
- A escolha de custo zero continua sendo uma restrição de produto: não ativar faturamento ou recursos pagos automaticamente.

## Próximas permissões

A criação de projetos e a configuração de serviços exigirão análise separada dos escopos e consentimentos necessários. Não amplie os escopos do aplicativo antes de implementar telas que expliquem a operação específica e obtenham confirmação explícita do usuário.
