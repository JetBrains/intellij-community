import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.dockerSupport
import jetbrains.buildServer.configs.kotlin.buildSteps.ScriptBuildStep
import jetbrains.buildServer.configs.kotlin.buildSteps.script

object Tests: MermaidBuild(
  buildTypeName = "Tests",
  dockerImageName = "registry.jetbrains.team/p/grazi/grazie-automation/mermaid-ci:latest",
  script = "./gradlew test --info"
)

open class MermaidBuild(buildTypeName: String, dockerImageName: String, script: String) : BuildType({
    name = buildTypeName

    vcs {
        root(Mermaid)
    }

    steps {
        script {
            scriptContent = script.trimIndent()
            dockerImage = dockerImageName
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
