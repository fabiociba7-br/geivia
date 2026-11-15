# GEIVIA

**Create · Connect · Deploy**

Aplicativo Android para ajudar pessoas sem conhecimento técnico a criar, configurar e publicar projetos Firebase.

## Objetivo

O GEIVIA será um produto independente do Zupi Gestão. Nesta primeira fase, o desenvolvimento é exclusivo para Android e todo o código, testes e builds serão mantidos neste repositório privado.

## Escopo planejado

- Preparar um ambiente local exclusivo por usuário e acessar o Google Cloud pelo navegador e Cloud Shell da própria conta.
- Importar inventários de projetos e diagnósticos gerados no Cloud Shell, sem transferir tokens ou senhas.
- Criar e selecionar projetos Google Cloud/Firebase.
- Diagnosticar APIs e preparar comandos individuais de habilitação, executados manualmente pelo usuário no Cloud Shell.
- Orientar a configuração do Firebase Authentication e preparar a criação segura do Cloud Firestore.
- Preparar comandos para configurar e publicar Firebase Hosting.
- Preparar a publicação de regras Firestore restritivas e orientar sua validação.
- Exibir diagnóstico claro, progresso por etapa e instruções de recuperação.

## Estado atual do produto

O aplicativo está em desenvolvimento. O fluxo atual usa o navegador e o Cloud Shell autenticados pelo próprio usuário: o GEIVIA prepara comandos, registra o progresso local e importa diagnósticos JSON. **O aplicativo ainda não executa operações de escrita diretamente no Google Cloud.** Cada alteração remota depende da revisão e execução manual do comando pelo usuário, seguida de novo diagnóstico quando aplicável.

A geração de APK e os testes automatizados são realizados pelo GitHub Actions. A validação real de serviços Firebase depende de um projeto Google Cloud/Firebase e da conta do usuário; não é simulada pelos testes locais.

## Integração com GitHub

A primeira fase da integração já está implementada no Android: GitHub App Authorization Code + PKCE S256, token cifrado com Android Keystore e renovação automática, listagem paginada de repositórios, perfil, branches, commits, pull requests, GitHub Actions, jobs e metadados de artefatos. A conexão é somente leitura; o GEIVIA não faz push, commit, merge ou alteração de workflows.

O proprietário ainda precisa registrar a própria GitHub App e publicar/configurar o Cloudflare Worker que protege o Client Secret. O passo a passo está em [github-auth-worker/README.md](github-auth-worker/README.md), e a arquitetura completa em [docs/GITHUB_INTEGRATION_ARCHITECTURE.md](docs/GITHUB_INTEGRATION_ARCHITECTURE.md). Não envie Client Secret, tokens ou senhas ao chat nem os coloque no APK ou no repositório.
## Segurança

- Segredos de servidor nunca serão incluídos no APK ou no repositório.
- O fluxo inicial usa o navegador e Cloud Shell da própria conta do usuário; não coleta senha, token ou chave.
- O fluxo inicial não exige OAuth próprio do GEIVIA: o usuário executa comandos de leitura na sessão autenticada do Cloud Shell.
- Consulte [a arquitetura de acesso ao Google Cloud](docs/GOOGLE_AUTHORIZATION_ARCHITECTURE.md) e a [política contra projetos duplicados](docs/PROJECT_CREATION_SAFETY.md).
- Operações administrativas exigem confirmação explícita, registro de etapas e recuperação segura no mesmo projeto.
- Nunca solicitar senha da conta Google ao usuário dentro do GEIVIA.

## Desenvolvimento

O projeto usa Kotlin, Jetpack Compose e Gradle. Os builds Android são executados pelo GitHub Actions.

### Build local (opcional)
Com JDK 17 e Android SDK configurados:

```bash
gradle assembleDebug
```

O usuário final não precisa instalar Android Studio: os APKs de teste serão gerados pelo GitHub Actions.
