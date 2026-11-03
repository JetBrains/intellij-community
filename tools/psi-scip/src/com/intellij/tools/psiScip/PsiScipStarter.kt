// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.psiScip

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ModernApplicationStarter
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.project.waitForSmartMode
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.InheritedJdkOrderEntry
import com.intellij.openapi.roots.JdkOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.platform.backend.observation.Observation
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.util.SystemProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.file.Files
import java.nio.file.Path
import java.util.TreeSet
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.absolute
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.system.exitProcess
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private const val COMMAND = "psiScip"
private const val DEFAULT_JDK_NAME = "psi-scip JDK"
private const val OPEN_TIMEOUT_PROPERTY = "psi.scip.open.timeout.seconds"
private const val SMART_MODE_TIMEOUT_PROPERTY = "psi.scip.smart.mode.timeout.seconds"

private const val USAGE = """Usage: $COMMAND --project=<dir> --jdk=<JDK home> --output=<index.scip> [options]
  --project=<dir>        the project to open; a directory with .idea opens without import
  --jdk=<JDK home>       a full JDK with lib/src.zip; it is registered under every JDK name that the project asks for
  --output=<index.scip>  the SCIP index to write; the summary goes to <index>.summary.json
  --format=scip|json|both  json writes <index>.json in the shape of `scip print --json` (default: scip)
  --files-from=<file>    relative paths of the candidate files, one per line (default: git ls-files)
  --threads=<n>          files collected in parallel (default: min(14, processors))"""

private class ExportOptions(
  @JvmField val project: Path,
  @JvmField val jdk: Path,
  @JvmField val output: Path,
  @JvmField val writeScip: Boolean,
  @JvmField val writeJson: Boolean,
  @JvmField val filesFrom: Path?,
  @JvmField val threads: Int,
) {
  companion object {
    fun parse(args: List<String>): ExportOptions {
      val values = HashMap<String, String>()
      for (arg in args) {
        require(arg.startsWith("--") && arg.contains('=')) { "unknown argument: $arg" }
        values[arg.substring(2, arg.indexOf('='))] = arg.substring(arg.indexOf('=') + 1)
      }
      val unknown = values.keys - setOf("project", "jdk", "output", "format", "files-from", "threads")
      require(unknown.isEmpty()) { "unknown options: $unknown" }
      val project = Path.of(requireNotNull(values["project"]) { "--project is required" }).absolute().normalize()
      val jdk = Path.of(requireNotNull(values["jdk"]) { "--jdk is required" }).absolute().normalize()
      require(Files.isRegularFile(jdk.resolve("lib/src.zip"))) { "--jdk must be a full JDK with lib/src.zip: $jdk" }
      val output = Path.of(requireNotNull(values["output"]) { "--output is required" }).absolute().normalize()
      val format = values["format"] ?: "scip"
      require(format in setOf("scip", "json", "both")) { "--format must be scip, json or both" }
      val threads = values["threads"]?.toInt() ?: minOf(14, Runtime.getRuntime().availableProcessors())
      return ExportOptions(
        project = project,
        jdk = jdk,
        output = output,
        writeScip = format != "json",
        writeJson = format != "scip",
        filesFrom = values["files-from"]?.let { Path.of(it).absolute() },
        threads = threads,
      )
    }
  }
}

/**
 * Opens a project headless and writes a SCIP index of its Java files from PSI resolve.
 *
 * The starter never saves the project or the application settings.
 * Run it with dedicated `idea.config.path` and `idea.system.path`, and on a copy of the project.
 * The exit code is 0 after the index is written, 1 on a failure and 2 on a usage error.
 * A wait that exceeds its timeout is a failure; the waits use [withTimeoutOrNull],
 * because the [kotlinx.coroutines.TimeoutCancellationException] of `withTimeout` is a cancellation that the failure handler rethrows.
 */
internal class PsiScipStarter : ModernApplicationStarter() {
  override val isHeadless: Boolean = true

  override fun premain(args: List<String>) {
    System.setProperty("ide.browser.jcef.enabled", false.toString())
    System.setProperty("vcs.log.index.enable", false.toString())
    System.setProperty("ide.slow.operations.assertion", false.toString())
    SystemProperties.setProperty("intellij.progress.task.ignoreHeadless", true.toString())
  }

  override suspend fun start(args: List<String>) {
    val options = try {
      ExportOptions.parse(args.drop(1))
    }
    catch (e: IllegalArgumentException) {
      System.err.println("Error: ${e.message}")
      System.err.println(USAGE)
      exitProcess(2)
    }
    ApplicationManagerEx.getApplicationEx().setSaveAllowed(false)
    val exitCode = try {
      export(options, args.drop(1))
      0
    }
    catch (e: Throwable) {
      rethrowControlFlowException(e)
      System.err.println("Error: the export failed")
      e.printStackTrace()
      1
    }
    exitProcess(exitCode)
  }
}

