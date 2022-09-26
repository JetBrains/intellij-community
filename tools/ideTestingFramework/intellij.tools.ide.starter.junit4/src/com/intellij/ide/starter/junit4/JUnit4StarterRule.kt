package com.intellij.ide.starter.junit4

import com.intellij.ide.starter.bus.StarterListener
import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.process.killOutdatedProcessesOnUnix
import com.intellij.ide.starter.runner.CurrentTestMethod
import com.intellij.ide.starter.runner.TestContainer
import com.intellij.ide.starter.utils.catchAll
import com.intellij.ide.starter.utils.logError
import com.intellij.ide.starter.utils.logOutput
import com.intellij.ide.starter.utils.withIndent
import org.junit.rules.ExternalResource
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.kodein.di.direct
import org.kodein.di.instance

fun initStarterRule(): JUnit4StarterRule = JUnit4StarterRule(useLatestDownloadedIdeBuild = false)

open class JUnit4StarterRule(
  override var useLatestDownloadedIdeBuild: Boolean,
  override val setupHooks: MutableList<IDETestContext.() -> IDETestContext> = mutableListOf(),
  override val ciServer: CIServer = di.direct.instance()
) : ExternalResource(), TestContainer<JUnit4StarterRule> {

  override lateinit var testContext: IDETestContext

  private lateinit var testDescription: Description

  override fun apply(base: Statement, description: Description): Statement {
    testDescription = description

    try {
      val testMethod = testDescription.testClass.getMethod(testDescription.methodName)
      di.direct.instance<CurrentTestMethod>().set(testMethod)
    }
    catch (_: Exception) {
      logError("Couldn't acquire test method")
    }

    return super.apply(base, description)
  }

  /**
   * Before each
   */
  override fun before() {
    if (ciServer.isBuildRunningOnCI) {
      logOutput(buildString {
        appendLine("Disk usage diagnostics before test ${testDescription.displayName}")
        appendLine(di.direct.instance<GlobalPaths>().getDiskUsageDiagnostics().withIndent("  "))
      })
    }

    killOutdatedProcessesOnUnix()

    super.before()
  }

  override fun close() {
    catchAll { testContext.paths.close() }
  }

  /**
   * After each
   */
  override fun after() {
    StarterListener.unsubscribe()
    close()
    super.after()
  }
}



