import jetbrains.buildServer.configs.kotlin.vcs.GitVcsRoot

object Mermaid : GitVcsRoot({
    name = "Mermaid"
    url = "ssh://git@git.jetbrains.team/grazi/mermaid.git"
    branch = "main"
    branchSpec = "+:*"
    authMethod = uploadedKey {
        uploadedKey = "default teamcity key"
    }
})
