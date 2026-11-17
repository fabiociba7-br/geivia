# GEIVIA Studio — direção Android

O GEIVIA Studio é uma plataforma visual para criar aplicativos Android nativos. A experiência principal é mobile-first e a saída do produto é um projeto Android; não há alvo de publicação Web nesta direção.

## Princípios

- O editor visual é a fonte de verdade para a estrutura das telas.
- Componentes, propriedades, variáveis, ações e navegação devem ter um esquema versionado.
- A prévia deve representar a tela Android com dimensões e comportamento previsíveis.
- A geração futura deve produzir um projeto Android compilável, preservando o package name e a assinatura configurados pelo proprietário.
- Nenhuma ação declarada pelo usuário será executada silenciosamente durante a edição.

## Etapas de evolução

1. **Editor visual:** árvore de componentes, seleção, propriedades, hierarquia e reordenação.
2. **Dados e lógica:** variáveis tipadas e ações declarativas com validação e persistência local.
3. **Navegação:** múltiplas telas, rotas e parâmetros entre telas.
4. **Biblioteca de componentes:** controles Android reutilizáveis e propriedades completas.
5. **Dados e serviços:** fontes locais/remotas, Firebase e estados de carregamento/erro.
6. **Pré-visualização:** simulação das telas e fluxos antes da compilação.
7. **Geração Android:** projeto Gradle nativo, compilação de validação e exportação controlada.
8. **Publicação:** preparação de assinatura e publicação somente após confirmação explícita.

## Implementação atual

- O editor permite inserir e editar componentes básicos, organizar a hierarquia e salvar a estrutura localmente.
- O painel de dados e ações permite cadastrar variáveis de texto, número, booleano e JSON.
- O painel permite definir gatilhos e ações de mensagem, navegação e alteração de variável.
- As definições são persistidas em JSON versionado e validadas; ainda não existe motor de execução dessas ações.
- A geração de APK não faz parte das validações automáticas.

## Próxima entrega

Conectar ações aos componentes selecionados e implementar navegação entre múltiplas telas, mantendo as ações como definições até que o fluxo de execução seja implementado e testado explicitamente.
