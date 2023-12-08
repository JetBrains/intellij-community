import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.VcsRoot
import jetbrains.buildServer.configs.kotlin.buildFeatures.commitStatusPublisher
import jetbrains.buildServer.configs.kotlin.buildFeatures.dockerSupport
import jetbrains.buildServer.configs.kotlin.buildSteps.GradleBuildStep
import jetbrains.buildServer.configs.kotlin.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.triggers.vcs

internal const val DefaultImage = "registry.jetbrains.team/p/grazi/grazie-automation/mermaid-ci:1.0.0"

abstract class MermaidBuild(
  name: String,
  root: VcsRoot,
  dockerImage: String = DefaultImage,
  tasks: List<String>,
  configuration: BuildType.() -> Unit = {}
): BuildType({
  this.name = name

  vcs {
    root(root)
  }
  steps {
    gradle {
      this.tasks = tasks.joinToString(separator = " ")
      gradleParams = "--info"
      this.dockerImage = dockerImage
      dockerImagePlatform = GradleBuildStep.ImagePlatform.Linux
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
