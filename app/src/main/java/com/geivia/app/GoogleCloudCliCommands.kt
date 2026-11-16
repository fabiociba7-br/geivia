package com.geivia.app

/** Idempotent Debian guest setup for the official Google Cloud CLI APT repository. */
internal object GoogleCloudCliCommands {
    fun install(): List<String> = listOf(
        "login", "debian", "--",
        "/bin/sh", "-lc",
        """
        set -eu
        export DEBIAN_FRONTEND=noninteractive
        apt-get update
        apt-get install -y ca-certificates curl gnupg apt-transport-https
        install -d -m 0755 /usr/share/keyrings
        curl -fsSL https://packages.cloud.google.com/apt/doc/apt-key.gpg | gpg --dearmor --yes -o /usr/share/keyrings/cloud.google.gpg
        chmod 0644 /usr/share/keyrings/cloud.google.gpg
        printf '%s\\n' 'deb [signed-by=/usr/share/keyrings/cloud.google.gpg] https://packages.cloud.google.com/apt cloud-sdk main' > /etc/apt/sources.list.d/google-cloud-sdk.list
        apt-get update
        apt-get install -y google-cloud-cli
        gcloud --version
        """.trimIndent()
    )
}
