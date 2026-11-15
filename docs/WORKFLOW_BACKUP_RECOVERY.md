# Backup e recuperação do histórico GEIVIA

## Exportar

No painel de acompanhamento das etapas, use **Exportar backup do histórico** e salve o arquivo JSON em local seguro. A exportação:
- copia literalmente os valores persistidos de progresso e auditoria;
- funciona mesmo quando o JSON de progresso está corrompido;
- inclui versão do formato, data da exportação e hashes SHA-256 dos dois conteúdos;
- não altera nem corrige os dados originais do aplicativo.

Guarde o arquivo fora do aparelho. O backup pode conter IDs de projetos, estados e mensagens operacionais; trate-o como dado privado.

## Limites de segurança

O arquivo é uma cópia para preservação e análise, não um comando de restauração automática. Um backup antigo não prova o estado atual de um recurso Google Cloud. Em particular:
- nunca converta automaticamente IN_PROGRESS ou UNKNOWN em NOT_STARTED;
- nunca trate ausência em uma lista genérica como prova de que uma criação não ocorreu;
- nunca repita criação, publicação ou alteração com base apenas no backup;
- não substitua o histórico local corrompido por um backup sem reconciliação de cada operação pendente.

## Recuperação operacional

1. Preserve o arquivo original do backup e não limpe os dados do GEIVIA nem reinstale o app para tentar corrigir o problema.
2. Confira o Project ID exato associado a cada operação pendente.
3. Para cada operação IN_PROGRESS ou UNKNOWN, execute apenas consultas de leitura no projeto original e obtenha um diagnóstico novo.
4. Compare o resultado com o recurso exato e com o mesmo Project ID. Se o resultado for ambíguo, mantenha a operação bloqueada.
5. Só registre conclusão com evidência atual do mesmo projeto. Para criação, somente uma resposta inequívoca de ausência para o ID exato, seguida de confirmação explícita separada, pode encerrar a tentativa anterior.
6. Não crie um projeto substituto nem altere o ID para contornar uma falha.

A recuperação automática a partir de backup ainda não é oferecida. O GEIVIA prefere bloquear a operação e preservar os dados a restaurar um estado possivelmente desatualizado e provocar duplicidade.
