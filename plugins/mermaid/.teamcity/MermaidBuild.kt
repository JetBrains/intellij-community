import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.VcsRoot
import jetbrains.buildServer.configs.kotlin.buildFeatures.commitStatusPublisher
import jetbrains.buildServer.configs.kotlin.buildFeatures.dockerSupport
import jetbrains.buildServer.configs.kotlin.buildSteps.ScriptBuildStep
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import jetbrains.buildServer.configs.kotlin.triggers.vcs
import org.intellij.lang.annotations.Language

internal const val DefaultImage = "registry.jetbrains.team/p/grazi/grazie-automation/mermaid-ci:1.0.0"

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
