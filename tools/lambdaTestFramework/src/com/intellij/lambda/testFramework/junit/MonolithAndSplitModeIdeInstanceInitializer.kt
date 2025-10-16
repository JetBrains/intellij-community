package com.intellij.lambda.testFramework.junit

import com.intellij.ide.starter.runner.Starter
import examples.data.TestCases
import com.intellij.lambda.testFramework.starter.newContextWithLambda
import com.intellij.lambda.testFramework.utils.IdeLambdaStarter
import com.intellij.lambda.testFramework.utils.IdeLambdaStarter.BackgroundRunWithLambda
import com.intellij.lambda.testFramework.utils.IdeLambdaStarter.runIdeWithLambda
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext

class MonolithAndSplitModeIdeInstanceInitializer : BeforeAllCallback, AfterAllCallback {
  companion object {
    lateinit var ideBackgroundRun: BackgroundRunWithLambda
  }

  override fun beforeAll(context: ExtensionContext) {
    val testContext = Starter.newContextWithLambda(context.testClass.get().simpleName,
                                                   TestCases.IU.JpsEmptyProject,
                                                   additionalPluginModule = IdeLambdaStarter.ADDITIONAL_LAMBDA_TEST_PLUGIN)
    ideBackgroundRun = testContext.runIdeWithLambda()
  }

  override fun afterAll(context: ExtensionContext) {
    ideBackgroundRun.closeIdeAndWait()
  }
}