import jetbrains.buildServer.configs.kotlin.*

version = "2023.05"

project {
  vcsRoot(Mermaid)
  buildType(Tests)
  buildType(PluginVerifier)
  buildType(ReleaseStable)
  buildType(ReleaseNightly)
}
