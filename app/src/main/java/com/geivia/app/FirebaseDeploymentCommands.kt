package com.geivia.app

/**
 * Commands are copied to the user's Cloud Shell. GEIVIA never executes them
 * remotely and never stores Google credentials.
 */
object FirebaseDeploymentCommands {
    private fun requireProjectId(projectId: String) {
        require(projectId.matches(Regex("^[a-z][a-z0-9-]{4,28}[a-z0-9]$"))) {
            "Project ID inválido."
        }
    }

    fun firestoreRulesBootstrap(projectId: String): String {
        requireProjectId(projectId)
        return """
            set -euo pipefail
            PROJECT_ID='$projectId'
            WORKDIR="§HOME/geivia-firestore-§PROJECT_ID"
            if [ -L "§WORKDIR" ]; then
              echo "A pasta de destino é um link simbólico. Operação interrompida."
              exit 30
            fi
            if [ -e "§WORKDIR" ]; then
              if [ ! -d "§WORKDIR" ] || [ ! -f "§WORKDIR/.geivia-generated" ] || [ -L "§WORKDIR/.geivia-generated" ] || [ "$(cat "§WORKDIR/.geivia-generated" 2>/dev/null)" != "GEIVIA_FIRESTORE_RULES_STARTER_V1" ] || [ ! -f "§WORKDIR/firebase.json" ] || [ -L "§WORKDIR/firebase.json" ] || [ ! -f "§WORKDIR/firestore.rules" ] || [ -L "§WORKDIR/firestore.rules" ]; then
                echo "A pasta de destino não corresponde exatamente à estrutura gerada pelo GEIVIA. Nada foi alterado."
                exit 5
              fi
            fi
            if [ -e "§WORKDIR" ] && ! jq -e '.firestore.rules == "firestore.rules"' "§WORKDIR/firebase.json" >/dev/null 2>&1; then
              echo "A configuração Firestore local não aponta para firestore.rules. Nada foi publicado."
              exit 32
            fi
            gcloud projects describe "§PROJECT_ID" --format=json >/dev/null
            gcloud services list --enabled --project="§PROJECT_ID" --format=json | jq -e 'any(.[]; ((.config.name // .name) == "firestore.googleapis.com"))' >/dev/null
            gcloud services list --enabled --project="§PROJECT_ID" --format=json | jq -e 'any(.[]; ((.config.name // .name) == "firebaserules.googleapis.com"))' >/dev/null
            gcloud services list --enabled --project="§PROJECT_ID" --format=json | jq -e 'any(.[]; ((.config.name // .name) == "firebase.googleapis.com"))' >/dev/null
            command -v firebase >/dev/null
            if ! firebase projects:list >/dev/null 2>&1; then
              echo "Autentique a Firebase CLI com sua conta Google."
              firebase login --no-localhost
              firebase projects:list >/dev/null
            fi
            TOKEN="§(gcloud auth print-access-token)"
            FIREBASE_HTTP="§(curl -sS -o geivia-firebase-project.json -w '%{http_code}' -H "Authorization: Bearer §TOKEN" "https://firebase.googleapis.com/v1beta1/projects/§PROJECT_ID")"
            if [ "§FIREBASE_HTTP" = "404" ]; then
              firebase projects:addfirebase "§PROJECT_ID" --non-interactive
            elif [ "§FIREBASE_HTTP" != "200" ]; then
              echo "Não foi possível verificar o registro Firebase (HTTP §FIREBASE_HTTP)."
              cat geivia-firebase-project.json
              exit 8
            fi
            RULES_HTTP="$(curl -sS -o geivia-firestore-release.json -w '%{http_code}' \\
              -H "Authorization: Bearer §TOKEN" \\
              "https://firebaserules.googleapis.com/v1/projects/§PROJECT_ID/releases/cloud.firestore")"
            if [ "§RULES_HTTP" = "200" ]; then
              echo "Já existe uma versão ativa de regras Firestore. Por segurança, nenhuma regra será sobrescrita."
              echo "Revise as regras atuais no Console Firebase antes de planejar uma atualização."
              exit 4
            elif [ "§RULES_HTTP" != "404" ]; then
              echo "Não foi possível verificar a versão de regras (HTTP §RULES_HTTP). Nenhuma publicação foi feita."
              cat geivia-firestore-release.json
              exit 25
            fi
            if [ -e "§WORKDIR" ]; then
              :
            else
              mkdir "§WORKDIR"
              printf '%s\n' 'GEIVIA_FIRESTORE_RULES_STARTER_V1' > "§WORKDIR/.geivia-generated"
              cat > "§WORKDIR/firebase.json" <<'GEIVIA_FIREBASE_JSON'
            {
              "firestore": {
                "rules": "firestore.rules"
              }
            }
            GEIVIA_FIREBASE_JSON
              cat > "§WORKDIR/firestore.rules" <<'GEIVIA_FIRESTORE_RULES'
            rules_version = '2';
            service cloud.firestore {
              match /databases/{database}/documents {
                match /{document=**} {
                  allow read, write: if false;
                }
              }
            }
            GEIVIA_FIRESTORE_RULES
            fi
            if ! jq -e '.firestore.rules == "firestore.rules"' "§WORKDIR/firebase.json" >/dev/null 2>&1; then
              echo "A configuração Firestore local não aponta para firestore.rules. Nada foi publicado."
              exit 32
            fi
            cd "§WORKDIR"
            firebase deploy --only firestore:rules --project "§PROJECT_ID" --non-interactive
            echo "Regras iniciais restritivas publicadas. Todas as leituras e gravações de clientes estão bloqueadas até personalizar e testar regras por coleção."
        """.trimIndent().replace("§", "$")
    }

