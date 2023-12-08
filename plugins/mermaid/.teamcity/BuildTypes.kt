object Tests: MermaidBuild(
  name = "Tests",
  root = Mermaid,
  tasks = listOf("test"),
  configuration = {
    id("Tests${BranchConfiguration.IdSuffix}")
    withBaseVcsTrigger()
    withCommitStatusPublisher("Compilation and Tests")
  }
)

object PluginVerifier: MermaidBuild(
  name = "Plugin Verifier",
  root = Mermaid,
  tasks = listOf("runPluginVerifier"),
  configuration = {
    id("PluginVerifier${BranchConfiguration.IdSuffix}")
    withBaseVcsTrigger()
    withCommitStatusPublisher("Plugin Verifier")
  }
)

object ReleaseStable: MermaidBuild(
  name = "Release Stable",
  root = Mermaid,
  tasks = listOf("test", "publishPlugin"),
  configuration = {
    id("ReleaseStable${BranchConfiguration.IdSuffix}")
    withCommitStatusPublisher("Release Stable")
  }
)

object ReleaseNightly: MermaidBuild(
  name = "Release Nightly",
  root = Mermaid,
  tasks = listOf("test", "publishPlugin"),
  configuration = {
    id("ReleaseNightly${BranchConfiguration.IdSuffix}")
    withCommitStatusPublisher("Release Nightly")
    params {
      param("env.MARKETPLACE_CHANNEL", "nightly")
    }
  }
)
