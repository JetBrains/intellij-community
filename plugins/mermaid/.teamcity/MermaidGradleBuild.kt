import jetbrains.buildServer.configs.kotlin.BuildSteps
import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.VcsRoot
import jetbrains.buildServer.configs.kotlin.buildFeatures.commitStatusPublisher
import jetbrains.buildServer.configs.kotlin.buildFeatures.dockerSupport
import jetbrains.buildServer.configs.kotlin.buildSteps.GradleBuildStep
import jetbrains.buildServer.configs.kotlin.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.triggers.vcs

internal const val DefaultImage = "registry.jetbrains.team/p/grazi/grazie-automation/mermaid-ci:1.0.0"

abstract class MermaidGradleBuild(
  name: String,
  root: VcsRoot,
  dockerImage: String = DefaultImage,
  tasks: List<String>,
  configuration: BuildType.() -> Unit = {}
): MermaidBaseBuild(name, root, configuration = {
  steps {
    defaultGradleStep(tasks, image = dockerImage, configuration = {})
  }
  withDockerFeature()
  configuration()
}) {
  companion object {
    internal fun BuildSteps.defaultGradleStep(
      tasks: List<String>,
      image: String = DefaultImage,
      configuration: GradleBuildStep.() -> Unit
    ): GradleBuildStep {
      return gradle {
        this.tasks = tasks.joinToString(separator = " ")
        gradleParams = "--info"
        dockerImage = image
        dockerImagePlatform = GradleBuildStep.ImagePlatform.Linux
        configuration()
      }
    }
  }
}

internal fun BuildType.withDockerFeature() {
  features {
    dockerSupport {
      loginToRegistry = on {
        dockerRegistryId = "PROJECT_EXT_3495"
      }
    }
  }
}

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
