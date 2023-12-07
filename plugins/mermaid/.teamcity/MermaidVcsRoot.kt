import jetbrains.buildServer.configs.kotlin.vcs.GitVcsRoot

object Mermaid: GitVcsRoot({
  name = "Mermaid${BranchConfiguration.IdSuffix}"
  id("Mermaid${BranchConfiguration.IdSuffix}")
  url = "ssh://git@git.jetbrains.team/grazi/mermaid.git"
  branch = BranchConfiguration.Branch
  authMethod = uploadedKey {
    uploadedKey = "default teamcity key"
  }
})
