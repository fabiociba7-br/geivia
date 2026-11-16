# Diagnóstico, recuperação e continuidade da GEIVIA

## Objetivo

Transformar falhas encontradas durante desenvolvimento e uso em cenários cobertos pelo próprio aplicativo. O usuário final deve receber diagnóstico em linguagem simples, correção segura quando possível e instruções guiadas quando uma ação externa for indispensável. Não deve depender de Termux, ADB ou comandos manuais para tarefas comuns.

## Princípios obrigatórios

1. **Detectar antes de agir:** cada rotina deve verificar pré-condições e estado atual antes de instalar, criar, substituir ou publicar qualquer recurso.
2. **Preservar progresso:** persistir checkpoints após cada etapa confirmada. Reinício do processo, Activity ou aparelho não pode apagar etapas já verificadas.
3. **Retomar com segurança:** repetir somente operações idempotentes. Operações externas com resultado ambíguo devem entrar em estado UNKNOWN e exigir reconciliação antes de nova tentativa.
4. **Evidência, não suposição:** distinguir conclusão verificada, confirmação declarada pelo usuário e operação ainda não confirmada.
5. **Explicar sem jargão:** mostrar o que ocorreu, o impacto, o que será preservado e o próximo passo.
6. **Falha fechada:** se o estado persistido estiver corrompido ou incompleto, não presumir que uma operação nunca ocorreu.
7. **Privacidade:** relatórios não devem conter tokens, senhas, chaves privadas, conteúdo de arquivos pessoais ou variáveis de ambiente secretas.
8. **Limites reais do Android:** não prometer root, acesso a dados privados de outros apps, execução em background irrestrita ou controle de serviços externos que o Android/Google não disponibilizam.
9. **Sem ações pagas não autorizadas:** faturamento, upgrades e recursos com cobrança exigem consentimento separado e explícito.
10. **Toda correção deve ter teste:** incluir teste unitário ou instrumentado para o cenário que motivou a mudança.

## Etapa zero — validar a versão assinada

- [x] Configurar assinatura Release persistente no workflow.
- [x] Cadastrar os quatro Secrets do repositório por canal autenticado.
- [x] Validar os Secrets no workflow.
- [x] Gerar o APK Release 1.0.2.
- [x] Verificar a assinatura e o fingerprint esperado no workflow.
- [x] Publicar o artefato `geivia-release-apk`.
- [ ] Instalar a versão 1.0.2 no aparelho de teste e confirmar abertura.
- [ ] Confirmar inicialização do Debian e disponibilidade das ferramentas.
- [ ] Validar atualização de uma versão anterior assinada com a mesma chave, quando aplicável.
- [ ] Guardar cópia segura da chave de assinatura e documentar recuperação; nunca embutir a chave no APK nem no repositório.

**Separação de responsabilidades:** a chave de assinatura e os Secrets do GitHub são assunto de manutenção/publicação do projeto. Usuários finais da GEIVIA não devem configurar Secrets, keystores ou certificados para instalar e usar o aplicativo.

## Etapa 1 — central de diagnóstico no aplicativo

Criar uma área acessível pelo Dashboard, denominada **Diagnóstico e solução de problemas**, com:

- visão geral: ambiente pronto, atenção necessária ou ação do usuário;
- verificações por domínio: dispositivo/Android, armazenamento, rede, runtime PRoot, Debian, toolchain, contas e integrações;
- data/hora da última verificação e evidência resumida;
- explicação em português simples, sem expor stack traces por padrão;
- botão **Tentar corrigir** apenas quando houver uma correção conhecida e segura;
- botão **Ver detalhes técnicos** e **Copiar relatório**;
- histórico das últimas verificações e correções, com limite e retenção definidos;
- orientação manual em passos curtos quando não houver API ou permissão para corrigir automaticamente.

O diagnóstico deve ser somente leitura por padrão. Ações que alterem arquivos, pacotes, configurações ou recursos remotos precisam de pré-verificação e confirmação adequada.

## Etapa 2 — catálogo de verificações e correções

Implementar verificadores pequenos e independentes, com identificador estável, severidade, categoria, mensagem ao usuário, evidência sanitizada e ações permitidas.

### Android e dispositivo
- versão mínima do Android e ABI ARM64;
- espaço disponível antes de downloads e instalações;
- disponibilidade e integridade das bibliotecas nativas do APK;
- diretórios privados, permissões e capacidade de escrita;
- condições de rede e interrupções conhecidas;
- incompatibilidades de execução, sem sugerir root como solução.

