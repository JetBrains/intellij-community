// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.testFramework.performance

import com.intellij.codeInspection.LocalInspectionEP
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.RecursionManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.codeInsight.PyCodeInsightCounters
import com.jetbrains.python.codeInsight.PyCodeInsightCounters.Counter
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyTypedElement
import com.jetbrains.python.psi.types.PyTypeEngineSettingsModificationTracker
import com.jetbrains.python.psi.types.TypeEvalContext
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToLong

/**
 * Measures one scenario of the native Python code insight: wall time, [PyCodeInsightCounters], AST loads and,
 * for an action on the calling thread, the allocated bytes.
 *
 * Each attempt runs `setup` and then the measured `action`. The warm-up attempts are not in the result.
 * [cold] drops the PSI caches and both type context maps, so a `setup` that calls it gives a cold type state.
 * The PSI, the stubs and the control flow of the open file stay.
 *
 * [report] prints one line per metric with the prefix `PYPERF`. The system property `pyperf.out` names a file
 * that also gets the lines.
 */
class PyPerfProbe(private val project: Project, private val editor: Editor, private val parentDisposable: Disposable) {

  /**
   * The open editor that the scenarios measure. Each test module adapts its test fixture to this interface. The probe
   * does not use the test framework classes, because the class loader of the probe cannot see them in the
   * `intellij.python.junit5Tests` layout.
   */
  interface Editor {
    val file: PsiFile
    val document: Document

    /** Runs the highlighting passes of [file] and returns the number of highlights. */
    fun highlight(): Int
    fun enableInspections(inspections: List<LocalInspectionTool>)
    fun disableInspections(inspections: List<LocalInspectionTool>)
  }

  /**
   * The AST loads of one attempt. [injectedFragments] counts the parses of injected fragments. Their file window
   * has the name of the host file.
   */
  class AstLoads(
    val openFile: Long,
    val projectFiles: Long,
    val libraryFiles: Long,
    val injectedFragments: Long,
    val byFileName: Map<String, Long>,
  ) {
    override fun toString(): String =
      "open=$openFile project=$projectFiles library=$libraryFiles injected=$injectedFragments files=" +
      byFileName.entries.sortedByDescending { it.value }.take(8).joinToString(",") { "${it.key}:${it.value}" }
  }

  class Result(
    val scenario: String,
    val nanos: List<Long>,
    val counters: List<PyCodeInsightCounters.Snapshot>,
    val allocatedBytes: List<Long>,
    val allThreadsAllocatedBytes: List<Long>,
    val firstAttemptAstLoads: AstLoads,
    val firstAttemptCounters: PyCodeInsightCounters.Snapshot,
  ) {
    val medianMs: Double get() = median(nanos) / 1e6

    /** The counters of the attempt with the median time. */
    val medianCounters: PyCodeInsightCounters.Snapshot
      get() = counters[nanos.indices.sortedBy { nanos[it] }[nanos.size / 2]]

    operator fun get(counter: Counter): Long = medianCounters[counter]
  }

  init {
    PyCodeInsightCounters.enable(parentDisposable)
    // In tests, RecursionManager fails on each prevented recursion and on each value that a prevented recursion keeps
    // out of a cache. Real code such as pandas does both, and the IDE does not check it.
    RecursionManager.disableAssertOnRecursionPrevention(parentDisposable)
    RecursionManager.disableMissedCacheAssertions(parentDisposable)
    // Measure with the IDE values of these keys, whatever the test setup sets.
    for ((key, value) in PINNED_REGISTRY) {
      if (Registry.getInstance().getBundleValueOrNull(key) == null) {
        // The key is not declared in this product. A system property gives it a value without a declaration.
        val previous = System.setProperty(key, value)
        Disposer.register(parentDisposable) {
          if (previous == null) System.clearProperty(key) else System.setProperty(key, previous)
        }
        continue
      }
      val registryValue = Registry.get(key)
      if (value == "true" || value == "false") registryValue.setValue(value.toBoolean(), parentDisposable)
      else registryValue.setValue(value, parentDisposable)
    }
  }

