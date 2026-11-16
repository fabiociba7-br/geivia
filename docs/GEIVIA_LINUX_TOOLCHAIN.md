# GEIVIA — ambiente Linux e ferramentas de desenvolvimento

## Objetivo

O GEIVIA deverá oferecer um ambiente Linux ARM64 dentro do próprio aplicativo Android, sem exigir Termux, root ou outro aplicativo de terminal. O runtime e a distribuição ficam no armazenamento privado do GEIVIA; o usuário continua vendo uma única interface integrada ao painel.

## Decisão de arquitetura

A opção técnica prioritária para prototipação é integrar um motor PRoot Android mantido como biblioteca, em vez de executar binários Linux diretamente pelo Android. A avaliação inicial identificou o projeto público [oonid/pr](https://github.com/oonid/pr), que separa o motor Android em `:proot-engine` e documenta suporte a imagens OCI, instalação de distribuições e execução sem Termux.

**A integração ainda não está concluída nem aprovada para produção.** Antes de incorporar o motor, é obrigatório validar:
- compatibilidade com o minSdk atual do GEIVIA (26), ou documentar e aprovar a elevação mínima;
- compatibilidade entre compileSdk, Android Gradle Plugin, Gradle e NDK;
- ABI ARM64 do aparelho e comportamento real em Android 16;
- licença e disponibilização das fontes correspondentes de todos os componentes nativos;
- execução, encerramento e cancelamento de processos no ciclo de vida Android;
- extração segura e integridade criptográfica da rootfs.

O motor candidato declara minSdk 28, compileSdk 37 e ABI arm64-v8a. Portanto, não deve ser conectado cegamente ao app atual: isso poderia excluir aparelhos Android 8/8.1 e exigir atualização da cadeia de build.

## Pacotes planejados

O manifesto versionado em `app/src/main/assets/linux-toolchain-manifest.json` define o perfil inicial:

| Perfil | Ferramentas |
|---|---|
| Base | Git, SSH, curl, wget, certificados, unzip, zip, tar, xz, jq, nano |
| Java | OpenJDK 17 headless |
| Node.js | Node.js e npm |
| Python | Python 3, pip e venv |
| Build | build-essential, make, CMake e pkg-config |
| Firebase | Firebase CLI via npm |
| Google Cloud | Google Cloud CLI via repositório oficial assinado, opcional e condicionado à confirmação de suporte ARM64 |

O perfil padrão será Base + Java + Node.js + Python + Build + Firebase. Google Cloud CLI será opcional, pois depende de validação de disponibilidade ARM64 e de configuração de repositório externo assinado.

## Instalação e atualização

A experiência pretendida é uma ação única **Preparar ambiente de desenvolvimento**, com:
1. verificação de arquitetura, espaço livre e conectividade;
2. instalação da distribuição no armazenamento privado;
3. configuração de repositórios assinados;
4. instalação dos perfis selecionados;
5. validação de versões reais (`git --version`, `java -version`, `node --version`, `npm --version`, `python3 --version`, `firebase --version`);
6. resumo do que foi instalado e do que falhou, com opção de repetir apenas a etapa com falha.

Downloads serão feitos sob demanda e verificados antes da extração. O APK não carregará centenas de megabytes de rootfs e pacotes. O app informará o espaço estimado e permitirá cancelar sem apagar projetos existentes.

## Firebase Hosting

Com o perfil Firebase instalado, o ambiente poderá preparar e executar `firebase` dentro do Linux. A autenticação será iniciada pelo usuário, sem credenciais embutidas no APK. O fluxo de deploy deverá:
- verificar projeto e diretório antes de publicar;
- exibir o projeto Firebase selecionado;
- mostrar o comando e solicitar confirmação antes do deploy;
- nunca criar projeto duplicado automaticamente;
- registrar o resultado e permitir abrir a URL publicada.

A instalação da CLI não significa que o deploy esteja autenticado nem que o projeto tenha billing ou APIs habilitados.

## Limites da primeira entrega

- O manifesto é a definição versionada dos pacotes; ele **não significa que os pacotes já estejam instalados**.
- O runtime Linux ainda precisa ser integrado e testado em aparelho real.
- Android SDK/NDK e builds completos de APK dentro do telefone ficam fora do perfil inicial: são grandes e exigem uma avaliação separada.
- Google Cloud CLI fica opcional até confirmar suporte ARM64 no repositório oficial.
- A instalação de ferramentas não ativa faturamento nem executa alterações remotas automaticamente.
