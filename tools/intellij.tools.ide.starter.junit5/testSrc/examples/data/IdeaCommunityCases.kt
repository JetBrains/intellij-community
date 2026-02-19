package examples.data

import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.project.GitHubProject
import com.intellij.ide.starter.project.TestCaseTemplate

object IdeaCommunityCases : TestCaseTemplate(IdeProductProvider.IC) {

  val GradleJitPackSimple = withProject(
    GitHubProject.fromGithub(
      branchName = "master",
      repoRelativeUrl = "/jitpack/gradle-simple",
      commitHash = "c11de3b42af65dd14c58d175c6ce0deb629704d6"
    )
  ).useRelease()
}