    fun hostingStarterDeploy(projectId: String): String {
        requireProjectId(projectId)
        return """
            set -euo pipefail
            PROJECT_ID='$projectId'
            SITE_ID='$projectId'
            WORKDIR="§HOME/geivia-hosting-§PROJECT_ID"
            if [ -L "§WORKDIR" ]; then
              echo "A pasta de destino é um link simbólico. Operação interrompida."
              exit 31
            fi
            if [ -e "§WORKDIR" ]; then
              if [ ! -d "§WORKDIR" ] || [ ! -f "§WORKDIR/.geivia-generated" ] || [ -L "§WORKDIR/.geivia-generated" ] || [ "$(cat "§WORKDIR/.geivia-generated" 2>/dev/null)" != "GEIVIA_HOSTING_STARTER_V1" ] || [ ! -f "§WORKDIR/firebase.json" ] || [ -L "§WORKDIR/firebase.json" ] || [ ! -d "§WORKDIR/public" ] || [ -L "§WORKDIR/public" ] || [ ! -f "§WORKDIR/public/index.html" ] || [ -L "§WORKDIR/public/index.html" ]; then
                echo "A pasta de destino não corresponde exatamente à estrutura gerada pelo GEIVIA. Nada foi alterado."
                exit 7
              fi
            fi
            if [ -e "§WORKDIR" ] && ! jq -e --arg id "§PROJECT_ID" '.hosting.site == §id and .hosting.public == "public"' "§WORKDIR/firebase.json" >/dev/null 2>&1; then
              echo "A configuração Hosting local não corresponde ao site padrão deste projeto. Nada foi criado ou publicado."
              exit 33
            fi
            gcloud projects describe "§PROJECT_ID" --format=json >/dev/null
            gcloud services list --enabled --project="§PROJECT_ID" --format=json | jq -e 'any(.[]; ((.config.name // .name) == "firebasehosting.googleapis.com"))' >/dev/null
            gcloud services list --enabled --project="§PROJECT_ID" --format=json | jq -e 'any(.[]; ((.config.name // .name) == "firebase.googleapis.com"))' >/dev/null
            command -v firebase >/dev/null
            if ! firebase projects:list >/dev/null 2>&1; then
              echo "Autentique a Firebase CLI com sua conta Google."
              firebase login --no-localhost
              firebase projects:list >/dev/null
            fi
            TOKEN="§(gcloud auth print-access-token)"
            FIREBASE_HTTP="§(curl -sS -o geivia-firebase-project.json -w '%{http_code}' -H "Authorization: Bearer §TOKEN" "https://firebase.googleapis.com/v1beta1/projects/§PROJECT_ID")"
            if [ "§FIREBASE_HTTP" = "404" ]; then
              firebase projects:addfirebase "§PROJECT_ID" --non-interactive
            elif [ "§FIREBASE_HTTP" != "200" ]; then
              echo "Não foi possível verificar o registro Firebase (HTTP §FIREBASE_HTTP)."
              cat geivia-firebase-project.json
              exit 9
            fi
            SITE_HTTP="§(curl -sS -o geivia-hosting-site.json -w '%{http_code}' \
              -H "Authorization: Bearer §TOKEN" \
              "https://firebasehosting.googleapis.com/v1beta1/projects/§PROJECT_ID/sites/§SITE_ID")"
            if [ "§SITE_HTTP" = "404" ]; then
              curl -fsS -X POST \
                -H "Authorization: Bearer §TOKEN" \
                -H "Content-Type: application/json" \
                -d '{}' \
                "https://firebasehosting.googleapis.com/v1beta1/projects/§PROJECT_ID/sites?siteId=§SITE_ID" \
                > geivia-hosting-site-create.json
            elif [ "§SITE_HTTP" != "200" ]; then
              echo "Não foi possível verificar o site (HTTP §SITE_HTTP). Nenhuma criação ou publicação foi feita."
              cat geivia-hosting-site.json
              exit 6
            fi
            SITE_READY=false
            for attempt in $(seq 1 18); do
              SITE_HTTP="$(curl -sS -o geivia-hosting-site-check.json -w '%{http_code}' \\
                -H "Authorization: Bearer §TOKEN" \\
                "https://firebasehosting.googleapis.com/v1beta1/projects/§PROJECT_ID/sites/§SITE_ID")"
              if [ "§SITE_HTTP" = "200" ]; then
                if ! jq -e '.type == "DEFAULT_SITE"' geivia-hosting-site-check.json >/dev/null; then
                  echo "O site encontrado não é o site padrão. Deploy interrompido para evitar publicar no destino errado."
                  cat geivia-hosting-site-check.json
                  exit 26
                fi
                SITE_READY=true
                break
              elif [ "§SITE_HTTP" = "404" ]; then
                sleep 10
              else
                echo "Falha ao confirmar a propagação do site (HTTP §SITE_HTTP)."
                cat geivia-hosting-site-check.json
                exit 27
              fi
            done
            if [ "§SITE_READY" != "true" ]; then
              echo "O site ainda não propagou. Não crie outro site; execute novamente a verificação do mesmo projeto."
              exit 28
            fi
            if [ -e "§WORKDIR" ]; then
              :
            else
              mkdir "§WORKDIR"
              mkdir "§WORKDIR/public"
              printf '%s\n' 'GEIVIA_HOSTING_STARTER_V1' > "§WORKDIR/.geivia-generated"
              cat > "§WORKDIR/firebase.json" <<'GEIVIA_HOSTING_JSON'
            {
              "hosting": {
                "site": "PROJECT_ID_PLACEHOLDER",
                "public": "public",
                "ignore": ["firebase.json", "**/.*", "**/node_modules/**"]
              }
            }
            GEIVIA_HOSTING_JSON
              sed -i "s/PROJECT_ID_PLACEHOLDER/§PROJECT_ID/g" "§WORKDIR/firebase.json"
              cat > "§WORKDIR/public/index.html" <<'GEIVIA_HOSTING_HTML'
            <!doctype html>
            <html lang="pt-BR">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <meta name="description" content="Página inicial publicada com GEIVIA">
              <title>Projeto publicado | GEIVIA</title>
              <style>
                body{margin:0;min-height:100vh;display:grid;place-items:center;background:#f3f8fc;color:#142c40;font:16px system-ui,sans-serif}
                main{max-width:680px;margin:24px;padding:40px;border-radius:20px;background:white;box-shadow:0 12px 40px #15334a18}
                h1{color:#07528d}p{line-height:1.7}.tag{color:#087db5;font-weight:700;letter-spacing:.12em}
              </style>
            </head>
            <body><main>
              <div class="tag">GEIVIA • FIREBASE HOSTING</div>
              <h1>Seu projeto está publicado.</h1>
              <p>Esta é uma página inicial de demonstração. Substitua este conteúdo pelos arquivos reais do seu site antes de divulgar o endereço.</p>
              <p id="project"></p>
            </main><script>document.getElementById('project').textContent='Project ID: ' + location.hostname.split('.')[0];</script></body>
            </html>
            GEIVIA_HOSTING_HTML
            fi
            if ! jq -e --arg id "§PROJECT_ID" '.hosting.site == §id and .hosting.public == "public"' "§WORKDIR/firebase.json" >/dev/null 2>&1; then
              echo "A configuração Hosting local não corresponde ao site padrão deste projeto. Nada foi publicado."
              exit 33
            fi
            cd "§WORKDIR"
            firebase deploy --only hosting --project "§PROJECT_ID" --non-interactive
            echo "Deploy enviado. Aguardando a URL responder por HTTPS."
            URL_READY=false
            for attempt in $(seq 1 18); do
              if curl -fsSI "https://§PROJECT_ID.web.app" >/dev/null; then
                URL_READY=true
                break
              fi
              sleep 10
            done
            if [ "§URL_READY" != "true" ]; then
              echo "O deploy foi enviado, mas a URL ainda não respondeu. Não repita a criação do site; verifique o Hosting no Console."
              exit 29
            fi
            echo "Publicação concluída. URL padrão: https://§PROJECT_ID.web.app"
        """.trimIndent().replace("§", "$")
    }
}