private suspend fun export(options: ExportOptions, arguments: List<String>) {
  val startedAt = System.currentTimeMillis()
  val project = openProject(options)
  waitForProject(project, options.jdk)
  val moduleSdks = readAction {
    ModuleManager.getInstance(project).modules.map { module ->
      val sdk = ModuleRootManager.getInstance(module).sdk
      "${module.name}=${sdk?.name}@${sdk?.homePath}"
    }.sorted()
  }
  log("module SDKs: ${moduleSdks.joinToString()}")
  val openMs = System.currentTimeMillis() - startedAt
  log("project is ready after $openMs ms")

  val summary = ExportSummary()
  val files = selectFiles(project, options.project, options.filesFrom, summary)
  log("walking ${files.size} files, skipped ${summary.skippedFiles}, threads ${options.threads}")

  val collectStartedAt = System.currentTimeMillis()
  val documents = arrayOfNulls<ScipDocument>(files.size)
  val fileStats = arrayOfNulls<ExportStats>(files.size)
  val done = AtomicInteger()
  val dispatcher = Dispatchers.Default.limitedParallelism(options.threads)
  coroutineScope {
    files.forEachIndexed { index, file ->
      launch(dispatcher) {
        val (document, stats) = smartReadAction(project) { collectFile(project, file) }
        documents[index] = document
        fileStats[index] = stats
        val count = done.incrementAndGet()
        if (count % 1000 == 0) {
          log("collected $count of ${files.size} files")
        }
      }
    }
  }
  files.forEachIndexed { index, file -> summary.add(file.module, fileStats[index]!!) }
  val collectMs = System.currentTimeMillis() - collectStartedAt
  log("collected ${files.size} files in $collectMs ms")
  val duplicates = summary.findDuplicateSymbols(documents.filterNotNull())
  if (duplicates > 0) {
    log("$duplicates symbols have more than one definition; the summary lists them under duplicate_symbol_sites")
  }

  val writeStartedAt = System.currentTimeMillis()
  Files.createDirectories(options.output.parent)
  // the paths of a run stay out of the tool arguments, so two runs on copies of one revision differ only in `project_root`
  val pathOptions = listOf("--project=", "--jdk=", "--output=", "--files-from=")
  val toolArguments = arguments.filter { argument -> pathOptions.none { argument.startsWith(it) } }
  val tool = ScipToolInfo("psi-scip", ApplicationInfo.getInstance().build.asString(), toolArguments)
  val projectRoot = options.project.toUri().toString().removeSuffix("/")
  val outputs = ArrayList<Path>()
  if (options.writeScip) {
    ScipWriter(options.output).use { writer ->
      writer.writeMetadata(projectRoot, tool)
      documents.forEach { it?.let(writer::writeDocument) }
    }
    outputs.add(options.output)
  }
  if (options.writeJson) {
    val json = options.output.resolveSibling(options.output.name.removeSuffix(".scip") + ".json")
    ScipJsonWriter(json).use { writer ->
      writer.writeMetadata(projectRoot, tool)
      documents.forEach { it?.let(writer::writeDocument) }
    }
    outputs.add(json)
  }
  val writeMs = System.currentTimeMillis() - writeStartedAt
  val summaryFile = options.output.resolveSibling(options.output.name.removeSuffix(".scip") + ".summary.json")
  summary.write(summaryFile, linkedMapOf(
    "project" to "\"${options.project}\"",
    "documents" to documents.count { it != null }.toString(),
    "occurrences" to documents.sumOf { it?.occurrences?.size ?: 0 }.toString(),
    "open_ms" to openMs.toString(),
    "collect_ms" to collectMs.toString(),
    "write_ms" to writeMs.toString(),
    "output_bytes" to outputs.joinToString(prefix = "{", postfix = "}") { "\"${it.name}\": ${Files.size(it)}" },
  ))
  log("wrote ${outputs.joinToString()} and $summaryFile in $writeMs ms; total ${System.currentTimeMillis() - startedAt} ms")
}

