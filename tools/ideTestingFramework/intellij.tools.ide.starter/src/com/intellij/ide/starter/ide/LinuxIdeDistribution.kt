package com.intellij.ide.starter.ide

import com.intellij.ide.starter.models.VMOptions
import com.intellij.ide.starter.process.exec.ExecOutputRedirect
import com.intellij.ide.starter.process.exec.ProcessExecutor
import com.intellij.ide.starter.system.SystemInfo
import com.intellij.ide.starter.utils.callJavaVersion
import com.intellij.ide.starter.utils.logOutput
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.time.Duration.Companion.seconds

class LinuxIdeDistribution : IdeDistribution() {
  companion object {

    private val xvfbRunTool by lazy {
      val toolName = "xvfb-run"

      val homePath = Path(System.getProperty("user.home")).toAbsolutePath()
      ProcessExecutor("xvfb-run", homePath, timeout = 5.seconds, args = listOf("which", toolName),
                      stdoutRedirect = ExecOutputRedirect.ToStdOut("xvfb-run-out"),
                      stderrRedirect = ExecOutputRedirect.ToStdOut("xvfb-run-err")
      ).start()
      toolName
    }

    fun linuxCommandLine(xvfbRunLog: Path): List<String> {
      return when {
        System.getenv("DISPLAY") != null -> listOf()
        else ->
          //hint https://gist.github.com/tullmann/2d8d38444c5e81a41b6d
          listOf(
            xvfbRunTool,
            "--error-file=" + xvfbRunLog.toAbsolutePath().toString(),
            "--server-args=-ac -screen 0 1920x1080x24",
            "--auto-servernum",
            "--server-num=88"
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

    val appHome = (unpackDir.toFile().listFiles()?.singleOrNull { it.isDirectory }?.toPath() ?: unpackDir)

    val buildTxtPath = appHome.resolve("build.txt")
    require(buildTxtPath.isRegularFile()) { "Cannot find LinuxOS IDE vmoptions file in $unpackDir" }
    val (productCode, build) = buildTxtPath.readText().trim().split("-", limit = 2)

    val binDir = appHome / "bin"
    val allBinFiles = binDir.listDirectoryEntries()
    val executablePath = allBinFiles.singleOrNull { file ->
      file.fileName.toString() == "$executableFileName.sh"
    } ?: error("Failed to detect IDE executable .sh in:\n${allBinFiles.joinToString("\n")}")

    return object : InstalledIde {
      override val bundledPluginsDir = appHome.resolve("plugins")

      val originalVMOptionsFile = executablePath.parent.resolve(
        executablePath.fileName.toString().removeSuffix(".sh") + "64.vmoptions") //TODO: which file to pick with 64 or without?
      override val originalVMOptions = VMOptions.readIdeVMOptions(this, originalVMOptionsFile)
      override val patchedVMOptionsFile = appHome.parent.resolve("${appHome.fileName}.vmoptions")

      override fun startConfig(vmOptions: VMOptions, logsDir: Path) =
        object : InstalledBackedIDEStartConfig(patchedVMOptionsFile, vmOptions) {

          override val environmentVariables: Map<String, String>
            get() = super.environmentVariables.filterKeys {
              when {
                it.startsWith("DESKTOP") -> false
                it.startsWith("DBUS") -> false
                it.startsWith("APPIMAGE") -> false
                it.startsWith("DEFAULTS_PATH") -> false
                it.startsWith("GDM") -> false
                it.startsWith("GNOME") -> false
                it.startsWith("GTK") -> false
                it.startsWith("MANDATORY_PATH") -> false
                it.startsWith("QT") -> false
                it.startsWith("SESSION") -> false
                it.startsWith("TOOLBOX_VERSION") -> false
                it.startsWith("XAUTHORITY") -> false
                it.startsWith("XDG") -> false
                it.startsWith("XMODIFIERS") -> false
                it.startsWith("GPG_") -> false
                it.startsWith("CLUTTER_IM_MODULE") -> false
                it.startsWith("APPDIR") -> false
                it.startsWith("LC") -> false
                it.startsWith("SSH") -> false
                else -> true
              }
            } + ("LC_ALL" to "en_US.UTF-8")

          val xvfbRunLog = createXvfbRunLog(logsDir)

          override val errorDiagnosticFiles = listOf(xvfbRunLog)
          override val workDir = appHome
          override val commandLine: List<String> = linuxCommandLine(xvfbRunLog) + executablePath.toAbsolutePath().toString()
        }

      override val build = build
      override val os = "linux"
      override val productCode = productCode
      override val isFromSources = false

      override fun toString() = "IDE{$productCode, $build, $os, home=$unpackDir}"
      override fun resolveAndDownloadTheSameJDK(): Path {
        val jbrHome = appHome.resolve("jbr")
        require(jbrHome.isDirectory()) {
          "JbrHome is not found under $jbrHome"
        }

        val jbrFullVersion = callJavaVersion(jbrHome).substringAfter("build ").substringBefore(")")
        logOutput("Found following $jbrFullVersion in the product: $productCode $build")
        // in Android Studio bundled only JRE
        if (productCode == IdeProductProvider.AI.productCode) return jbrHome
        return downloadAndUnpackJbrIfNeeded(jbrFullVersion)
      }
    }
  }
}