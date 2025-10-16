package examples.data

import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.project.GitHubProject
import com.intellij.ide.starter.project.LocalProjectInfo
import com.intellij.ide.starter.project.TestCaseTemplate
import com.intellij.openapi.application.PathManager
import java.nio.file.Paths
import kotlin.io.path.createDirectories

object IdeaUltimateCases : TestCaseTemplate(IdeProductProvider.IU) {
  val IntelliJCommunityProject = withProject(
    GitHubProject.fromGithub(
      repoRelativeUrl = "JetBrains/intellij-community",
      branchName = "master")
  )

  val JitPackAndroidExample = withProject(
    GitHubProject.fromGithub(
      repoRelativeUrl = "jitpack/android-example",
      branchName = "master"
    )
  )

  val JpsEmptyProject: TestCase<LocalProjectInfo> = withProject(
    projectInfo = LocalProjectInfo(
      projectDir = Paths.get(PathManager.getHomePath(), "out/ide-tests/cache/empty-project").createDirectories()
    )
  )
}