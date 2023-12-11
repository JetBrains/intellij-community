import MermaidGradleBuild.Companion.defaultGradleStep
import jetbrains.buildServer.configs.kotlin.buildSteps.ScriptBuildStep
import jetbrains.buildServer.configs.kotlin.buildSteps.qodana
import jetbrains.buildServer.configs.kotlin.buildSteps.script

object Tests: MermaidGradleBuild(
  name = "Tests",
  root = Mermaid,
  tasks = listOf("test"),
  configuration = {
    id("Tests${BranchConfiguration.IdSuffix}")
    withBaseVcsTrigger()
    withCommitStatusPublisher("Compilation and Tests")
  }
)

object PluginVerifier: MermaidGradleBuild(
  name = "Plugin Verifier",
  root = Mermaid,
  tasks = listOf("runPluginVerifier"),
  configuration = {
    id("PluginVerifier${BranchConfiguration.IdSuffix}")
    withBaseVcsTrigger()
    withCommitStatusPublisher("Plugin Verifier")
  }
)

object Qodana: MermaidBaseBuild(
  name = "Qodana",
  root = Mermaid,
  configuration = {
    steps {
      defaultGradleStep(tasks = listOf(":plugin:generateLexerAndParser"), configuration = {})
      qodana {
        linter = jvm()
        cloudToken = "%mermaid.qodana.cloud.token%"
      }
    }
    withDockerFeature()
    withBaseVcsTrigger()
    withCommitStatusPublisher("Qodana")
  }
)

object ReleaseStable: MermaidGradleBuild(
  name = "Release Stable",
  root = Mermaid,
  tasks = listOf("test", "publishPlugin"),
  configuration = {
    id("ReleaseStable${BranchConfiguration.IdSuffix}")
    withCommitStatusPublisher("Release Stable")
  }
)

object ReleaseNightly: MermaidGradleBuild(
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
