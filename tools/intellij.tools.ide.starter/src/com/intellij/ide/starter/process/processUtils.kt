@file:Suppress("RAW_RUN_BLOCKING", "OPT_IN_USAGE")

package com.intellij.ide.starter.process

import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.ide.LinuxIdeDistribution
import com.intellij.ide.starter.path.IDE_TESTS_SUBSTRING
import com.intellij.ide.starter.process.ProcessInfo.Companion.toProcessInfo
import com.intellij.ide.starter.process.ProcessKiller.killProcesses
import com.intellij.ide.starter.process.exec.ExecOutputRedirect
import com.intellij.ide.starter.process.exec.ProcessExecutor
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.platform.testFramework.teamCity.TeamCityReporter.SyntheticTestKind
import com.intellij.tools.ide.util.common.PrintFailuresMode
import com.intellij.tools.ide.util.common.logOutput
import com.intellij.tools.ide.util.common.withRetry
import com.intellij.util.system.OS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import oshi.SystemInfo
import oshi.software.os.OSProcess
import java.io.IOException
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.util.function.Predicate
import kotlin.io.path.absolutePathString
import kotlin.io.path.isRegularFile
import kotlin.io.path.readLines
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTimedValue

suspend fun getProcessList(processName: String? = null): List<ProcessInfo> =
  if (processName == null) getFilteredProcessList()
  else {
    getFilteredProcessList { p ->
      p.name == processName
    }
  }

suspend fun getProcessList(vararg substringToSearch: String): List<ProcessInfo> =
  getFilteredProcessList { p ->
    substringToSearch.isEmpty() || p.arguments.any { arg -> substringToSearch.any { arg.contains(it) } }
  }

suspend fun getProcessList(filter: Predicate<ProcessInfo>): List<ProcessInfo> =
  getFilteredProcessList().filter(filter::test)

private suspend fun getFilteredProcessList(filter: Predicate<OSProcess>? = null): List<ProcessInfo> = withContext(Dispatchers.IO) {
  SystemInfo().operatingSystem.getProcesses(
    { p -> p.state != OSProcess.State.INVALID && (filter == null || filter.test(p)) },
    null,
    0,
  )
    .map { it.toProcessInfo() }
}

private suspend fun getChildProcessList(parentPid: Long): List<ProcessInfo> =
  getFilteredProcessList { p -> p.parentProcessID.toLong() == parentPid }

/**
 * Identifies and terminates any leftover processes from previous test runs, specifically those
 * whose command lines contain specific substrings indicative of test runs.
 *
 * CI may not kill processes started during the build (for TeamCity: TW-69045).
 * They stay alive and consume resources after tests.
 * This lead to OOM and other errors during tests, for example,
 * IDEA-256265: shared-indexes tests on Linux suspiciously fail with 137 (killed by OOM)
 */
suspend fun findAndKillLeftoverProcessesFromTestRuns(reportErrors: Boolean = false, testClassWithLeftoverProcesses: String? = null) {
  findAndKillProcessesBySubPathInArguments(IDE_TESTS_SUBSTRING) { processes ->
    if (reportErrors) {
      processes.reportEachOutdatedProcessSeparately(testClassWithLeftoverProcesses)
    }
  }
}

private fun List<ProcessInfo>.reportEachOutdatedProcessSeparately(testClassWithLeftoverProcesses: String? = null) {
  groupBy { it.name }.apply {
    forEach {
      val message = "Unexpected running '${it.key}' process was detected after IDE was stopped"
      CIServer.instance.reportTestFailure(testName = message,
                                          generifyTestName = false,
                                          kind = SyntheticTestKind.TEST_INFRA_EXCEPTION,
                                          details = "",
                                          message = (testClassWithLeftoverProcesses?.let { "Test class where leftover processes were detected: [$it]\n" }
                                            .orEmpty()) +
                                                    "$message. Amount of processes: ${it.value.size}.\n" +
                                                    "Outdated process details:\n${it.value.joinToString("\n") { p -> p.description }}\n\n" +
                                                    "Please investigate if the processes should have been stopped together with the IDE, it means it is a bug, you can raise a YT ticket and mute the exception.\n" +
                                                    "If it is an expected behaviour, it is recommended to add a call `\${::findAndKillProcesses}` with appropriate arguments in @After/@AfterEach."
      )
    }
  }
}

