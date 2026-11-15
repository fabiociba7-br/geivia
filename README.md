# GEIVIA

**Create · Connect · Deploy**

Aplicativo Android para ajudar pessoas sem conhecimento técnico a criar, configurar e publicar projetos Firebase.

## Objetivo

O GEIVIA será um produto independente do Zupi Gestão. Nesta primeira fase, o desenvolvimento é exclusivo para Android e todo o código, testes e builds serão mantidos neste repositório privado.

## Escopo planejado

- Conectar a conta Google escolhida pelo usuário, com autorização incremental e escopos mínimos.
- Adaptar questionário e recomendações ao perfil, projetos, serviços e permissões que o usuário autorizar.
- Criar e selecionar projetos Google Cloud/Firebase.
- Orientar e automatizar a habilitação das APIs necessárias.
- Configurar Firebase Authentication e Cloud Firestore.
- Preparar e publicar Firebase Hosting.
- Implantar e validar regras do Firestore.
- Exibir diagnóstico claro, progresso por etapa e instruções de recuperação.

## Segurança

- Segredos de servidor nunca serão incluídos no APK ou no repositório.
- O app usará autorização OAuth incremental, contextual e granular; leitura e escrita serão separadas.
- A identidade OAuth será do produto/publicador, independente de contas pessoais do desenvolvedor e dos usuários.
- Consulte [a arquitetura de autorização Google](docs/GOOGLE_AUTHORIZATION_ARCHITECTURE.md) para a matriz de escopos e o fluxo de consentimento.
- Operações administrativas serão executadas com credenciais e arquitetura apropriadas, com registro das ações.
- Nunca solicitar senha da conta Google ao usuário dentro do GEIVIA.

## Desenvolvimento

O projeto usa Kotlin, Jetpack Compose e Gradle. Os builds Android são executados pelo GitHub Actions.

### Build local (opcional)
Com JDK 17 e Android SDK configurados:

```bash
gradle assembleDebug
```

O usuário final não precisa instalar Android Studio: os APKs de teste serão gerados pelo GitHub Actions.
