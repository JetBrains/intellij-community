package com.intellij.ide.starter.ide

import com.intellij.ide.starter.models.VMOptions
import com.intellij.ide.starter.process.exec.ExecOutputRedirect
import com.intellij.ide.starter.process.exec.ProcessExecutor
import com.intellij.ide.starter.utils.FileSystem.listDirectoryEntriesQuietly
import com.intellij.ide.starter.utils.JvmUtils
import com.intellij.openapi.util.SystemInfo
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

    fun linuxCommandLine(xvfbRunLog: Path, vmOptions: VMOptions): List<String> {
      return when {
        System.getenv("DISPLAY") != null || vmOptions.environmentVariables["DISPLAY"] != null || vmOptions.hasHeadlessMode() -> listOf()
        else ->
          //hint https://gist.github.com/tullmann/2d8d38444c5e81a41b6d
          listOf(
            xvfbRunTool,
            "--error-file=" + xvfbRunLog.toAbsolutePath().toString(),
            "--server-args=-ac -screen 0 ${DEFAULT_DISPLAY_RESOLUTION}x24",
            "--auto-servernum",
            "--server-num=$DEFAULT_DISPLAY_ID"
          )
      }
    }

    fun createXvfbRunLog(logsDir: Path): Path {
      val logTxt = logsDir.resolve("xvfb-log.txt")
      logTxt.deleteIfExists()

      return Files.createFile(logTxt)
    }
  }

  override fun installIde(unpackDir: Path, executableFileName: String): InstalledIde {
    require(SystemInfo.isLinux) { "Can only run on Linux, docker is possible, please PR" }

    val appHome = (unpackDir.listDirectoryEntriesQuietly()?.singleOrNull { it.isDirectory() } ?: unpackDir).toAbsolutePath()
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