/**
 * Finds and kills processes with command lines that contain any of the specified substrings.
 *
 * @param substringToSearch Vararg of substrings to search for within process command lines.
 *                          Processes matching any of these substrings will be identified for termination.
 * @param onFoundProcesses A callback invoked with the list of `ProcessInfo` objects corresponding
 *                         to the found processes before attempting to kill them. Defaults to an empty callback.
 * @return `true` if all targeted processes were successfully killed or none were detected; `false` otherwise.
 */
suspend fun findAndKillProcessesBySubstring(vararg substringToSearch: String, onFoundProcesses: (List<ProcessInfo>) -> Unit = {}) {
  return findAndKillProcesses(message = "Killing process containing '${substringToSearch.joinToString(",")}' in command line",
                              filter = { p -> p.arguments.any { arg -> substringToSearch.any { arg.contains(it) } } },
                              onFoundProcesses = onFoundProcesses)
}


suspend fun findAndKillProcessesBySubPathInArguments(pathToSearch: String, onFoundProcesses: (List<ProcessInfo>) -> Unit = {}) {
  val regex = Regex("""(^|[\\/])${Regex.escape(pathToSearch)}([\\/]|$)""")
  return findAndKillProcesses(message = "Killing process containing subpath '${pathToSearch}' in arguments",
                              filter = { p -> p.arguments.any { regex.containsMatchIn(it) } },
                              onFoundProcesses = onFoundProcesses)
}

suspend fun findAndKillProcessesByName(nameToSearch: String, onFoundProcesses: (List<ProcessInfo>) -> Unit = {}) {
  return findAndKillProcesses(message = "Killing process with name '$nameToSearch'",
                              processName = nameToSearch,
                              onFoundProcesses = onFoundProcesses,
                              filter = { true })
}

suspend fun findAndKillProcesses(
  message: String? = null,
  processName: String? = null,
  filter: Predicate<ProcessInfo>,
  onFoundProcesses: (List<ProcessInfo>) -> Unit = {},
) {
  val prefix = message ?: "Killing process matching '$filter' in command line"
  logOutput("$prefix ...")
  val processInfosToKill = processName?.let { getProcessList(processName = it).filter { filter.test(it) } }
                           ?: getProcessList(filter)

  if (processInfosToKill.any { it.pid == ProcessHandle.current().pid() }) {
    error("The filter has resolved the current process")
  }

  if (processInfosToKill.isNotEmpty()) {
    onFoundProcesses.invoke(processInfosToKill)
    logOutput("$prefix: These processes will be killed: ${processInfosToKill.joinToString("\n") { it.description }}")
    killProcesses(processInfosToKill)
  }
  else {
    logOutput("$prefix: no processes were detected")
  }
}

private val devBuildArgumentsSet = setOf(
  "com.intellij.idea.Main",
  "com.intellij.platform.runtime.loader.IntellijLoader" // thin client
)

private fun ProcessInfo.isIde(runContext: IDERunContext): Boolean =
  /** for installer runs
   * Example:
   *  Name: idea
   *  Arguments: [/mnt/agent/temp/buildTmp/testb0bv1hja1z5rg/ide-tests/cache/builds/IU-installer-from-file/idea-IU-261.1243/bin/idea, serverMode,
   *    /mnt/agent/temp/buildTmp/testb0bv1hja1z5rg/ide-tests/cache/projects/unpacked/TestScopesProj]
   **/
  (name != LinuxIdeDistribution.XVFB_TOOL_NAME && arguments.firstOrNull()
    ?.startsWith(runContext.testContext.ide.installationPath.absolutePathString()) == true) ||
  /**  for dev build runs
   * Example:
   *  Name: java
   *  Arguments: [/mnt/agent/system/.persistent_cache/5tq0kti2dt-jbrsdk_jcef-21.0.8-linux-x64-b1173.3.tar.gz.2qppum.d/bin/java,
   *    @/mnt/agent/temp/buildTmp/testapcvq8gxezoyw/ide-tests/tmp/perf-vmOps-1760988642136-, ... com.intellij.idea.Main, /mnt/agent/temp/buildTmp/test8b25i2v1x4unr/ide-tests/cache/projects/unpacked/ui-tests-data/projects/catch_test_project_sample]
   **/
  (name == "java" && argumentsAreFromIdea())

