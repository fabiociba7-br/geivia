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

## Verificação de compatibilidade do aparelho

Foi adicionada a classe `LinuxRuntimePreflight` e sua suíte unitária. Ela valida três pré-requisitos antes de uma futura instalação: Android API 28 ou superior (limitação do motor PRoot candidato), ABI `arm64-v8a` e pelo menos 2 GiB livres como limite inicial conservador. A verificação é pura e testável, retorna todos os motivos de incompatibilidade de uma vez e não inicia downloads nem processos.

O limite de 2 GiB é provisório para a primeira avaliação e deverá ser refinado com medições da rootfs e dos perfis de pacotes. O app atual mantém seu terminal local restrito em aparelhos que não passem nessa verificação; não se altera o `minSdk 26` global apenas por causa do runtime opcional.
## Estado da integração PRoot (16/11/2026)

O projeto agora contém o módulo Android `:proot-engine`, fixado no submódulo `oonid/pr` no commit `7029b1fe8fa84d64339cabe38f9c5be2ea2ae7ac`. O `GeiviaProotHost` prepara diretórios privados em `files/linux/` e cria os links locais para `libpr-cli.so` e `libproot.so`. O controlador usa o PTY nativo do motor para executar `pr-cli list`, `pr-cli install debian` e abrir a sessão `pr-cli login debian`.

O painel oferece verificação, instalação, abertura e encerramento da sessão. A operação de instalação/verificação agora pode ser cancelada pelo usuário; ao sair do painel, a sessão ativa é encerrada para evitar deixar processos órfãos. O cancelamento fecha o descritor PTY e solicita o encerramento do processo filho; a operação pode levar alguns segundos para devolver o código final.

**Validação ainda pendente em aparelho físico:** o CI confirma compilação e testes JVM, mas não executa o Debian ARM64 no Android. Antes de considerar o terminal Linux pronto, validar em pelo menos um aparelho ARM64 Android 9+ (idealmente Android 16/HyperOS): inicialização do PRoot, download e extração da rootfs, `apt update`, instalação de pacotes, rede/DNS, entrada/saída interativa, cancelamento durante download, fechamento da tela e retomada após reabrir o app.

O ambiente usa armazenamento privado do aplicativo. Desinstalar o GEIVIA pode apagar a distribuição e os projetos que estiverem somente nesse diretório; o usuário deve exportar/backupar os projetos antes de desinstalar ou limpar os dados.


## Fluxo de instalação e verificação do Debian (16/11/2026)

O botão **Instalar Debian** agora executa três etapas sequenciais e interrompe o fluxo na primeira falha:

1. `pr-cli install debian` — baixa e instala a distribuição ARM64.
2. `pr-cli test debian --suite distro` — executa a suíte base upstream sem solicitar instalação automática de toolchains.
3. `pr-cli login debian -- /bin/sh -lc <smoke-check>` — executa dentro do guest verificações de `/bin/sh`, `/root`, escrita em `/root`, `id` e arquitetura, emitindo o marcador `GEIVIA_DEBIAN_SHELL_OK`.

O controlador exige código de saída zero em cada etapa; uma falha impede que a interface informe instalação validada. Os vetores de argumentos têm testes JVM. Foi incluído também um teste instrumentado que executa as três etapas em um aparelho Android e confirma o marcador do shell. Esse teste faz download real do Debian e **não é executado pelo CI hospedado**; deve ser acionado manualmente em dispositivo ARM64 com rede e espaço disponíveis.

A validação do terminal interativo continua sendo feita separadamente ao abrir a sessão Debian e enviar comandos. O teste instrumentado de instalação não substitui a verificação de teclado, redimensionamento do PTY, persistência após reinício, cancelamento durante download ou particularidades do HyperOS.
