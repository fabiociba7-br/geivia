# Firebase Authentication e Cloud Firestore no modo custo zero

## Firebase Authentication básico

- O GEIVIA orienta a configuração dos provedores pelo Console Firebase, na página Authentication > Método de login.
- O plano Spark inclui Authentication para a maioria dos provedores sem custo.
- Não fazer upgrade para Firebase Authentication with Identity Platform no modo custo zero.
- Não habilitar autenticação por telefone/SMS: esse método exige faturamento.
- A API `identitytoolkit.googleapis.com` é apenas um serviço técnico. Habilitá-la não ativa provedores nem configura o produto Authentication.
- O comando `projects.identityPlatform.initializeAuth` documentado para inicialização pública é uma variante do Identity Platform que exige projeto com faturamento habilitado; não usar esse fluxo no modo Spark.

Referências:
- https://firebase.google.com/docs/auth/android/start
- https://firebase.google.com/docs/auth/
- https://firebase.google.com/docs/projects/billing/firebase-pricing-plans

## Diagnóstico do Firestore

O diagnóstico somente leitura inclui:
- APIs habilitadas;
- lista de bancos Firestore, apenas quando a API Firestore já está habilitada;
- estado `billingEnabled`, obtido por `gcloud billing projects describe PROJECT_ID`.

Se a consulta de faturamento falhar ou o campo não estiver presente, o estado é desconhecido e a criação fica bloqueada. O app não presume que desconhecido significa faturamento desligado.

## Criação do banco padrão

A criação assistida só fica disponível quando:
1. o Project ID selecionado corresponde exatamente ao diagnóstico;
2. `firestore.googleapis.com` está habilitada;
3. o diagnóstico inclui a lista de bancos e não encontra `(default)`;
4. o diagnóstico confirma `billingEnabled: false`;
5. não há outra criação pendente para o mesmo projeto;
6. o usuário escolhe explicitamente a região e confirma a preparação do comando.

O comando copiado:
- confere novamente que o faturamento está desabilitado;
- confere novamente que a API Firestore está habilitada;
- lista os bancos existentes;
- cria apenas `(default)` se ele ainda não existir;
- usa edição Standard e proteção contra exclusão;
- não é executado pelo aplicativo.

Se a execução ficar ambígua, não copiar/executar novamente às cegas. Importar outro diagnóstico e reconciliar o estado do banco. A pendência só é encerrada automaticamente quando o diagnóstico do mesmo projeto encontra `(default)`.

## Localização

Opções apresentadas:
- `southamerica-east1` — São Paulo, Brasil;
- `us-central1` — Iowa, Estados Unidos;
- `nam5` — multirregião Estados Unidos.

A localização de uma instância provisionada não pode ser alterada depois. A localização do banco padrão também pode depender da localização padrão de recursos Google Cloud já definida no projeto. Se a região escolhida for incompatível com essa dependência, parar e investigar; não criar outro projeto como tentativa de contornar o erro.

Referências:
- https://firebase.google.com/docs/firestore/locations
- https://cloud.google.com/sdk/gcloud/reference/firestore/databases/create
- https://cloud.google.com/sdk/gcloud/reference/billing/projects/describe

## Limites de custo

O plano Spark fornece cotas sem custo para Firestore; ao exceder a cota, o serviço pode ser interrompido até o próximo ciclo, em vez de cobrar automaticamente. Não vincular faturamento ou migrar para Blaze para contornar limites sem autorização explícita do usuário. Confirmar sempre os limites e regras vigentes antes do uso em produção.

Referência:
- https://firebase.google.com/docs/projects/billing/firebase-pricing-plans

## Regras de segurança

A criação do banco não publica regras de segurança. Antes de conectar clientes Android ou web, revisar e publicar regras restritivas alinhadas ao modelo de dados e à identidade dos usuários. Não usar regras abertas de teste em produção. O GEIVIA ainda não gera regras específicas automaticamente, porque isso depende da estrutura real dos documentos e das permissões de cada aplicação.

Referência:
- https://firebase.google.com/docs/firestore/security/get-started

## Preservar o projeto Google Cloud existente

Se o Project ID selecionado ainda não estiver registrado como projeto Firebase, adicionar Firebase ao projeto Google Cloud existente pelo Console Firebase. Não escolher a opção de criar outro projeto para contornar qualquer erro de configuração. Se o projeto já estiver associado ao Firebase, reutilizá-lo.
