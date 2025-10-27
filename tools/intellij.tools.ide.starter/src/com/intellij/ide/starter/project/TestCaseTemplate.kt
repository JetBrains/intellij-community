package com.intellij.ide.starter.project

import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.models.TestCase

abstract class TestCaseTemplate(val ideInfo: IdeInfo) {
  fun <T : ProjectInfoSpec> withProject(projectInfo: T) = TestCase(ideInfo = ideInfo, projectInfo = projectInfo)
  override fun toString(): String {
    return ideInfo.productCode
  }
}

