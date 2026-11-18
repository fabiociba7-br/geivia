# GEIVIA

**Create · Connect · Deploy**

Ambiente Android de desenvolvimento visual, inspirado no FlutterFlow, para montar telas nativas, organizar dados e lógica, integrar serviços, preparar projetos e publicar aplicativos. O fluxo de Google Cloud/Firebase continua disponível como parte do ambiente.

## Objetivo

O GEIVIA é um ambiente de desenvolvimento Android open source, independente do Zupi Gestão.

## Licença

GEIVIA é software livre licenciado sob a **GNU General Public License v3.0 (GPL-3.0)**.

Copyright (C) 2026 Fábio Rodrigo Guimarães.

Você pode usar, estudar, modificar e redistribuir o código, desde que mantenha a mesma licença e disponibilize o código-fonte das versões distribuídas.

Veja o arquivo [LICENSE](LICENSE) para o texto da licença.

## Escopo do produto

- Editor visual mobile-first com canvas, menu lateral, árvore de componentes, propriedades e prévia.
- Componentes reutilizáveis, páginas, tema, assets, variáveis e ações declarativas.
- Geração de projeto Android nativo em Kotlin/Jetpack Compose e exportação ZIP.
- Terminal local restrito e ambiente Debian ARM64/PRoot.
- Integrações com Google Cloud, Firebase e GitHub.
- Importação de projetos, preparação de deploy, diagnóstico e recuperação.

## Fluxos de serviços e infraestrutura

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

Os workflows de GitHub Actions permanecem manuais para evitar consumo automático de minutos. Testes, validações e geração de APK são executados somente quando solicitados.

## Terminal integrado

O GEIVIA oferece um terminal local restrito no painel e um terminal de recuperação interativo dentro do Debian ARM64/PRoot, acessível pela tela de diagnóstico e pelos detalhes técnicos quando a configuração inicial falha. O ambiente Linux e as ferramentas são instalados no armazenamento privado do app, sem Termux e sem root Android. A instalação das ferramentas mostra etapas, checkpoints e retomada após falhas. A validação final ainda depende de testes em aparelho físico. Consulte [a arquitetura e os critérios de aceite](docs/GEIVIA_TERMINAL.md).
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

O projeto usa Kotlin, Jetpack Compose e Gradle. Não há builds nem validações automáticas.

### Build local (opcional)
Com JDK 17 e Android SDK configurados:

```bash
gradle assembleDebug
```

A compilação final será realizada manualmente somente depois da revisão conjunta do projeto.

## Incorporação do Code on the Go

O GEIVIA inclui uma ponte opcional para abrir o projeto ativo no **Code on the Go**, IDE Android open source da App Dev for All. A integração é gradual; o aplicativo não incorpora toda a IDE nem garante que o deep link seja aceito por todas as versões instaladas.

- Repositório: https://github.com/appdevforall/CodeOnTheGo
- Avisos e status: [third_party/code-on-the-go/NOTICE.md](third_party/code-on-the-go/NOTICE.md)
