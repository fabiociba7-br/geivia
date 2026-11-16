# GEIVIA Terminal — experiência integrada e motor Linux

## Dois terminais com funções diferentes

O GEIVIA mantém duas experiências complementares:

1. **Terminal do painel:** interpretador local restrito, com comandos seguros para manipular arquivos dentro da área privada de trabalho. Não executa shell arbitrário.
2. **Terminal de recuperação:** disponível na tela **Diagnóstico e solução de problemas**. Abre uma sessão interativa do Debian ARM64 pelo motor PRoot integrado ao aplicativo. É destinado à manutenção do ambiente Linux e à recuperação de instalações.

O usuário não precisa instalar nem abrir Termux. O terminal de recuperação usa o armazenamento privado do GEIVIA e não é um terminal root do Android.

## Runtime Linux atual

O módulo `:proot-engine` integra o motor PRoot Android do submódulo `oonid/pr`. O `GeiviaProotHost` prepara os diretórios privados em `files/linux/`, valida os componentes nativos ARM64 extraídos pelo Android e cria os links executáveis usados pelo `pr-cli`.

O `LinuxRuntimeController` oferece:
- execução de comandos Debian com saída incremental e timeout;
- abertura de sessão interativa por PTY;
- envio de comandos para a sessão;
- encerramento da sessão ativa;
- instalação e verificação básica do Debian.

O runtime exige Android compatível e ABI `arm64-v8a`. A disponibilidade real depende também do espaço livre, dos componentes nativos presentes no APK e do funcionamento do PRoot no aparelho.

## Instalação recuperável das ferramentas

A configuração inicial exibe o Debian e cada etapa da toolchain com estado individual:
- Aguardando;
- Em andamento;
- Concluída;
- Falhou.

A instalação é dividida em recuperação do dpkg, atualização dos índices, pacotes básicos, configuração do npm, Firebase CLI e verificação final. Cada etapa concluída recebe um checkpoint persistido no aparelho. Ao tentar novamente, o GEIVIA pula etapas concluídas e retoma a primeira etapa pendente ou com falha. A instalação parcialmente executada pode ser repetida.

O terminal de recuperação permite ao usuário executar comandos Debian para investigar problemas sem sair do GEIVIA. Os comandos podem alterar pacotes e arquivos dentro do Debian; a interface avisa que não se trata de acesso root ao Android.

## Segurança e limites

- Não solicita root Android nem tenta contornar o sandbox do sistema.
- Não acessa dados privados de outros aplicativos.
- Não embute tokens, senhas ou chaves privadas.
- O terminal de recuperação executa comandos no Debian/PRoot do próprio aplicativo; portanto, comandos digitados pelo usuário podem modificar esse ambiente.
- O terminal local restrito do painel continua limitado ao workspace privado.
- Operações no Google Cloud/Firebase seguem o fluxo de autenticação e confirmação descrito na documentação de integração; a instalação local de ferramentas não significa que um deploy esteja autorizado.
- Os dados guardados exclusivamente no diretório privado do app podem ser apagados se o GEIVIA for desinstalado ou tiver seus dados limpos. Faça backup dos projetos antes disso.

## Validação ainda necessária em aparelho físico

A compilação e os testes JVM não comprovam que a distribuição Debian inicia corretamente em todos os aparelhos. Antes de considerar o runtime pronto para produção, validar em dispositivo ARM64 Android 9+ (idealmente Android 16/HyperOS):
1. inicialização do PRoot e instalação do Debian;
2. rede, DNS e execução de `apt-get update`;
3. instalação de pacotes e recuperação de uma transação dpkg interrompida;
4. entrada/saída interativa, teclado e encerramento do PTY;
5. cancelamento durante instalação e durante um comando;
6. fechamento e reabertura da tela, preservando os checkpoints;
7. atualização do APK por cima da versão instalada, sem apagar os dados do ambiente.

O CI não executa o Debian ARM64 em aparelho real. O teste instrumentado de instalação, quando acionado manualmente, faz download real da distribuição e exige rede e espaço disponíveis.
