package com.intellij.lambda.testFramework.starter

import com.intellij.ide.starter.ide.IdeRunMode
import com.intellij.ide.starter.driver.engine.BackgroundRun
import com.intellij.ide.starter.driver.reused.ReusedSessionStrategy
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.testContextCache.CachedProjectConfiguration
import com.intellij.ide.starter.testContextCache.CachedTestContext
import com.intellij.ide.starter.project.NoProject
import com.intellij.ide.starter.project.ProjectInfoSpec
import com.intellij.ide.starter.project.TestCaseTemplate
import com.intellij.lambda.testFramework.junit.getCurrentRunMode
import com.intellij.lambda.testFramework.junit.getCurrentRunModeOrNull
import com.intellij.lambda.testFramework.project.TestAppProject
import com.intellij.lambda.testFramework.utils.IdeWithLambda
import com.intellij.lambda.testFramework.utils.LambdaTestPluginHolder
import com.intellij.tools.ide.starter.product.idea.ultimate.IdeaUltimate
import org.junit.jupiter.api.extension.ExtensionContext
import kotlin.time.Duration

open class LambdaReusedSessionStrategy : ReusedSessionStrategy {
  /**
   * Which plugins this session's IDE is built with, and which module its in-IDE lambdas are loaded from.
   *
   * Read per session rather than from process-wide state, so a product's declaration cannot outlive the session
   * that made it and be inherited by whatever runs next in the same JVM. Override in a product whose test
   * classes are generated, and therefore cannot be listed in [LambdaTestPluginHolder.setupFor]; build the setup
   * with [LambdaTestPluginHolder.allModesPlugin].
   */
  open fun lambdaTestSetup(context: ExtensionContext): LambdaTestPluginHolder.LambdaTestSetup =
    LambdaTestPluginHolder.setupFor(context.requiredTestClass)

  override fun getOrCreateCachedTestContext(context: ExtensionContext, projectConfiguration: CachedProjectConfiguration): CachedTestContext {
    return LambdaIdeSession.getOrCreateCachedTestContext(context, projectConfiguration)
  }

  override fun reusedIdeSessionKey(context: ExtensionContext): Any {
    val mode = getCurrentRunModeOrNull(context) ?: return context.requiredTestClass
    return LambdaReusedIdeSessionKey(context.requiredTestClass, mode)
  }

  override fun currentRunMode(context: ExtensionContext): IdeRunMode {
    return getCurrentRunMode(context)
  }

  override fun injectedParameterTypes(): Set<Class<*>> = super.injectedParameterTypes() + IdeWithLambda::class.java

  override fun startIde(context: ExtensionContext, testContext: IDETestContext, ideRunTimeout: Duration): BackgroundRun {
    return LambdaIdeSession.startIde(context, testContext, ideRunTimeout)
  }

  override fun beforeAllOnIde(context: ExtensionContext, bgRun: BackgroundRun) {
    runLambdaLifecycleCallback(context, LambdaLifecycleCallback.BEFORE_ALL)
  }

  override fun beforeEachOnIde(context: ExtensionContext, bgRun: BackgroundRun) {
    runLambdaLifecycleCallback(context, LambdaLifecycleCallback.BEFORE_EACH)
  }

  override fun afterEachOnIde(context: ExtensionContext, bgRun: BackgroundRun) {
    runLambdaLifecycleCallback(context, LambdaLifecycleCallback.AFTER_EACH)
  }

  override fun afterAllOnIde(context: ExtensionContext, bgRun: BackgroundRun) {
    runLambdaLifecycleCallback(context, LambdaLifecycleCallback.AFTER_ALL)
  }
}

val lambdaNoProjectConfiguration: CachedProjectConfiguration = CachedProjectConfiguration(
  testCase = testCaseWithProject(NoProject),
)

val lambdaTestAppConfiguration: CachedProjectConfiguration = CachedProjectConfiguration(
  testCase = testCaseWithProject(TestAppProject),
)

val lambdaUnitTestModeNoProjectConfiguration: CachedProjectConfiguration = CachedProjectConfiguration(
  testCase = testCaseWithProject(NoProject),
  setup = {
    disableAIAssistantToolwindowActivationOnStart()
    applyVMOptionsPatch { inUnitTestMode() }
  },
)

val lambdaNoHeadlessTestAppConfiguration: CachedProjectConfiguration = CachedProjectConfiguration(
  testCase = testCaseWithProject(TestAppProject),
  setup = {
    disableAIAssistantToolwindowActivationOnStart()
    applyVMOptionsPatch { clearSystemProperty("java.awt.headless") }
  },
)

private fun testCaseWithProject(project: ProjectInfoSpec) = (object : TestCaseTemplate(IdeInfo.IdeaUltimate) {}).withProject(project)

private data class LambdaReusedIdeSessionKey(val testClass: Class<*>, val mode: IdeRunMode)
