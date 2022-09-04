package com.intellij.ide.starter.tests.examples.junit4

import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.di.di
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.ide.starter.process.killOutdatedProcessesOnUnix
import com.intellij.ide.starter.runner.TestContainer
import com.intellij.ide.starter.system.SystemInfo
import com.intellij.ide.starter.utils.catchAll
import com.intellij.ide.starter.utils.logOutput
import com.intellij.ide.starter.utils.withIndent
import org.junit.rules.ExternalResource
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.kodein.di.direct
import org.kodein.di.instance

fun initStarterRule(): JUnit4StarterRule = JUnit4StarterRule(useLatestDownloadedIdeBuild = false)

class JUnit4StarterRule(
  override var useLatestDownloadedIdeBuild: Boolean,
  override var allContexts: MutableList<IDETestContext> = mutableListOf(),
  override val setupHooks: MutableList<IDETestContext.() -> IDETestContext> = mutableListOf(),
  override val ciServer: CIServer = di.direct.instance()
) : ExternalResource(), TestContainer<JUnit4StarterRule> {

  private lateinit var testDescription: Description

  override fun apply(base: Statement, description: Description): Statement {
    testDescription = description
    return super.apply(base, description)
  }

  override fun before() {
    if (ciServer.isBuildRunningOnCI) {
      logOutput(buildString {
        appendLine("Disk usage diagnostics before test ${testDescription.displayName}")
        appendLine(di.direct.instance<GlobalPaths>().getDiskUsageDiagnostics().withIndent("  "))
      })
    }

    if (!SystemInfo.isWindows) {
      killOutdatedProcessesOnUnix()
    }
  }

  override fun close() {
    for (context in allContexts) {
      catchAll { context.paths.close() }
    }
  }

  override fun after() {
    super.after()
    close()
  }
}



