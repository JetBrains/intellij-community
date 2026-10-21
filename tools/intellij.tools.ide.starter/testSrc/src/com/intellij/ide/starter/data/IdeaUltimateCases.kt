package com.intellij.ide.starter.data

import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.project.GitHubProject
import com.intellij.ide.starter.project.TestCaseTemplate
import com.intellij.tools.ide.starter.product.idea.ultimate.IdeaUltimate

object IdeaUltimateCases : TestCaseTemplate(IdeInfo.IdeaUltimate) {
  val IntelliJCommunityProject = withProject(
    GitHubProject.fromGithubOverSsh(
      repoRelativeUrl = "JetBrains/intellij-community",
      branchName = "master")
  )

  val JitPackAndroidExample = withProject(
    GitHubProject.fromGithubOverSsh(
      repoRelativeUrl = "jitpack/android-example",
      branchName = "master"
    )
  )
}