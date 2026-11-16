# GEIVIA Terminal — fase 1

## Estado

A primeira versão do Terminal GEIVIA é um interpretador local restrito, integrado à interface Android. Ele cria e utiliza somente a pasta privada `filesDir/terminal-workspace` do aplicativo.

## Comandos disponíveis

`help`, `pwd`, `ls`, `mkdir`, `touch`, `cat`, `echo`, `date`, `whoami`, `version` e `clear`.

O comando `cat` limita a leitura a 64 KB. A entrada de cada comando é limitada a 2.048 caracteres. Caminhos são canonicalizados e qualquer tentativa de sair da pasta de trabalho é rejeitada.

## Limites de segurança

- Não invoca `/system/bin/sh`, `Runtime.exec` ou `ProcessBuilder`.
- Não executa comandos arbitrários nem scripts.
- Não acessa arquivos privados de outros aplicativos.
- Não recebe tokens, senhas ou chaves.
- Não executa ações no Google Cloud ou GitHub.
- A pasta de trabalho é privada do aplicativo e removida quando o app é desinstalado ou seus dados são apagados.

Esta versão é um primeiro terminal de comandos internos, não uma distribuição Linux e não substitui Termux. A integração Termux será uma fase separada, opcional e sujeita à autorização explícita do usuário. A execução remota também será isolada em um módulo próprio, com autenticação e confirmação antes de qualquer operação.

## Próximas fases

1. Integração opcional com Termux por interface oficial de comandos, verificando instalação e permissões.
2. Sessões e histórico persistentes, com cancelamento e limites de recursos.
3. Adaptador remoto independente para Cloud Shell/ambiente Linux, sem armazenar credenciais no APK.
4. Integração de tarefas GEIVIA com pré-visualização, confirmação e relatório de resultado.
