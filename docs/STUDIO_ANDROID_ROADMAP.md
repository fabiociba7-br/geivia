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

- O editor mobile-first abre em um canvas de tela cheia com menu lateral, barra flutuante, prévia e ações de testar/publicar.
- O editor permite inserir e editar texto, botões, cartões, campos, imagens, divisores, espaços, linhas, colunas e seções; organizar hierarquia, reordenar, duplicar, desfazer/refazer e excluir componentes.
- Propriedades por componente incluem texto, tamanho, espaçamento, alinhamento, cores, arredondamento e visibilidade.
- Telas, componentes, tema, assets, variáveis e ações são armazenados por projeto e salvos localmente automaticamente.
- Painéis do Studio incluem páginas, componentes, tema, assets, ações/lógica e estrutura exportável.
- O painel de dados e ações permite cadastrar variáveis de texto, número, booleano e JSON.
- O painel permite definir gatilhos e ações de mensagem, navegação e alteração de variável.
- Gatilhos de toque e alteração de valor podem ser associados a um componente específico da tela; o vínculo é persistido pelo ID do componente e validado contra a tela atual.
- Gatilhos de abertura da tela permanecem no nível da tela, sem exigir componente.
- O editor já possui cadastro, seleção, renomeação e definição da tela inicial, com componentes persistidos separadamente por tela. A exclusão de telas exige confirmação, preserva ao menos uma tela, atualiza a tela inicial quando necessário e desativa ações que apontavam para a tela ou seus componentes.
- A navegação declarativa usa a lista de telas do projeto e valida referências a destinos inexistentes.
- Ações de navegação aceitam até 30 parâmetros nomeados por destino, com tipos texto, número, booleano e JSON; valores podem referenciar variáveis do projeto e são validados antes de salvar.
- Parâmetros são serializados junto à ação; documentos antigos sem esse campo continuam sendo carregados como listas vazias.
- As definições são persistidas em JSON versionado e validadas. A prévia e o projeto Android gerado executam navegação, mensagens e alteração de variáveis nos gatilhos de toque, abertura de tela e alteração de valor, incluindo parâmetros de navegação. Essas ações locais não executam operações remotas de infraestrutura.
- O Studio gera um projeto Android nativo inicial com Kotlin, Jetpack Compose, Manifest e arquivos Gradle, exportável em ZIP pelo seletor de arquivos do Android.
- O pacote ZIP também contém o esquema versionado do GEIVIA, o manifesto de assets e os arquivos locais selecionados, copiados para app/src/main/assets. URLs externas são registradas, mas não baixadas automaticamente.
- O projeto Android gerado inclui runtime para ações declarativas de toque, abertura de tela e alteração de valor: navegação, alteração de variáveis e mensagens. A navegação aplica os parâmetros padrão da tela de destino e os valores informados pela ação; um limite protege contra ciclos automáticos sem bloquear novas interações do usuário.
- Por decisão do proprietário, os workflows GitHub Actions foram removidos de main e da branch de desenvolvimento. Não há builds nem validações automáticas; a verificação será manual quando solicitada.

## Próximas etapas de desenvolvimento

1. Ampliar o suporte a formatos de imagem e definir uma estratégia segura para baixar ou referenciar URLs externas, sempre com escolha explícita do usuário.
2. Refinar a geração Android para incluir permissões, recursos e integrações escolhidas, sem copiar credenciais ou segredos.
3. Evoluir os painéis de propriedades, componentes reutilizáveis e preview para aproximar o fluxo de edição do FlutterFlow.
4. Adicionar configurações de dados locais/remotos e estados de carregamento/erro sem executar operações remotas silenciosamente.
5. Quando o proprietário considerar o app pronto, fazer uma revisão manual conjunta antes de qualquer compilação ou publicação.