private fun ProcessInfo.argumentsAreFromIdea(): Boolean {
  if (arguments.any { it in devBuildArgumentsSet }) {
    return true
  }
  // Check for Java `@argFile`
  return arguments
    .mapNotNull { if (it.startsWith("@") && it.length > 1) it.substring(1) else null }
    .any { fileName ->
      try {
        val argsFromFile = Path.of(fileName).readLines().map { it.trim() }
        argsFromFile.any { it in devBuildArgumentsSet }
      }
      catch (e: InvalidPathException) {
        logOutput("$fileName is invalid file name: $e")
        false
      }
      catch (e: IOException) {
        logOutput("$fileName is unreadable: $e")
        false
      }
    }
}

/*
  In most cases returns a real pid.
  In case the parent process has exited too quickly, returns null.
  In case we were trying to find the pid for some adequate time and failed, throws an exception.
 */
suspend fun getIdeProcessIdWithRetry(parentProcessInfo: ProcessInfo, runContext: IDERunContext): Long? {
  if (OS.CURRENT != OS.Linux) {
    return parentProcessInfo.pid
  }

  if (parentProcessInfo.isIde(runContext)) {
    logOutput("Parent process is an IDE process itself (was launched without wrapper)")
    return parentProcessInfo.pid
  }

  logOutput("Guessing IDE process ID on Linux: \n${parentProcessInfo.description}")

  val attemptsResult = measureTimedValue {
    withRetry(retries = 10,
              delay = 3.seconds,
              messageOnFailure = "Couldn't find appropriate IDE process id for pid ${parentProcessInfo.pid}",
              printFailuresMode = PrintFailuresMode.ALL_FAILURES) {
      getIdeProcessId(parentProcessInfo, runContext)
    }
  }

  if (attemptsResult.value == null) {
    if (attemptsResult.duration < 5.seconds && parentProcessInfo.processHandle?.isAlive != true) {
      //the processes finished too fast for us to determine the pid
      return null
    }
    else {
      error("Couldn't find appropriate IDE process id for pid ${parentProcessInfo.pid} in ${attemptsResult.duration}. " +
            "Means something is wrong with our diagnostics.")
    }
  }

  return attemptsResult.value
}


/**
 * On Linux we run IDE using `xvfb-run` tool wrapper, so we need to guess the real PID.
 * Thus, we must guess the IDE process ID for capturing the thread dumps.
 * In case of Dev Server, under xvfb-run the whole build process is happening so the waiting time can be long.
 */
private suspend fun getIdeProcessId(parentProcessInfo: ProcessInfo, runContext: IDERunContext): Long? {
  if (OS.CURRENT != OS.Linux) {
    return parentProcessInfo.pid
  }

  if (parentProcessInfo.processHandle?.isAlive != true) {
    logOutput("Couldn't guess IDE process: parent process is not alive")
    return null
  }

  logOutput("Guessing IDE process ID on Linux (pid of the IDE process wrapper ${parentProcessInfo.pid})")

  val children = getChildProcessList(parentProcessInfo.pid)
  val suitableChildren = children.filter { it.isIde(runContext) }

  if (suitableChildren.isEmpty()) {
    throw Exception("There are no suitable candidates for IDE process\n" +
                    "All children: \n" +
                    children.joinToString("\n") { it.description })
  }

  if (suitableChildren.size > 1) {
    logOutput("Found more than one IDE process candidates: " + suitableChildren.joinToString("\n") { it.description } +
              "Returning oldest suitable IDE process: ${suitableChildren.first()}")
    return suitableChildren.first().pid
  }
  else {
    logOutput("Returning single suitable IDE process: ${suitableChildren.single().description}")
    return suitableChildren.single().pid
  }
}

