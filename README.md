# GEIVIA

**Create · Connect · Deploy**

Aplicativo Android para ajudar pessoas sem conhecimento técnico a criar, configurar e publicar projetos Firebase.

## Objetivo

O GEIVIA será um produto independente do Zupi Gestão. Nesta primeira fase, o desenvolvimento é exclusivo para Android e todo o código, testes e builds serão mantidos neste repositório privado.

## Escopo planejado

- Conectar uma conta Google com autorização explícita.
- Criar e selecionar projetos Google Cloud/Firebase.
- Orientar e automatizar a habilitação das APIs necessárias.
- Configurar Firebase Authentication e Cloud Firestore.
- Preparar e publicar Firebase Hosting.
- Implantar e validar regras do Firestore.
- Exibir diagnóstico claro, progresso por etapa e instruções de recuperação.

## Segurança

- Segredos de servidor nunca serão incluídos no APK ou no repositório.
- O app usará OAuth com escopos mínimos e consentimento explícito.
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
