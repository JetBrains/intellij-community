object Tests: MermaidBuild(
  name = "Tests",
  root = Mermaid,
  script = "./gradlew test --info",
  configuration = {
    id("Tests${BranchConfiguration.IdSuffix}")
    withBaseVcsTrigger()
    withCommitStatusPublisher("Compilation and Tests")
  }
)

object PluginVerifier: MermaidBuild(
  name = "Plugin Verifier",
  root = Mermaid,
  script = "./gradlew runPluginVerifier --info",
  configuration = {
    id("PluginVerifier${BranchConfiguration.IdSuffix}")
    withBaseVcsTrigger()
    withCommitStatusPublisher("Plugin Verifier")
  }
)

object ReleaseStable: MermaidBuild(
  name = "Release Stable",
  root = Mermaid,
  script = "./gradlew test publishPlugin --info",
  configuration = {
    id("ReleaseStable${BranchConfiguration.IdSuffix}")
    withCommitStatusPublisher("Release Stable")
  }
)

object ReleaseNightly: MermaidBuild(
  name = "Release Nightly",
  root = Mermaid,
  script = "./gradlew test publishPlugin --info",
  configuration = {
    id("ReleaseNightly${BranchConfiguration.IdSuffix}")
    withCommitStatusPublisher("Release Nightly")
    params {
      param("env.MARKETPLACE_CHANNEL", "nightly")
    }
  }
)
