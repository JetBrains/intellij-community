import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.dockerSupport
import jetbrains.buildServer.configs.kotlin.buildSteps.ScriptBuildStep
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import org.intellij.lang.annotations.Language

private val defaultImage = "registry.jetbrains.team/p/grazi/grazie-automation/mermaid-ci:1.0.0"

object Tests: MermaidBuild(
  name = "Tests",
  script = "./gradlew test --info"
)

open class MermaidBuild(
  name: String,
  dockerImage: String = defaultImage,
  @Language("Shell Script") script: String
): BuildType({
  this.name = name

  vcs {
    root(Mermaid)
  }
  steps {
    script {
      scriptContent = script.trimIndent()
      this.dockerImage = dockerImage
      dockerImagePlatform = ScriptBuildStep.ImagePlatform.Linux
    }
  }
  features {
    dockerSupport {
      loginToRegistry = on {
        dockerRegistryId = "PROJECT_EXT_3495"
      }
    }
  }
})