private fun collectFile(project: Project, file: ExportFile): Pair<ScipDocument?, ExportStats> {
  val psiFile = PsiManager.getInstance(project).findFile(file.file) as? PsiJavaFile
  if (psiFile == null) {
    val stats = ExportStats()
    stats.site(Counter.EXCEPTIONS, "${file.relativePath}: no Java PSI file")
    return null to stats
  }
  val collector = JavaDocumentCollector(psiFile, file.relativePath)
  val startedAt = System.nanoTime()
  return try {
    val document = collector.collect()
    collector.stats.add(Counter.FILES)
    collector.stats.add(Counter.MS, (System.nanoTime() - startedAt) / 1_000_000)
    document to collector.stats
  }
  catch (e: Throwable) {
    rethrowControlFlowException(e)
    val stats = ExportStats()
    stats.site(Counter.EXCEPTIONS, "${file.relativePath}: $e")
    null to stats
  }
}

private suspend fun openProject(options: ExportOptions): Project {
  val root = options.project
  TrustedProjects.setProjectTrusted(root, true)
  val task = OpenProjectTask {
    showWelcomeScreen = false
    beforeOpenTasks += { project ->
      configureSdk(project, options.jdk)
      true
    }
  }
  log("opening $root")
  val timeout = timeoutFromProperty(OPEN_TIMEOUT_PROPERTY, 20.minutes)
  val project = withTimeoutOrNull(timeout) {
    if (root.resolve(".idea").isDirectory()) {
      ProjectManagerEx.getInstanceEx().openProjectAsync(root, task)
    }
    else {
      ProjectUtil.openOrImportAsync(root, task)
    } ?: error("the project did not open: $root")
  } ?: error("the project did not open within $timeout ($OPEN_TIMEOUT_PROPERTY): $root")
  TrustedProjects.setProjectTrusted(project, true)
  // an import can skip the tasks of the open, and the SDK setup is idempotent
  configureSdk(project, options.jdk)
  return project
}

/**
 * Waits for smart mode and for the project configuration, such as a Maven import.
 * An import can give modules JDK names that do not exist yet, so the wait repeats until [configureSdk] registers no new name.
 */
private suspend fun waitForProject(project: Project, jdkHome: Path) {
  val timeout = timeoutFromProperty(SMART_MODE_TIMEOUT_PROPERTY, 120.minutes)
  withTimeoutOrNull(timeout) {
    repeat(3) {
      project.waitForSmartMode()
      log("smart mode; waiting for the project configuration")
      Observation.awaitConfiguration(project) { message -> log("configuration: $message") }
      project.waitForSmartMode()
      if (!configureSdk(project, jdkHome)) {
        return@withTimeoutOrNull
      }
    }
  } ?: error("smart mode and the project configuration took longer than $timeout ($SMART_MODE_TIMEOUT_PROPERTY)")
}

/**
 * Registers [jdkHome] under every JDK name that the project or one of its modules asks for and cannot find, plus [DEFAULT_JDK_NAME].
 * A project without an SDK gets [DEFAULT_JDK_NAME]. Returns `true` when the call changed the JDK table or the project SDK.
 */
private suspend fun configureSdk(project: Project, jdkHome: Path): Boolean {
  val names = TreeSet<String>()
  readAction {
    val rootManager = ProjectRootManager.getInstance(project)
    if (rootManager.projectSdk == null) {
      rootManager.projectSdkName?.let(names::add)
    }
    for (module in ModuleManager.getInstance(project).modules) {
      for (entry in ModuleRootManager.getInstance(module).orderEntries) {
        if (entry is JdkOrderEntry && entry !is InheritedJdkOrderEntry && entry.jdk == null) {
          entry.jdkName?.let(names::add)
        }
      }
    }
  }
  names.add(DEFAULT_JDK_NAME)
  val added = ArrayList<String>()
  val setProjectSdk = edtWriteAction {
    val table = ProjectJdkTable.getInstance()
    for (name in names) {
      if (table.findJdk(name) == null) {
        table.addJdk(JavaSdk.getInstance().createJdk(name, jdkHome.toString(), false))
        added.add(name)
      }
    }
    val rootManager = ProjectRootManager.getInstance(project)
    if (rootManager.projectSdk == null) {
      rootManager.projectSdk = table.findJdk(DEFAULT_JDK_NAME)
      true
    }
    else {
      false
    }
  }
  if (added.isNotEmpty()) {
    log("registered $jdkHome as ${added.joinToString()}")
  }
  if (setProjectSdk) {
    log("set the project SDK to $DEFAULT_JDK_NAME")
  }
  return added.isNotEmpty() || setProjectSdk
}

private fun timeoutFromProperty(name: String, default: Duration): Duration {
  return System.getProperty(name)?.toLongOrNull()?.takeIf { it > 0 }?.seconds ?: default
}

private fun log(message: String) {
  System.err.println("psi-scip: $message")
}
