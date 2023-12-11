import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.VcsRoot

abstract class MermaidBaseBuild(
  name: String,
  root: VcsRoot,
  configuration: BuildType.() -> Unit = {}
): BuildType({
  this.name = name
  vcs {
    root(root)
  }
  configuration()
})