### Runtime Linux e Debian
- binários PRoot extraídos e executáveis;
- diretórios e links simbólicos esperados;
- existência e integridade da raiz Debian;
- teste direto de shell, identidade e arquitetura;
- espaço necessário para instalação/atualização;
- detecção de instalação parcial e recuperação sem apagar o ambiente existente.

### Toolchain
- versões e disponibilidade de Git, SSH, Java, Node.js/npm, Python/pip/venv, CMake e Firebase CLI;
- detecção de pacotes ausentes ou incompatíveis;
- atualização apenas após mostrar o que será alterado;
- validação posterior a cada correção.

### Integrações e nuvem
- estado de autenticação sem ler ou exibir tokens;
- validade/escopo de autorização quando a API permitir;
- APIs habilitadas e recursos remotos por diagnóstico específico;
- distinguir conta conectada, API habilitada, serviço configurado e deploy realmente publicado;
- em respostas ambíguas, reconciliar o estado remoto antes de repetir uma criação ou publicação.

## Etapa 3 — motor de recuperação e retomada

- Manter estados por etapa: NOT_STARTED, WAITING_CONFIRMATION, IN_PROGRESS, COMPLETED, USER_CONFIRMED, FAILED e UNKNOWN, respeitando o motor de progresso existente.
- Registrar identificador único da tentativa, início/fim, versão do app, etapa, resultado e evidência sanitizada.
- Persistir checkpoints após cada etapa que tenha sido realmente verificada.
- Em interrupção, retomar da última etapa verificada; não reiniciar Debian nem baixar tudo novamente sem necessidade.
- Em timeout ou perda de conexão, marcar UNKNOWN quando o resultado externo não puder ser comprovado.
- Permitir reconciliação do mesmo recurso/Project ID antes de qualquer nova tentativa.
- Não repetir operações remotas não idempotentes automaticamente.
- Não apagar dados do usuário como primeira medida de recuperação.
- Oferecer backup/exportação antes de operações destrutivas e exigir confirmação explícita para qualquer limpeza.

## Etapa 4 — instalação inicial resiliente

A tela atual já possui checkpoints para Debian e toolchain. Evoluir para:

- etapas visíveis individualmente (preflight, runtime, Debian, shell, pacotes, verificação final);
- estimativa apenas quando houver base confiável; nunca inventar percentual;
- distinguir download, instalação, verificação e conclusão;
- botão pausar/cancelar com semântica clara;
- persistência por etapa e retomada após fechamento forçado;
- mensagens de erro mapeadas para causas conhecidas;
- relatório técnico sanitizado;
- verificação final completa antes de marcar onboarding como concluído.

## Etapa 5 — testes de falhas reais

Criar testes para:
- pouco espaço antes e durante download;
- rede indisponível e conexão interrompida;
- processo/Activity encerrado em cada checkpoint;
- Debian já instalado, instalação parcial e raiz inválida;
- bibliotecas nativas ausentes ou não executáveis;
- apt/npm com erro, timeout ou pacote parcialmente instalado;
- JSON de progresso corrompido;
- operação remota concluída apesar de timeout local;
- autenticação expirada ou escopo insuficiente;
- atualização do app preservando o ambiente privado;
- falha de assinatura/atualização detectada antes de orientar instalação.

Cada incidente encontrado durante o desenvolvimento deve gerar: cenário reproduzível, diagnóstico, recuperação segura, teste de regressão e texto de orientação ao usuário.

## Ordem de implementação

1. Concluir teste real da versão 1.0.2 no aparelho.
2. Fazer inventário dos verificadores e estados já existentes para evitar duplicação.
3. Criar modelos de diagnóstico e catálogo de códigos de erro.
4. Implementar tela central de diagnóstico em modo somente leitura.
5. Conectar verificações Android/PRoot/Debian/toolchain.
6. Conectar correções seguras e retomada por checkpoints.
7. Integrar verificações das contas e serviços externos.
8. Adicionar testes automatizados de falhas e recuperação.
9. Atualizar ajuda e documentação do usuário final.

## Critérios de aceite

- O usuário entende o problema e o próximo passo sem precisar conhecer Linux.
- O diagnóstico não revela credenciais nem dados privados.
- A correção automática só ocorre quando a causa e o procedimento são conhecidos e seguros.
- Operações externas ambíguas nunca são repetidas às cegas.
- Uma falha não apaga etapas concluídas nem reinstala o ambiente sem necessidade.
- Cada correção possui teste de regressão.
- A interface deixa claro quando algo foi verificado automaticamente e quando depende de confirmação do usuário.
