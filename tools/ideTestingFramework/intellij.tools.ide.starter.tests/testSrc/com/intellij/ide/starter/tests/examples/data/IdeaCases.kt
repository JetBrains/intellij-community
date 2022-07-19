package com.intellij.ide.starter.tests.examples.data

import com.intellij.ide.starter.data.TestCaseTemplate
import com.intellij.ide.starter.models.IdeProduct
import com.intellij.ide.starter.project.ProjectInfo
import kotlin.io.path.div

object IdeaCases : TestCaseTemplate(IdeProduct.IU) {

  val GradleJitPackSimple = getTemplate().withProject(
    ProjectInfo(
      testProjectURL = "https://github.com/jitpack/gradle-simple/archive/refs/heads/master.zip",
      testProjectImageRelPath = { it / "gradle-simple-master" }
    )
  )

  val IntelliJCommunityProject = getTemplate().withProject(
    ProjectInfo(
      testProjectURL = "https://github.com/JetBrains/intellij-community/archive/master.zip",
      testProjectImageRelPath = { it / "intellij-community-master" }
    )
  )
}