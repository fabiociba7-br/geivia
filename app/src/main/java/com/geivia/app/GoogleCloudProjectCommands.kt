package com.geivia.app

import java.util.Base64

/**
 * Project provisioning is intentionally user-run in Cloud Shell.
 * The app persists the exact Project ID before exposing this command.
 */
object GoogleCloudProjectCommands {
    private fun requireProjectId(projectId: String) {
        require(projectId.matches(Regex("^[a-z][a-z0-9-]{4,28}[a-z0-9]$"))) {
            "Project ID inválido."
        }
    }

    fun createOrReuse(projectId: String, displayName: String): String {
        requireProjectId(projectId)
        require(displayName.isNotBlank()) { "Informe o nome do projeto." }
        val encodedName = Base64.getEncoder().encodeToString(displayName.toByteArray(Charsets.UTF_8))
        return """
            set -euo pipefail
            PROJECT_ID='$projectId'
            PROJECT_NAME="$(printf '%s' '$encodedName' | base64 -d)"
            if gcloud projects describe "§PROJECT_ID" --format=json > geivia-project-preflight.json 2> geivia-project-preflight.err; then
              echo "Projeto já existe e está acessível. Nenhuma criação será feita."
            else
              if grep -Eq '^ERROR: \(gcloud\.projects\.describe\\) NOT_FOUND:' geivia-project-preflight.err; then
                echo "O Google confirmou NOT_FOUND para o ID exato. Enviando uma única solicitação."
                if ! gcloud projects create "§PROJECT_ID" --name="§PROJECT_NAME" --format=json > geivia-project-create-response.json 2> geivia-project-create.err; then
                  cat geivia-project-create.err
                  echo "A solicitação falhou ou ficou inconclusiva. NÃO repita. Reconcilie o mesmo Project ID."
                  exit 20
                fi
              else
                cat geivia-project-preflight.err
                echo "Consulta inconclusiva ou sem permissão. Nenhuma criação foi enviada."
                exit 21
              fi
            fi
            if ! gcloud projects describe "§PROJECT_ID" --format=json > geivia-project-result.json 2> geivia-project-result.err; then
              cat geivia-project-result.err
              echo "Não foi possível confirmar o resultado. NÃO crie outro projeto. Reconcilie este mesmo ID."
              exit 22
            fi
            if ! jq -e '.lifecycleState == "ACTIVE"' geivia-project-result.json >/dev/null; then
              echo "O projeto existe, mas ainda não está ACTIVE. Não crie outro; aguarde e reconcilie este mesmo ID."
              exit 23
            fi
            jq '[{projectId:.projectId,name:.name,projectNumber:(.projectNumber|tostring),lifecycleState:.lifecycleState}]' geivia-project-result.json > geivia-projects.json
            cloudshell download geivia-projects.json
        """.trimIndent().replace("§", "$")
    }

    fun reconcile(projectId: String): String {
        requireProjectId(projectId)
        return """
            set -euo pipefail
            if gcloud projects describe '$projectId' --format=json > geivia-project-result.json 2> geivia-project-reconcile.err; then
              jq '[{projectId:.projectId,name:.name,projectNumber:(.projectNumber|tostring),lifecycleState:.lifecycleState}]' geivia-project-result.json > geivia-projects.json
            else
              if grep -Eq '^ERROR: \(gcloud\.projects\.describe\\) NOT_FOUND:' geivia-project-reconcile.err; then
                jq -n --arg id '$projectId' '[{projectId:§id,name:§id,projectNumber:"",lifecycleState:"NOT_FOUND_CONFIRMED"}]' > geivia-projects.json
                echo "O Google confirmou NOT_FOUND para este Project ID. Nenhum projeto foi criado."
              else
                cat geivia-project-reconcile.err
                echo "Reconciliação inconclusiva. Não altere o ID nem tente criar outro projeto."
                exit 24
              fi
            fi
            cloudshell download geivia-projects.json
        """.trimIndent().replace("§", "$")
    }
}