fun collectJavaThreadDump(
  javaHome: Path,
  workDir: Path?,
  javaProcessId: Long,
  dumpFile: Path,
) {
  runBlocking {
    collectJavaThreadDumpSuspendable(javaHome, workDir, javaProcessId, dumpFile)
  }
}


/**
 * DON'T ADD ANY LOGGING OTHERWISE IF STDOUT IS BLOCKED THERE WILL BE NO DUMPS
 */
suspend fun collectJavaThreadDumpSuspendable(
  javaHome: Path,
  workDir: Path?,
  javaProcessId: Long,
  dumpFile: Path,
) {
  val ext = if (OS.CURRENT == OS.Windows) ".exe" else ""
  val jstackPath = listOf(
    javaHome.resolve("bin/jstack$ext"),
    javaHome.parent.resolve("bin/jstack$ext")
  ).map { it.toAbsolutePath() }.firstOrNull { it.isRegularFile() } ?: error("Failed to locate jstack under $javaHome")

  val command = listOf(jstackPath.toAbsolutePath().toString(), "-l", javaProcessId.toString())

  try {
    ProcessExecutor(
      "jstack",
      workDir,
      timeout = 1.minutes,
      args = command,
      stdoutRedirect = ExecOutputRedirect.ToFile(dumpFile),
      stderrRedirect = ExecOutputRedirect.ToStdOut("[jstack-err]"),
      silent = true
    ).startCancellable()
  }
  catch (ise: IllegalStateException) {
    val message = ise.message ?: ""
    if (message.startsWith("External process `jstack` failed with code ")
        || message.startsWith("Shutdown in progress")) {
      logOutput("... " + ise.message)
    }
    else {
      throw ise
    }
  }
}

fun collectMemoryDump(
  javaHome: Path,
  workDir: Path,
  javaProcessId: Long,
  dumpFile: Path,
) {
  logOutput("Collecting memory dump to $dumpFile")
  val command = listOf("GC.heap_dump", "-gz=4", dumpFile.toString())
  jcmd(javaHome, workDir, javaProcessId, command)
}

fun jcmd(
  javaHome: Path,
  workDir: Path,
  javaProcessId: Long,
  command: List<String>,
) {
  val pathToJcmd = "bin/jcmd"
  val ext = if (OS.CURRENT == OS.Windows) ".exe" else ""
  val jcmdPath = listOf(
    javaHome.resolve("$pathToJcmd$ext"),
    javaHome.parent.resolve("$pathToJcmd$ext")
  ).map { it.toAbsolutePath() }.firstOrNull { it.isRegularFile() } ?: error("Failed to locate jcmd under $javaHome")

  val jcmdCommand = listOf(jcmdPath.toAbsolutePath().toString(), javaProcessId.toString()) + command
  ProcessExecutor(
    "jcmd",
    workDir,
    timeout = 5.minutes,
    args = jcmdCommand,
    stdoutRedirect = ExecOutputRedirect.ToStdOut("[jcmd-out]"),
    stderrRedirect = ExecOutputRedirect.ToStdOut("[jcmd-err]")
  ).start()

}

fun getAllJavaProcesses(): List<String> {
  val stdout = ExecOutputRedirect.ToString()
  ProcessExecutor(
    "get jps process",
    workDir = null,
    timeout = 30.seconds,
    args = listOf("jps", "-l"),
    stdoutRedirect = stdout
  ).start(printEnvVariables = CIServer.instance.isBuildRunningOnCI)

  logOutput("List of java processes: \n" + stdout.read())
  return stdout.read().split("\n")
}


fun getProcessesIdByProcessName(processName: String): Set<Long> {
  return getAllJavaProcesses().filter {
    it.contains(processName)
  }.map {
    it.split(" ").first().toLong()
  }.toSet()
}