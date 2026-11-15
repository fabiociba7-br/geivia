# Continuidade do fluxo GEIVIA

## Checkpoint central de navegação

A aplicação persiste localmente:
- a tela/etapa atual (`workflow_checkpoint`);
- o índice da pergunta atual (`workflow_question_index`);
- o horário da última atualização (`workflow_checkpoint_updated_at`).

Ao reabrir o aplicativo, a tela inicial oferece **Continuar de onde parei** e restaura a última etapa válida. O índice do questionário também é restaurado. A gravação ocorre automaticamente quando a tela ou o índice muda.

O botão de reiniciar questionário limpa o checkpoint anterior e inicia um novo percurso pela configuração do ambiente. Não remove o projeto Google Cloud selecionado nem recursos na nuvem.

## Limite importante

Este checkpoint cobre a continuidade da navegação/onboarding. Ele não significa que todas as operações de nuvem já tenham um motor universal de estados. Cada operação externa ainda precisa de seu próprio estado persistente, pré-verificação, confirmação, reconciliação e histórico antes de ser considerada retomável com segurança.

Em particular:
- nunca repetir criação de projeto ou banco por timeout;
- não considerar uma etapa externa concluída apenas porque o comando foi copiado;
- exigir novo diagnóstico para confirmar o resultado no Google Cloud;
- manter o mesmo Project ID durante a recuperação;
- não ativar faturamento nem recursos pagos sem autorização separada.

## Próxima evolução

Unificar os estados individuais das operações em um registro central de etapas com estados `NOT_STARTED`, `IN_PROGRESS`, `WAITING_CONFIRMATION`, `UNKNOWN`, `COMPLETED` e `FAILED`, sem substituir as verificações específicas de cada serviço.