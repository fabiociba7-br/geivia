# GEIVIA — auditoria de permissões e limites do Android

Data da auditoria: 2026-11-15
Manifesto auditado: `app/src/main/AndroidManifest.xml`
SDK do app: targetSdk 35, minSdk 26.

## 1. Estado encontrado

O manifesto declara somente:

- `android.permission.INTERNET` — permissão normal, concedida na instalação, necessária para GitHub, downloads e serviços web.

Não foram encontradas declarações de permissões para armazenamento, acesso amplo a arquivos, notificações, localização, câmera, microfone, contatos, telefone, Bluetooth, instalação de APKs, inicialização automática ou execução em primeiro plano.

O callback OAuth do GitHub usa deep link `geivia://github/callback`; não exige permissão especial. A visibilidade de pacotes de outros apps também não é necessária para a arquitetura atual sem Termux.

## 2. Armazenamento, pastas e arquivos

Para rootfs, ferramentas, projetos e arquivos internos do ambiente Linux, usar diretórios privados do app:

- `filesDir`: dados persistentes e workspace;
- `noBackupFilesDir`: dados grandes ou reconstruíveis que não devem entrar no backup;
- `cacheDir`: somente downloads temporários e conteúdo descartável.

Acesso a esses diretórios não exige permissão de armazenamento. O Android isola esses arquivos de outros aplicativos. Deve-se medir espaço disponível antes de instalar e manter margem para atualização e extração.

**Não adicionar** `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE` ou `MANAGE_EXTERNAL_STORAGE` para instalar o Linux ou operar no workspace privado. A permissão de “acesso a todos os arquivos” seria excessiva e não resolve as restrições de execução de código.

Para importar/exportar arquivos escolhidos pelo usuário, usar Storage Access Framework (`ACTION_OPEN_DOCUMENT`, `ACTION_CREATE_DOCUMENT` ou `ACTION_OPEN_DOCUMENT_TREE`) com URI grants. Isso não exige permissão ampla de armazenamento. Só adicionar esse fluxo quando a interface de importação/exportação estiver implementada.

## 3. Rede e downloads

- Manter `INTERNET`.
- `ACCESS_NETWORK_STATE` é opcional; só adicionar se a interface precisar consultar explicitamente o estado da rede. Uma tentativa de conexão com tratamento de erro não exige essa permissão.
- Não declarar permissões de Wi-Fi ou alteração de rede para downloads HTTP/HTTPS comuns.

Downloads devem ocorrer em primeiro plano enquanto o usuário acompanha a instalação, ou via WorkManager quando forem adiáveis e precisarem sobreviver à saída da tela. Não criar serviço em primeiro plano sem necessidade.

Se uma futura implementação realmente usar foreground service para transferência contínua, será necessário declarar o tipo apropriado e suas permissões conforme a versão do Android e target SDK. Para target 35, revisar também os limites de execução do Android 15. Não solicitar `POST_NOTIFICATIONS` preventivamente; avaliar apenas se a experiência de notificação exigir. 

## 4. Permissões que não devem ser solicitadas para o objetivo atual

- `MANAGE_EXTERNAL_STORAGE`: não é necessária para arquivos privados do GEIVIA.
- `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_MEDIA_AUDIO`: sem uso atual de mídia compartilhada.
- `QUERY_ALL_PACKAGES`: não é necessária; não enumerar aplicativos instalados.
- `REQUEST_INSTALL_PACKAGES`: GEIVIA não instala APKs de terceiros.
- `RECEIVE_BOOT_COMPLETED`: não iniciar Linux automaticamente ao ligar o telefone.
- `WAKE_LOCK`: não adicionar por padrão; avaliar somente se testes mostrarem necessidade real de manter CPU ativa durante operação longa.
- Localização, câmera, microfone, contatos, telefone, Bluetooth e sensores: sem justificativa funcional.

## 5. Limites de sistema: não são permissões concedíveis

O ambiente Linux não ganha acesso root ao Android. PRoot simula uma visão de filesystem/usuário e não concede privilégios de kernel.

Não presumir acesso real a:
- `/system`, `/vendor`, `/data/data` de outros apps ou dados de outros usuários;
- montagem real de filesystems, namespaces, cgroups, módulos de kernel ou serviços systemd;
- comandos privilegiados que exigem UID 0 real, como operações reais de mount, iptables ou administração do sistema.

Qualquer bind de diretórios do Android deve ser mínimo, somente leitura quando possível, e limitado a diretórios explicitamente autorizados. O workspace Linux deve permanecer no sandbox do GEIVIA.

## 6. Bloqueador crítico: execução de binários baixados

A documentação oficial do Android descreve a restrição W^X: para apps que miram Android 10/API 29 ou superior, executar com `exec()` arquivos colocados no diretório gravável de home do app é bloqueado. O GEIVIA mira API 35.

Isso é diferente de uma permissão ausente: **não existe uma permissão de manifesto que simplesmente libere essa execução**. Portanto, não se deve implementar um instalador que baixa um rootfs e tenta executar diretamente `/bin/bash`, `proot` ou outros executáveis de uma pasta gravável sem provar antes uma estratégia compatível com Android.

Antes de aprovar o runtime PRoot, criar um protótipo mínimo e testá-lo em aparelho real Android 16:
1. carregar o componente nativo permitido/empacotado no APK;
2. validar a forma suportada de iniciar o motor e os processos convidados;
3. validar `ptrace`, seccomp, SELinux e ABI no aparelho;
4. executar um shell convidado e comandos simples;
5. confirmar cancelamento, encerramento e ausência de processos órfãos.

Se o candidato depender de executar binários arbitrários baixados do diretório privado, considerar a arquitetura reprovada até que exista uma solução documentada e testada. Não tentar contornar a política com permissões excessivas, root ou acesso a pastas do sistema.

## 7. Decisão de permissões

A lista mínima continua sendo somente `INTERNET` para a arquitetura atual e para downloads do runtime. Não ampliar permissões agora.

A implementação futura pode precisar de permissões normais adicionais apenas se adotar recursos específicos, como foreground service de transferência. A necessidade de executar o Linux é principalmente uma questão de compatibilidade do runtime com as políticas de execução do Android, não de conceder acesso irrestrito ao sistema de arquivos.

## Referências oficiais

- Android app-specific storage: https://developer.android.com/training/data-storage/app-specific
- Android data storage overview / Storage Access Framework: https://developer.android.com/training/data-storage
- Android dynamic code loading security: https://developer.android.com/privacy-and-security/security-best-practices
- Android 14+ foreground service types: https://developer.android.com/about/versions/14/changes/fgs-types-required
- Android package visibility: https://developer.android.com/training/package-visibility
- PRoot behavior and limitations: https://github.com/termux/proot-distro
