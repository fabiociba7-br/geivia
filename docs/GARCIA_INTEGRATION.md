# Integração GEIVIA Studio + Garcia AI

A integração usa o serviço Ciba AI já existente, sem incorporar o servidor da Garcia ao GEIVIA.

## Contrato

- Serviço: `https://ciba-ai.fabiociba7.workers.dev`
- Aplicativo cliente: `com.geivia.app`
- Estado do serviço: `GET /v1/status`
- Registro privado: `POST /v1/auth/register`
- Pareamento com a identidade do proprietário: `POST /v1/owner/pair`
- Conversa: `POST /v1/ai/chat`

O token de dispositivo é armazenado cifrado com AES-GCM por uma chave do Android Keystore. O aplicativo não contém token administrativo.

## Provisionar a conexão

Depois que a migration `0019_geivia_studio_client.sql` estiver aprovada e aplicada no Ciba AI:

1. Abra o painel privado da Garcia em `https://ciba-ai.fabiociba7.workers.dev/dashboard`.
2. Entre como proprietário da Garcia.
3. Em **Conectar novo aplicativo**, selecione **GEIVIA Studio (com.geivia.app)**.
4. Gere os convites. O painel mostra um código de registro e um código de pareamento, válidos por 24 horas e de uso único.
5. No GEIVIA, abra **Garcia AI**, cole os dois códigos e toque em **Conectar com segurança**.

Os códigos são emitidos pelo painel autenticado da Garcia; não compartilhe esses códigos em canais públicos. Se expirarem ou forem consumidos, gere novos convites.

## Contexto enviado à Garcia

Quando o usuário envia uma mensagem, o GEIVIA pode incluir:
- nome e ID do projeto selecionado;
- plataformas escolhidas no planejamento;
- JSON da tela atualmente salva no editor visual.

Esse contexto é limitado e enviado somente junto com a mensagem iniciada pelo usuário. O histórico da conversa fica em memória durante a sessão; não é gravado como histórico permanente pelo GEIVIA.

## Limites desta primeira integração

- A Garcia conversa, analisa e recomenda.
- A Garcia ainda não altera arquivos, componentes ou configurações diretamente.
- Não há execução de terminal, deploy, publicação ou concessão de ferramentas.
- A migration não é aplicada automaticamente e nenhum deploy é executado por esta PR.
