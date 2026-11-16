# GEIVIA Terminal — experiência integrada e motor Linux

## Experiência atual

O Terminal GEIVIA aparece recolhido dentro do painel principal. O usuário pode expandi-lo, executar comandos locais e recolhê-lo sem navegar para outra tela ou abrir outro aplicativo. A sessão mantém o histórico enquanto a tela permanece ativa.

## Recursos locais disponíveis nesta fase

`help`, `pwd`, `ls`, `mkdir`, `touch`, `cat`, `echo`, `date`, `whoami`, `version` e `clear`.

O comando `cat` limita a leitura a 64 KB. A entrada de cada comando é limitada a 2.048 caracteres. Caminhos são canonicalizados e qualquer tentativa de sair da pasta privada do terminal é rejeitada.

## Segurança e limites atuais

- Não invoca `/system/bin/sh`, `Runtime.exec` ou `ProcessBuilder`.
- Não executa comandos arbitrários nem scripts.
- Não acessa arquivos privados de outros aplicativos.
- Não recebe tokens, senhas ou chaves.
- Não executa ações no Google Cloud ou GitHub.
- A pasta de trabalho é privada do aplicativo e removida quando o app é desinstalado ou seus dados são apagados.
- A integração externa com Termux foi removida da experiência padrão: o GEIVIA não exige nem procura esse aplicativo.

Este terminal ainda é um interpretador local restrito; não é uma distribuição Linux e não deve apresentar comandos Linux como disponíveis antes da integração real do runtime.

## Próxima arquitetura: Linux embarcado

A próxima fase deve introduzir um runtime Linux em espaço privado do GEIVIA, sem root e sem depender do Termux instalado pelo usuário. A implementação deverá ser dividida em componentes verificáveis:

1. **Runtime isolado:** avaliar e integrar uma solução PRoot compatível com Android/ABI do aparelho, executada no sandbox do próprio GEIVIA.
2. **Distribuição:** provisionar uma rootfs ARM64 mínima por download verificável, com checksum, versão e espaço em disco informado antes da instalação.
3. **Gerenciador de pacotes:** instalar ferramentas sob demanda, sem incluir uma distribuição inteira no APK.
4. **Processos e terminal:** execução assíncrona, saída incremental, cancelamento, limites de CPU/memória/tempo e proteção contra caminhos fora do workspace.
5. **Ferramentas de desenvolvimento:** Git, Node.js, Python e Firebase CLI, verificando compatibilidade e tamanho antes de habilitar cada pacote.
6. **Segurança:** nenhuma credencial embutida; operações de nuvem exigem autenticação do próprio usuário, revisão e confirmação explícita.

### Critérios de aceite do runtime

- Funcionar sem Termux e sem root em dispositivos Android suportados.
- Não solicitar permissões desnecessárias.
- Verificar integridade dos downloads antes de extrair ou executar.
- Não executar comandos como root.
- Não anunciar ferramenta instalada antes de validar sua execução.
- Permitir remover/atualizar a distribuição sem apagar os projetos do usuário.
- Ter testes de isolamento, cancelamento, limites e recuperação de instalação interrompida.

A escolha do runtime não deve ser tratada como concluída até que a compatibilidade Android, a licença, a manutenção e a origem dos binários sejam verificadas.