  /** Drops the resolve caches, the PSI-dependent cached values and the user and library type contexts. */
  fun cold() {
    ApplicationManager.getApplication().invokeAndWait {
      WriteIntentReadAction.run {
        PsiManager.getInstance(project).dropPsiCaches()
        PyTypeEngineSettingsModificationTracker.getInstance(project).incModificationCount()
      }
    }
  }

  /** Measures [action] in [attempts] attempts after [warmups] warm-up attempts. [setup] runs before each attempt. */
  fun measure(
    scenario: String,
    warmups: Int = WARMUPS,
    attempts: Int = ATTEMPTS,
    setup: () -> Unit = { cold() },
    action: () -> Unit,
  ): Result {
    val series = Series()
    for (i in 0 until warmups + attempts) {
      ApplicationManagerEx.runInStressTest<RuntimeException>(true) {
        runAttempt(i >= warmups, setup, action, series)
      }
    }
    return series.result(scenario)
  }

  private class Series {
    val nanos = ArrayList<Long>()
    val counters = ArrayList<PyCodeInsightCounters.Snapshot>()
    val allocated = ArrayList<Long>()
    val allThreadsAllocated = ArrayList<Long>()
    var firstAstLoads: AstLoads? = null
    var firstCounters: PyCodeInsightCounters.Snapshot? = null

    fun result(scenario: String): Result =
      Result(scenario, nanos, counters, allocated, allThreadsAllocated, firstAstLoads!!, firstCounters!!)
  }

  private fun runAttempt(timed: Boolean, setup: () -> Unit, action: () -> Unit, series: Series) {
    setup()
    val attemptDisposable = Disposer.newDisposable(parentDisposable, "PyPerfProbe attempt")
    val astLoads = countAstLoads(attemptDisposable)
    try {
      val before = PyCodeInsightCounters.snapshot()
      val allThreadsBefore = THREAD_MX_BEAN.totalThreadAllocatedBytes
      val allocatedBefore = THREAD_MX_BEAN.currentThreadAllocatedBytes
      val start = System.nanoTime()
      action()
      val elapsed = System.nanoTime() - start
      val allocatedAfter = THREAD_MX_BEAN.currentThreadAllocatedBytes
      val allThreadsAfter = THREAD_MX_BEAN.totalThreadAllocatedBytes
      val delta = PyCodeInsightCounters.snapshot() - before
      if (series.firstCounters == null) {
        series.firstAstLoads = astLoads()
        series.firstCounters = delta
      }
      if (timed) {
        series.nanos.add(elapsed)
        series.counters.add(delta)
        series.allocated.add(allocatedAfter - allocatedBefore)
        series.allThreadsAllocated.add(allThreadsAfter - allThreadsBefore)
      }
    }
    finally {
      Disposer.dispose(attemptDisposable)
    }
  }

  /**
   * Like [measure] with the default attempt counts, but with one warm-up and three attempts when the first run of
   * [action] takes more than [slowNanos]. A large input can then finish in a bounded time. The report says so.
   */
  fun measureScaled(
    scenario: String,
    slowNanos: Long = 10_000_000_000,
    setup: () -> Unit = { cold() },
    action: () -> Unit,
  ): Result {
    setup()
    val start = System.nanoTime()
    action()
    val slow = System.nanoTime() - start > slowNanos
    return if (slow) measure("$scenario/reduced_attempts", warmups = 1, attempts = 3, setup = setup, action = action)
    else measure(scenario, setup = setup, action = action)
  }

  /** Evaluates the type of each [PyTypedElement] of [file] in one `codeAnalysis` context on the calling thread. */
  fun inferAll(file: PsiFile): Int = runReadActionBlocking {
    val context = TypeEvalContext.codeAnalysis(project, file)
    var count = 0
    for (element in PsiTreeUtil.collectElementsOfType(file, PyTypedElement::class.java)) {
      context.getType(element)
      count++
    }
    count
  }

