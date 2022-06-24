package com.intellij.ide.starter.tests.examples.data

import com.intellij.ide.starter.data.TestCaseTemplate
import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.project.ProjectInfo
import kotlin.io.path.div

object GoLandCases : TestCaseTemplate(IdeProductProvider.GO) {

  val LightEditor = getTemplate()

  val Kratos = getTemplate().copy(
    projectInfo = ProjectInfo(
      testProjectURL = "https://github.com/go-kratos/kratos/archive/refs/heads/main.zip",
      testProjectImageRelPath = { it / "kratos-main" }
    )
  )
}