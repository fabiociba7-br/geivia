# Compilador Android interno do GEIVIA

## Objetivo

O GEIVIA não deve abrir o Code on the Go nem exigir que ele esteja instalado. A edição, preparação da toolchain, compilação e geração de APK devem acontecer dentro do próprio GEIVIA.

## Implementação atual

- Interface de compilação chama `GeiviaCompilerEngine`.
- O motor executa JDK, Gradle e Android SDK no Debian/PRoot privado do GEIVIA.
- O projeto é compilado com Gradle (`assembleDebug`) e o APK é localizado em `outputs/apk`.
- A toolchain é instalada pelo GEIVIA e não por intents para outro aplicativo.
- A pasta do projeto é validada antes da execução.

## Referência open source

O projeto [Code on the Go](https://github.com/appdevforall/CodeOnTheGo) é um IDE Android open source sob GNU GPL v3. Seu desenho técnico serve como referência para a evolução do editor, linguagem, integração Gradle, SDK e ferramentas de desenvolvimento móveis.

Esta etapa não copia arquivos-fonte do Code on the Go. A implementação do motor de compilação já presente no GEIVIA permanece própria e isolada. Qualquer módulo de código do Code on the Go que venha a ser incorporado deverá manter os avisos de copyright e a licença GPL-3.0, com a origem e as modificações documentadas.

## Próximas etapas técnicas

1. Consolidar o editor de código, navegação de arquivos e diagnósticos em módulos GEIVIA.
2. Integrar a descoberta de JDK, Gradle, SDK, build-tools e licenças com estado verificável.
3. Executar builds com progresso, cancelamento, logs e relatórios de erro dentro do app.
4. Gerar APK debug e, quando configurado, APK release assinado sem abrir aplicativos externos.
5. Avaliar e portar seletivamente componentes compatíveis do Code on the Go (editor/LSP/árvore de arquivos/Gradle tooling), sem trazer dependências não necessárias para o runtime.

## Limites

Não remover nem substituir o terminal Linux, o ambiente Debian/PRoot ou os fluxos Firebase/GitHub existentes. A compilação não deve executar comandos de build fora do ambiente isolado do GEIVIA.