  /** Runs the highlighting passes of the editor file, with the enabled inspections. */
  fun highlight(): Int = editor.highlight()

  /** Inserts [text] at [offset] of the editor file and commits the document. */
  fun type(offset: Int, text: String) {
    WriteCommandAction.runWriteCommandAction(project) {
      editor.document.insertString(offset, text)
      PsiDocumentManager.getInstance(project).commitDocument(editor.document)
    }
  }

  /** Deletes [length] characters at [offset] of the editor file and commits the document. */
  fun delete(offset: Int, length: Int) {
    WriteCommandAction.runWriteCommandAction(project) {
      editor.document.deleteString(offset, offset + length)
      PsiDocumentManager.getInstance(project).commitDocument(editor.document)
    }
  }

  private fun countAstLoads(attemptDisposable: Disposable): () -> AstLoads {
    val openFile = AtomicLong()
    val projectFiles = AtomicLong()
    val libraryFiles = AtomicLong()
    val injectedFragments = AtomicLong()
    val byFileName = java.util.concurrent.ConcurrentHashMap<String, AtomicLong>()
    val fileIndex = ProjectFileIndex.getInstance(project)
    val editorFile: String = runReadActionBlocking { editor.file.viewProvider.virtualFile.path }
    PsiManagerEx.getInstanceEx(project).setAssertOnFileLoadingFilter({ file ->
      val loads = byFileName.computeIfAbsent(file.name) { AtomicLong() }.incrementAndGet()
      if (loads <= AST_LOAD_STACKS && java.lang.Boolean.getBoolean("pyperf.astload.stacks")) {
        emit(listOf("PYPERF ast_load_stack ${file.name} #$loads\n" + Throwable().stackTrace.take(60).joinToString("\n") { "    at $it" }))
      }
      when {
        file is VirtualFileWindow -> injectedFragments.incrementAndGet()
        file.path == editorFile -> openFile.incrementAndGet()
        fileIndex.isInContent(file) -> projectFiles.incrementAndGet()
        else -> libraryFiles.incrementAndGet()
      }
      false
    }, attemptDisposable)
    return { AstLoads(openFile.get(), projectFiles.get(), libraryFiles.get(), injectedFragments.get(), byFileName.mapValues { it.value.get() }) }
  }

  /**
   * Measures the editor file in three scenarios: a cold highlighting, a highlighting after a one-character edit in
   * the largest function, and one [inferAll] pass. [inspections] are on during the highlighting.
   * Returns the result of the cold highlighting.
   */
  fun measureEditorFile(name: String, inspections: List<LocalInspectionTool>): Result {
    val cold: Result
    editor.enableInspections(inspections)
    try {
      cold = measure("$name/cold_highlight") { highlight() }
      report(cold)

      val editOffset = runReadActionBlocking {
        val function = PsiTreeUtil.findChildrenOfType(editor.file, PyFunction::class.java).maxBy { it.statementList.statements.size }
        function.statementList.statements.first().textRange.endOffset
      }
      var attempt = 0
      val edit = measure("$name/edit_highlight", setup = {}) {
        if (attempt++ % 2 == 0) type(editOffset, " ") else delete(editOffset, 1)
        highlight()
      }
      report(edit)
      emit(listOf("PYPERF $name/edit_vs_cold getType.evaluations ratio=" +
                  fmt(edit[Counter.GET_TYPE_EVALUATIONS].toDouble() / cold[Counter.GET_TYPE_EVALUATIONS]) +
                  " time ratio=" + fmt(edit.medianMs / cold.medianMs)))
    }
    finally {
      editor.disableInspections(inspections)
    }
    report(measure("$name/infer_all") { inferAll(editor.file) })
    return cold
  }

