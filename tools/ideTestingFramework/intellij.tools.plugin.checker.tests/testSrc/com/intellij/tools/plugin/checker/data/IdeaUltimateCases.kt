package com.intellij.tools.plugin.checker.data

import com.intellij.ide.starter.data.TestCaseTemplate
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.project.ProjectInfo
import kotlin.io.path.div

object IdeaUltimateCases : TestCaseTemplate(IdeProductProvider.IU) {

  val GradleJitPackSimple = getTemplate().withProject(
    ProjectInfo(
      testProjectURL = "https://github.com/jitpack/gradle-simple/archive/refs/heads/master.zip",
      testProjectImageRelPath = { it / "gradle-simple-master" }
    )
  )

  val MavenSimpleApp = getTemplate().withProject(
    ProjectInfo(
      testProjectURL = "https://github.com/jenkins-docs/simple-java-maven-app/archive/refs/heads/master.zip",
      testProjectImageRelPath = { it / "simple-java-maven-app-master" }
    )
  )

  val IntelliJCommunityProject = getTemplate().withProject(
    ProjectInfo(
      testProjectURL = "https://github.com/JetBrains/intellij-community/archive/master.zip",
      testProjectImageRelPath = { it / "intellij-community-master" }
    )
  )
}