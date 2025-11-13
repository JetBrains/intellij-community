package com.intellij.ide.starter.driver.engine

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.impl.JmxCallException
import com.intellij.driver.model.LockSemantics
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.ui.ui
import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.ci.teamcity.TeamCityCIServer
import com.intellij.ide.starter.ci.teamcity.TeamCityClient
import com.intellij.ide.starter.config.ConfigurationStorage
import com.intellij.ide.starter.config.useDockerContainer
import com.intellij.ide.starter.report.FailureDetailsOnCI
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.ide.starter.runner.events.IdeLaunchEvent
import com.intellij.ide.starter.utils.replaceSpecialCharactersWithHyphens
import com.intellij.tools.ide.starter.bus.EventsBus
import com.intellij.tools.ide.util.common.logError
import com.intellij.tools.ide.util.common.logOutput
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

internal class DriverWithDetailedLogging(private val driver: Driver, logUiHierarchy: Boolean = true) : Driver by driver {
  private var runContext: IDERunContext? = null

  init {
    EventsBus.subscribeOnce(this) { event: IdeLaunchEvent ->
      runContext = event.runContext
      if (!CIServer.instance.isBuildRunningOnCI && !ConfigurationStorage.useDockerContainer()) {
        withTimeoutOrNull(1.minutes) {
          while (!driver.isConnected) {
            delay(3.seconds)
          }
          if (logUiHierarchy) {
            driver.withContext {
              val webserverPort = utility(BuiltInServerOptions::class).getInstance().getEffectiveBuiltInServerPort()
              logOutput("UI Hierarchy: http://localhost:$webserverPort/api/remote-driver/".color(LogColor.PURPLE))
            }
          }
        }
      }
    }
  }

  override fun <T> withContext(dispatcher: OnDispatcher, semantics: LockSemantics, code: Driver.() -> T): T {
    return driver.withContext(dispatcher, semantics) {
      try {
        code()
      }
      catch (e: Throwable) {
        throw detailedException(e)
      }
    }
  }

  private fun createErrorScreenshotOrNull(): String? {
    try {
      return this.takeScreenshot("driverError")
    }
    catch (_: JmxCallException) {
      return null
    }
  }

  private fun saveHierarchy(path: String) {
    try {
      ui.robotProvider.saveHierarchy(path)
    }
    catch (_: Throwable) {
    }
  }

  private fun detailedException(e: Throwable): DriverWithContextError {
    val screenshotPath = createErrorScreenshotOrNull()
    runContext?.let {
      saveHierarchy((it.logsDir / "ui-hierarchy").also { dir -> runCatching { dir.createDirectories() } }.toString())
    }
    val detailedMessage = buildString {
      append("\n----Driver Error----\n".color(LogColor.PURPLE))
      append("${e.message}\n".color(LogColor.BLUE))
      append("${e::class.simpleName}\n")
      e.stackTrace.firstOrNull { it.fileName?.endsWith("Test.kt") == true }?.let {
        append("\tat $it\n")
      }
      screenshotPath?.let {
        val path = Path(screenshotPath)
        if (!path.isRegularFile()) {
          logError("screenshot should be a regular file, but it is not: $screenshotPath")
        }
        else if (!CIServer.instance.isBuildRunningOnCI) {
          append("Screenshot: file://${path.invariantSeparatorsPathString}\n".color(LogColor.BLUE))
        }
        else {
          runContext?.let { context ->
            val artifactPath = context.contextName.replaceSpecialCharactersWithHyphens()
            val artifactName = path.name.replaceSpecialCharactersWithHyphens()
            logOutput("Adding screenshot to metadata: $artifactPath/$artifactName")
            TeamCityClient.publishTeamCityArtifacts(path, artifactPath, artifactName, false)
            TeamCityCIServer.addTestMetadata(testName = null, TeamCityCIServer.TeamCityMetadataType.IMAGE, flowId = null, name = null, value = "$artifactPath/$artifactName")
          }
        }
      }
      if (CIServer.instance.isBuildRunningOnCI) {
        runContext?.let {
          FailureDetailsOnCI.instance.getLinkToCIArtifacts(it)?.let { link ->
            append("Artifacts: $link\n")
          }
        }
      }
      append("Driver documentation: community/platform/remote-driver/README.md\n".color(LogColor.BLUE))
      append("--------------------".color(LogColor.PURPLE))
    }
    return DriverWithContextError(detailedMessage, e)
  }
}

class DriverWithContextError(message: String, e: Throwable) : AssertionError(message, e)


@Remote("org.jetbrains.builtInWebServer.BuiltInServerOptions")
private interface BuiltInServerOptions {
  fun getInstance(): BuiltInServerOptions
  fun getEffectiveBuiltInServerPort(): Int
}

internal fun String.color(color: LogColor): String {
  return "${color.key}$this${LogColor.RESET.key}"
}

@Suppress("unused")
internal enum class LogColor(val key: String) {
  RESET("\u001B[0m"),
  BLACK("\u001B[30m"),
  RED("\u001B[31m"),
  GREEN("\u001B[32m"),
  YELLOW("\u001B[33m"),
  BLUE("\u001B[34m"),
  PURPLE("\u001B[35m"),
  CYAN("\u001B[36m"),
  WHITE("\u001B[37m")
}