  fun report(result: Result) {
    val sortedMs = result.nanos.sorted().map { it / 1e6 }
    val lines = ArrayList<String>()
    val prefix = "PYPERF ${result.scenario}"
    lines += "$prefix time_ms median=${fmt(median(result.nanos) / 1e6)} min=${fmt(sortedMs.first())} " +
             "p25=${fmt(sortedMs[sortedMs.size / 4])} p75=${fmt(sortedMs[sortedMs.size * 3 / 4])} max=${fmt(sortedMs.last())} n=${sortedMs.size}"
    val allocatedMb = result.allocatedBytes.sorted().map { it / 1e6 }
    lines += "$prefix alloc_mb_callingThread median=${fmt(median(result.allocatedBytes) / 1e6)} min=${fmt(allocatedMb.first())} max=${fmt(allocatedMb.last())}"
    val allThreadsMb = result.allThreadsAllocatedBytes.sorted().map { it / 1e6 }
    lines += "$prefix alloc_mb_allThreads median=${fmt(median(result.allThreadsAllocatedBytes) / 1e6)} min=${fmt(allThreadsMb.first())} max=${fmt(allThreadsMb.last())}"
    lines += "$prefix ast_loads_first_attempt ${result.firstAttemptAstLoads}"
    val median = result.medianCounters
    for (counter in Counter.entries) {
      val values = result.counters.map { it[counter] }
      val spread = if (values.min() == values.max()) "" else " range=${values.min()}..${values.max()}"
      lines += "$prefix counter ${counter.id}=${median[counter]}$spread first=${result.firstAttemptCounters[counter]}"
    }
    emit(lines)
  }

  companion object {
    const val WARMUPS: Int = 3
    const val ATTEMPTS: Int = 10
    private const val AST_LOAD_STACKS = 3
    private const val PYTHON_LANGUAGE_ID = "Python"

    /** The production defaults of the keys that change the cost of type evaluation, pinned for each measurement. */
    val PINNED_REGISTRY: Map<String, String> = linkedMapOf(
      "python.use.better.control.flow.type.inference" to "true",
      "python.control.flow.assumption.max.depth" to "8",
      "python.use.separated.libraries.type.cache" to "true",
      "python.optimized.type.eval.context" to "true",
      "python.use.csp.type.inference" to "true",
      "python.typing.weak.keys.type.eval.context" to "true",
      "python.typing.strict.unions" to "true",
      "python.strict.type.narrow" to "true",
      // Declared by the type engine module. Without the declaration, each read throws and catches an exception.
      "pycharm.type.engine" to "true",
    )

    private val THREAD_MX_BEAN = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean

    fun median(values: List<Long>): Double {
      val sorted = values.sorted()
      val middle = sorted.size / 2
      return if (sorted.size % 2 == 1) sorted[middle].toDouble() else (sorted[middle - 1] + sorted[middle]) / 2.0
    }

    fun fmt(value: Double): String = if (value >= 100) value.roundToLong().toString() else "%.2f".format(value)

    fun emit(lines: List<String>) {
      lines.forEach(::println)
      val out = System.getProperty("pyperf.out") ?: return
      Files.write(Path.of(out), lines.joinToString("") { it + "\n" }.toByteArray(),
                  StandardOpenOption.CREATE, StandardOpenOption.APPEND)
    }

    /**
     * The Python inspections that are on in the default profile, without the inspections that start an external
     * process. A mock SDK has no interpreter for such a process.
     */
    fun defaultPythonInspections(): List<LocalInspectionTool> =
      LocalInspectionEP.LOCAL_INSPECTION.extensionList
        .filter { it.language == PYTHON_LANGUAGE_ID && it.enabledByDefault && it.shortName !in EXTERNAL_TOOL_INSPECTIONS }
        .map { it.instantiateTool() as LocalInspectionTool }

    private val EXTERNAL_TOOL_INSPECTIONS = setOf("PyPep8Inspection")
  }
}
