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
    private const val NO_WINDOW_MANAGER_MESSAGE = "fluxbox is not the window manager"

    /**
     * Runs the IDE from its arguments. Before the IDE starts, it starts fluxbox when `DISPLAY` has no window manager.
     * The first argument is the fluxbox log. The IDE replaces the shell, so the IDE keeps the process of the start.
     * fluxbox becomes a child of the IDE and stops with the X server.
     */
    private val windowManagerScript = $$"""
      log="$1"
      shift
      socket_seconds=30
      xprop_seconds=2
      wm_seconds=10
      wm_attempts=3
      # a busy X server can block xprop, so each call gets a time limit
      limit=
      command -v timeout >/dev/null 2>&1 && limit="timeout $xprop_seconds"
      wm_check() { $limit xprop "$@" _NET_SUPPORTING_WM_CHECK 2>/dev/null | sed -n 's/.*window id # //p'; }
      # EWMH: the window must name itself, so a property of an exited window manager does not count
      wm_running() { id=$(wm_check -root); [ -n "$id" ] && [ "$(wm_check -id "$id")" = "$id" ]; }
      # the clock, not the number of checks, limits a wait, because one check can take two xprop calls
      seconds_since() { echo $(($(date +%s) - $1)); }
      # a DISPLAY without a colon, such as NoDisplay of a remote dev backend, names no X server
      if [ "${DISPLAY#*:}" != "$DISPLAY" ]; then
        if [ "${DISPLAY#:}" != "$DISPLAY" ]; then
          display="${DISPLAY#:}"
          display="${display%%.*}"
          start=$(date +%s)
          while [ ! -e "/tmp/.X11-unix/X$display" ] && [ $(seconds_since $start) -lt $socket_seconds ]; do
            sleep 0.5
          done
        fi
        if ! command -v xprop >/dev/null 2>&1 || ! command -v fluxbox >/dev/null 2>&1; then
          echo "xprop or fluxbox is not installed, so no window manager starts on $DISPLAY" >&2
        elif ! wm_running; then
          # on a loaded agent fluxbox can register only after many seconds, so only an exited fluxbox starts again
          attempt=0
          while [ $attempt -lt $wm_attempts ]; do
            attempt=$((attempt + 1))
            fluxbox >>"$log" 2>&1 &
            wm=$!
            start=$(date +%s)
            registered=false
            while :; do
              if wm_running; then registered=true; break; fi
              [ $(seconds_since $start) -lt $wm_seconds ] && kill -0 $wm 2>/dev/null || break
              sleep 0.5
            done
            # xprop can time out while a window manager runs, so the started fluxbox can lose to it
            if $registered; then
              echo "a window manager runs on $DISPLAY after about $(seconds_since $start) s" >&2
              break
            fi
            if kill -0 $wm 2>/dev/null; then
              tail -n 5 "$log" >&2
              # the log gets the lines too, so the run reports them; the state shows what fluxbox waits for
              {
                echo "$$NO_WINDOW_MANAGER_MESSAGE on $DISPLAY after $wm_seconds s, see $log"
                echo "fluxbox state: $(sed -n 's/^State:[[:space:]]*//p' /proc/$wm/status 2>/dev/null), wchan: $(cat /proc/$wm/wchan 2>/dev/null)"
              } | tee -a "$log" >&2
              break
            fi
            echo "fluxbox exited before it became the window manager on $DISPLAY, attempt $attempt/$wm_attempts, see $log" >&2
            if [ $attempt -eq $wm_attempts ]; then
              echo "$$NO_WINDOW_MANAGER_MESSAGE on $DISPLAY, because fluxbox exited $wm_attempts times, see $log" | tee -a "$log" >&2
            fi
          done
        fi
      fi
      exec "$@"
    """.trimIndent()

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
    require(SystemInfo.isLinux) { "Can only run on Linux, docker is possible, please PR" }

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