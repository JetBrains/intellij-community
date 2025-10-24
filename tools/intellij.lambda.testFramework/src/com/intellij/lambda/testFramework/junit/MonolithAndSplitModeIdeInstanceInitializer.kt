package com.intellij.lambda.testFramework.junit

import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.models.TestCase
import com.intellij.ide.starter.project.LocalProjectInfo
import com.intellij.ide.starter.project.TestCaseTemplate
import com.intellij.ide.starter.runner.Starter
import com.intellij.lambda.testFramework.starter.newContextWithLambda
import com.intellij.lambda.testFramework.utils.IdeLambdaStarter
import com.intellij.lambda.testFramework.utils.IdeLambdaStarter.BackgroundRunWithLambda
import com.intellij.lambda.testFramework.utils.IdeLambdaStarter.runIdeWithLambda
import com.intellij.openapi.application.PathManager
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import java.nio.file.Paths
import kotlin.io.path.createDirectories

class MonolithAndSplitModeIdeInstanceInitializer : BeforeAllCallback, AfterAllCallback {
  companion object {
    lateinit var ideBackgroundRun: BackgroundRunWithLambda
  }

  override fun beforeAll(context: ExtensionContext) {
    val testContext = Starter.newContextWithLambda(context.testClass.get().simpleName,
                                                   UltimateTestCases.JpsEmptyProject,
                                                   additionalPluginModule = IdeLambdaStarter.ADDITIONAL_LAMBDA_TEST_PLUGIN)
    ideBackgroundRun = testContext.runIdeWithLambda()
  }

  override fun afterAll(context: ExtensionContext) {
    ideBackgroundRun.closeIdeAndWait()
  }
}


object UltimateTestCases : TestCaseTemplate(IdeProductProvider.IU) {
  val JpsEmptyProject: TestCase<LocalProjectInfo> = withProject(
    projectInfo = LocalProjectInfo(
      projectDir = Paths.get(PathManager.getHomePath(), "out/ide-tests/cache/empty-project").createDirectories()
    )
  )
}