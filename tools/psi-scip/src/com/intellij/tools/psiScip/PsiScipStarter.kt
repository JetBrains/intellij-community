// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.psiScip

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ModernApplicationStarter
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.util.SystemProperties
import org.jetbrains.kotlin.psi.KtFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.absolute
import kotlin.io.path.name
import kotlin.system.exitProcess

private const val COMMAND = "psiScip"

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
 * Opens a project headless and writes a SCIP index of its Java and Kotlin files from PSI resolve.
 *
 * The starter never saves the project or the application settings.
 * Run it with dedicated `idea.config.path` and `idea.system.path`, and on a copy of the project.
 * The exit code is 0 after the index is written, 1 on a failure and 2 on a usage error.
 * A wait that exceeds its timeout is a failure ([openReadyProject]).
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
  val project = openReadyProject(options.project, options.jdk, ::log)
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

/** The document of a Java file by [JavaDocumentCollector], of a Kotlin file by [KotlinDocumentCollector]. */
private fun collectFile(project: Project, file: ExportFile): Pair<ScipDocument?, ExportStats> {
  val collector: Pair<() -> ScipDocument, ExportStats> = when (val psiFile = PsiManager.getInstance(project).findFile(file.file)) {
    is PsiJavaFile -> JavaDocumentCollector(psiFile, file.relativePath).let { it::collect to it.stats }
    is KtFile -> KotlinDocumentCollector(psiFile, file.relativePath).let { it::collect to it.stats }
    else -> {
      val stats = ExportStats()
      stats.site(Counter.EXCEPTIONS, "${file.relativePath}: no Java or Kotlin PSI file")
      return null to stats
    }
  }
  val (collect, stats) = collector
  val startedAt = System.nanoTime()
  return try {
    val document = collect()
    stats.add(Counter.FILES)
    stats.add(Counter.MS, (System.nanoTime() - startedAt) / 1_000_000)
    document to stats
  }
  catch (e: Throwable) {
    rethrowControlFlowException(e)
    val stats = ExportStats()
    stats.site(Counter.EXCEPTIONS, "${file.relativePath}: $e")
    null to stats
  }
}

private fun log(message: String) {
  System.err.println("psi-scip: $message")
}
