import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.commitStatusPublisher
import jetbrains.buildServer.configs.kotlin.buildFeatures.dockerSupport
import jetbrains.buildServer.configs.kotlin.buildSteps.ScriptBuildStep
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import jetbrains.buildServer.configs.kotlin.triggers.vcs
import org.intellij.lang.annotations.Language

private const val DefaultImage = "registry.jetbrains.team/p/grazi/grazie-automation/mermaid-ci:1.0.0"

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

abstract class MermaidBuild(
  name: String,
  root: VcsRoot,
  dockerImage: String = DefaultImage,
  @Language("Shell Script") script: String,
  configuration: BuildType.() -> Unit = {}
): BuildType({
  this.name = name

  vcs {
    root(root)
  }
  steps {
    script {
      scriptContent = script.trimIndent()
      this.dockerImage = dockerImage
      dockerImagePlatform = ScriptBuildStep.ImagePlatform.Linux
    }
  }
  configuration()
  features {
    dockerSupport {
      loginToRegistry = on {
        dockerRegistryId = "PROJECT_EXT_3495"
      }
    }
  }
})

internal fun BuildType.withBaseVcsTrigger() {
  triggers {
    vcs({})
  }
}

internal fun BuildType.withCommitStatusPublisher(name: String) {
  features {
    commitStatusPublisher {
      publisher = space {
        authType = connection {
          connectionId = "PROJECT_EXT_2845"
        }
        displayName = name
      }
    }
  }
}
