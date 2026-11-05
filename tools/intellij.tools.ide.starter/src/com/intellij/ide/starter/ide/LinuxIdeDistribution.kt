package com.intellij.ide.starter.ide

import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.config.ConfigurationStorage
import com.intellij.ide.starter.config.useDockerContainer
import com.intellij.ide.starter.models.VMOptions
import com.intellij.ide.starter.process.exec.ExecOutputRedirect
import com.intellij.ide.starter.process.exec.ProcessExecutor
import com.intellij.ide.starter.utils.FileSystem.listDirectoryEntriesQuietly
import com.intellij.ide.starter.utils.JvmUtils
import com.intellij.openapi.util.SystemInfo
import com.intellij.platform.testFramework.teamCity.TeamCityReporter.SyntheticTestKind
import com.intellij.tools.ide.util.common.logOutput
import com.intellij.util.system.OS
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isExecutable
import kotlin.io.path.readLines
import kotlin.time.Duration.Companion.seconds

const val DEFAULT_DISPLAY_ID = "88"

class LinuxIdeDistribution : IdeDistribution() {
  companion object {
    private const val DEFAULT_DISPLAY_RESOLUTION = "1920x1080"
    internal const val XVFB_TOOL_NAME: String = "xvfb-run"
    private val xvfbRunTool: String by lazy {
      val homePath = Path(System.getProperty("user.home")).toAbsolutePath()
      ProcessExecutor(XVFB_TOOL_NAME, homePath, timeout = 5.seconds, args = listOf("which", XVFB_TOOL_NAME),
                      stdoutRedirect = ExecOutputRedirect.ToStdOut("$XVFB_TOOL_NAME-out"),
                      stderrRedirect = ExecOutputRedirect.ToStdOut("$XVFB_TOOL_NAME-err")
      ).start()
      XVFB_TOOL_NAME
    }

    private const val FLUXBOX_LOG_NAME = "fluxbox.log"

    fun linuxCommandLine(xvfbRunLog: Path, vmOptions: VMOptions): List<String> {
      if (vmOptions.hasHeadlessMode()) return listOf()
      val windowManagerCommandLine = listOf("/bin/sh", "-c", windowManagerScript, "sh",
                                            xvfbRunLog.resolveSibling(FLUXBOX_LOG_NAME).toAbsolutePath().toString())
      return when {
        ConfigurationStorage.useDockerContainer()
          || System.getenv("DISPLAY") != null
          || vmOptions.environmentVariables["DISPLAY"] != null -> windowManagerCommandLine
        else ->
          //hint https://gist.github.com/tullmann/2d8d38444c5e81a41b6d
          listOf(
            xvfbRunTool,
            "--error-file=" + xvfbRunLog.toAbsolutePath().toString(),
            "--server-args=-ac -screen 0 ${DEFAULT_DISPLAY_RESOLUTION}x24",
            "--auto-servernum",
            "--server-num=$DEFAULT_DISPLAY_ID"
          ) + windowManagerCommandLine
      }
    }

    /** Reports a test infra exception when the IDE of the run in [logsDir] started before fluxbox became the window manager. */
    fun reportMissingWindowManager(logsDir: Path) {
      val lines = runCatching { logsDir.resolve(FLUXBOX_LOG_NAME).readLines() }.getOrNull() ?: return
      // fluxbox writes to the same log, so the message can follow a line of fluxbox without a line break
      val index = lines.indexOfFirst { NO_WINDOW_MANAGER_MESSAGE in it }
      if (index < 0) return
      CIServer.instance.reportTestFailure(testName = "The IDE started before fluxbox became the window manager",
                                          message = lines.drop(index).take(2).joinToString("\n") +
                                                    "\nThe JBR saw no window manager at its start (IJPL-246949).",
                                          details = "",
                                          generifyTestName = false,
                                          kind = SyntheticTestKind.TEST_INFRA_EXCEPTION)
    }

    fun createXvfbRunLog(logsDir: Path): Path {
      val logTxt = logsDir.resolve("xvfb-log.txt")
      logTxt.deleteIfExists()

      return Files.createFile(logTxt)
    }
  }

  override fun installIde(unpackDir: Path, executableFileName: String): InstalledIde {
    require(SystemInfo.isLinux || ConfigurationStorage.useDockerContainer()) { "Can only run on Linux or in docker" }

    val appHome = (unpackDir.listDirectoryEntriesQuietly()?.singleOrNull { it.isDirectory() } ?: unpackDir).toAbsolutePath()
    JBRResolver.applyInstallerJbrOverrideIfLocalJbrPathNotEmpty(appHome)
    val (productCode, build) = readProductCodeAndBuildNumberFromBuildTxt(appHome.resolve("build.txt"))

    val executablePath = listOf(appHome / "bin" / executableFileName, appHome / "bin" / "$executableFileName.sh")
                           .firstOrNull { it.exists() && it.isExecutable() }
                         ?: error("Neither ${appHome / "bin" / executableFileName} nor ${appHome / "bin" / "$executableFileName.sh"} is executable or exists")

    return object : InstalledIde {
      override val bundledPluginsDir = appHome.resolve("plugins")

      private val vmOptionsFinal: VMOptions = VMOptions(
        ide = this,
        data = emptyList(),
        env = emptyMap()
      )

      override val vmOptions: VMOptions
        get() = vmOptionsFinal

      override val patchedVMOptionsFile = appHome.parent.resolve("${appHome.fileName}.vmoptions")

      override fun startConfig(vmOptions: VMOptions, logsDir: Path): IDEStartConfig {
        val xvfbRunLog = createXvfbRunLog(logsDir)
        return object : InstalledBackedIDEStartConfig(patchedVMOptionsFile, vmOptions) {
          override val errorDiagnosticFiles = listOf(xvfbRunLog)
          override val workDir = appHome
          override val commandLine: List<String> = linuxCommandLine(xvfbRunLog, vmOptions) + executablePath.toAbsolutePath().toString()
        }
      }

      override val build = build
      override val os = OS.Linux
      override val productCode = productCode
      override val isFromSources = false
      override val installationPath: Path = appHome

      override fun toString() = "IDE{$productCode, $build, $os, home=$unpackDir}"

      override suspend fun resolveAndDownloadTheSameJDK(): Path {
        val jbrHome = appHome.resolve("jbr")
        require(jbrHome.isDirectory()) {
          "JbrHome is not found under $jbrHome"
        }

        val jbrFullVersion = JvmUtils.callJavaVersion(jbrHome).substringAfter("build ").substringBefore(")")
        logOutput("Found following $jbrFullVersion in the product: $productCode $build")
        return jbrHome
      }
    }
  }
}