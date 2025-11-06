package com.intellij.lambda.testFramework.junit

import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.project.LocalProjectInfo
import com.intellij.ide.starter.project.TestCaseTemplate
import com.intellij.openapi.application.PathManager
import java.nio.file.Paths
import kotlin.io.path.createDirectories

object UltimateTestCases : TestCaseTemplate(IdeProductProvider.IU) {
  val JpsEmptyProject: TestCase<LocalProjectInfo> = withProject(
    projectInfo = LocalProjectInfo(
      projectDir = Paths.get(PathManager.getHomePath(), "out/ide-tests/cache/empty-project").createDirectories()
    )
  )
}