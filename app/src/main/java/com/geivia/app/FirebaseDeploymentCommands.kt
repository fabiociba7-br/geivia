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
            gcloud projects describe "$PROJECT_ID" --format=json >/dev/null
            gcloud services list --enabled --project="$PROJECT_ID" --format=json | jq -e 'any(.[]; ((.config.name // .name) == "firestore.googleapis.com"))' >/dev/null
            gcloud services list --enabled --project="$PROJECT_ID" --format=json | jq -e 'any(.[]; ((.config.name // .name) == "firebase.googleapis.com"))' >/dev/null
            command -v firebase >/dev/null
            if ! firebase projects:list >/dev/null 2>&1; then
              echo "Autentique a Firebase CLI com sua conta Google."
              firebase login --no-localhost
              firebase projects:list >/dev/null
            fi
            TOKEN="$(gcloud auth print-access-token)"
            FIREBASE_HTTP="$(curl -sS -o geivia-firebase-project.json -w '%{http_code}' -H "Authorization: Bearer $TOKEN" "https://firebase.googleapis.com/v1beta1/projects/$PROJECT_ID")"
            if [ "$FIREBASE_HTTP" = "404" ]; then
              firebase projects:addfirebase "$PROJECT_ID" --non-interactive
            elif [ "$FIREBASE_HTTP" != "200" ]; then
              echo "Não foi possível verificar o registro Firebase (HTTP $FIREBASE_HTTP)."
              cat geivia-firebase-project.json
              exit 8
            fi
            curl -fsS -H "Authorization: Bearer $TOKEN" \
              "https://firebaserules.googleapis.com/v1/projects/$PROJECT_ID/releases?pageSize=100" \
              > geivia-firestore-releases.json
            if jq -e --arg p "$PROJECT_ID" 'any(.releases[]?; .name == ("projects/" + $p + "/releases/cloud.firestore"))' geivia-firestore-releases.json >/dev/null; then
              echo "Já existe uma versão ativa de regras Firestore. Por segurança, nenhuma regra será sobrescrita."
              echo "Revise as regras atuais no Console Firebase antes de planejar uma atualização."
              exit 4
            fi
            WORKDIR="$HOME/geivia-firestore-$PROJECT_ID"
            if [ -e "$WORKDIR" ]; then
              if [ ! -f "$WORKDIR/.geivia-generated" ]; then
                echo "A pasta de destino já existe e não pertence ao GEIVIA. Nada foi sobrescrito."
                exit 5
              fi
            else
              mkdir -p "$WORKDIR"
              printf '%s\n' 'GEIVIA_FIRESTORE_RULES_STARTER_V1' > "$WORKDIR/.geivia-generated"
              cat > "$WORKDIR/firebase.json" <<'GEIVIA_FIREBASE_JSON'
            {
              "firestore": {
                "rules": "firestore.rules"
              }
            }
            GEIVIA_FIREBASE_JSON
              cat > "$WORKDIR/firestore.rules" <<'GEIVIA_FIRESTORE_RULES'
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
            cd "$WORKDIR"
            firebase deploy --only firestore:rules --project "$PROJECT_ID" --non-interactive
            echo "Regras iniciais restritivas publicadas. Todas as leituras e gravações de clientes estão bloqueadas até personalizar e testar regras por coleção."
        """.trimIndent()
    }

    fun hostingStarterDeploy(projectId: String): String {
        requireProjectId(projectId)
        return """
            set -euo pipefail
            PROJECT_ID='$projectId'
            SITE_ID='$projectId'
            gcloud projects describe "$PROJECT_ID" --format=json >/dev/null
            gcloud services list --enabled --project="$PROJECT_ID" --format=json | jq -e 'any(.[]; ((.config.name // .name) == "firebasehosting.googleapis.com"))' >/dev/null
            gcloud services list --enabled --project="$PROJECT_ID" --format=json | jq -e 'any(.[]; ((.config.name // .name) == "firebase.googleapis.com"))' >/dev/null
            command -v firebase >/dev/null
            if ! firebase projects:list >/dev/null 2>&1; then
              echo "Autentique a Firebase CLI com sua conta Google."
              firebase login --no-localhost
              firebase projects:list >/dev/null
            fi
            TOKEN="$(gcloud auth print-access-token)"
            FIREBASE_HTTP="$(curl -sS -o geivia-firebase-project.json -w '%{http_code}' -H "Authorization: Bearer $TOKEN" "https://firebase.googleapis.com/v1beta1/projects/$PROJECT_ID")"
            if [ "$FIREBASE_HTTP" = "404" ]; then
              firebase projects:addfirebase "$PROJECT_ID" --non-interactive
            elif [ "$FIREBASE_HTTP" != "200" ]; then
              echo "Não foi possível verificar o registro Firebase (HTTP $FIREBASE_HTTP)."
              cat geivia-firebase-project.json
              exit 9
            fi
            SITE_HTTP="$(curl -sS -o geivia-hosting-site.json -w '%{http_code}' \
              -H "Authorization: Bearer $TOKEN" \
              "https://firebasehosting.googleapis.com/v1beta1/projects/$PROJECT_ID/sites/$SITE_ID")"
            if [ "$SITE_HTTP" = "404" ]; then
              curl -fsS -X POST \
                -H "Authorization: Bearer $TOKEN" \
                -H "Content-Type: application/json" \
                -d '{}' \
                "https://firebasehosting.googleapis.com/v1beta1/projects/$PROJECT_ID/sites?siteId=$SITE_ID" \
                > geivia-hosting-site-create.json
            elif [ "$SITE_HTTP" != "200" ]; then
              echo "Não foi possível verificar o site (HTTP $SITE_HTTP). Nenhuma criação ou publicação foi feita."
              cat geivia-hosting-site.json
              exit 6
            fi
            WORKDIR="$HOME/geivia-hosting-$PROJECT_ID"
            if [ -e "$WORKDIR" ]; then
              if [ ! -f "$WORKDIR/.geivia-generated" ]; then
                echo "A pasta de destino já existe e não pertence ao GEIVIA. Nada foi sobrescrito."
                exit 7
              fi
            else
              mkdir -p "$WORKDIR/public"
              printf '%s\n' 'GEIVIA_HOSTING_STARTER_V1' > "$WORKDIR/.geivia-generated"
              cat > "$WORKDIR/firebase.json" <<'GEIVIA_HOSTING_JSON'
            {
              "hosting": {
                "site": "PROJECT_ID_PLACEHOLDER",
                "public": "public",
                "ignore": ["firebase.json", "**/.*", "**/node_modules/**"]
              }
            }
            GEIVIA_HOSTING_JSON
              sed -i "s/PROJECT_ID_PLACEHOLDER/$PROJECT_ID/g" "$WORKDIR/firebase.json"
              cat > "$WORKDIR/public/index.html" <<'GEIVIA_HOSTING_HTML'
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
            cd "$WORKDIR"
            firebase deploy --only hosting --project "$PROJECT_ID" --non-interactive
            echo "Publicação concluída. URL padrão: https://$PROJECT_ID.web.app"
            curl -fsSI "https://$PROJECT_ID.web.app" >/dev/null
            echo "A URL respondeu por HTTPS."
        """.trimIndent()
    }
}
