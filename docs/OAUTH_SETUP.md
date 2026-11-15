# OAuth e autenticação no GEIVIA

## Decisão atual

A primeira versão do GEIVIA não exige que o usuário crie um cliente OAuth próprio. Também não usa credenciais OAuth ou contas pessoais do desenvolvedor.

O acesso inicial ao Google Cloud acontece no navegador e no Cloud Shell, com a própria conta do usuário. O GEIVIA abre os endereços oficiais e fornece comandos que o usuário revisa e executa no Cloud Shell. O aplicativo não recebe senha, cookie, token ou chave de serviço.

## Por que não configurar um OAuth individual automaticamente

O Google determina que clientes OAuth sejam criados e alterados manualmente no Cloud Console. Clientes Android são associados ao package e ao certificado SHA-1 do aplicativo. A autorização de apps instalados tem requisitos próprios; não se deve aplicar ao Android o fluxo de app para computador nem presumir que um APK possa alternar com segurança entre clientes OAuth de projetos diferentes.

Fontes oficiais:
- https://developers.google.com/identity/protocols/oauth2/resources/best-practices
- https://developers.google.com/identity/protocols/oauth2/native-app
- https://developer.android.com/identity/authorization

## Quando OAuth direto será reconsiderado

Somente se uma função exigir acesso direto do APK e houver um fluxo Android suportado, seguro e verificável para esse caso. Antes disso:
- não solicitar senha Google no GEIVIA;
- não pedir tokens ao usuário;
- não importar cookies;
- não armazenar refresh tokens;
- não colocar client secrets ou chaves de conta de serviço no APK;
- não habilitar OAuth falso ou simular conta conectada.

## Distribuição

O APK pode continuar sendo distribuído diretamente. O uso do Cloud Shell depende de uma conta Google com acesso ao Google Cloud e das permissões IAM correspondentes. O GEIVIA não exige ativação de faturamento para o diagnóstico inicial.
