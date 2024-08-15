package com.jetbrains.performancePlugin.commands

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.codeInsight.daemon.impl.TrafficLightRenderer
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.ide.lightEdit.LightEdit
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.event.BulkAwareDocumentListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.fileEditor.ex.FileEditorWithProvider
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.util.use
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.diagnostic.telemetry.Scope
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.ide.diagnostic.startUpPerformanceReporter.FUSProjectHotStartUpMeasurerService
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.ui.EDT
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.*
import java.util.Collections
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds

private val FileEditor.description: String
  get() = "${hashCode()} ${javaClass} ${toString()}"

// See DaemonFusReporter. Reality in DaemonCodeAnalyzerImpl is a bit more complicated, probably including other editors if the file has Psi
private fun Collection<FileEditor>.getWorthy(): List<TextEditor> {
  return mapNotNull {
    if (it !is TextEditor || it.editor.editorKind != EditorKind.MAIN_EDITOR) null
    else if (it is TextEditorWithPreview) it.textEditor
    else it
  }
}

internal class WaitForFinishedCodeAnalysis(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX = CMD_PREFIX + "waitForFinishedCodeAnalysis"
    val LOG = logger<WaitForFinishedCodeAnalysis>()
  }

  override suspend fun doExecute(context: PlaybackContext) {
    LOG.info("WaitForFinishedCodeAnalysis started its execution")
    context.project.service<CodeAnalysisStateListener>().waitAnalysisToFinish()
  }

  override fun getName(): String {
    return PREFIX
  }
}

@Service(Service.Level.PROJECT)
class CodeAnalysisStateListener(val project: Project, val cs: CoroutineScope) {

  internal companion object {
    val LOG = logger<WaitForFinishedCodeAnalysis>()
  }

  private val stateLock = Any()
  private val filesYetToStartHighlighting = ConcurrentHashMap<VirtualFile, Unit>()
  private val sessions = ConcurrentHashMap<TextEditor, ExceptionWithTime>()
  private val waitingJobs: MutableList<CompletableFuture<Unit>> = Collections.synchronizedList(mutableListOf<CompletableFuture<Unit>>())
  private var locked: Boolean = false

  private fun ensureLockedIfNeeded() {
    synchronized(stateLock) {
      @Suppress("UsePropertyAccessSyntax") // inhibit weak warning, for property access is a warning
      if ((!sessions.isEmpty() || !filesYetToStartHighlighting.isEmpty()) && !locked) {
        LOG.info("Highlighting began with ${sessions.keys.joinToString(separator = ",\n") { it.description }} \n" +
                 "and files ${filesYetToStartHighlighting.keys.joinToString(separator = ",\n") { it.name }}")
        locked = true
      }
    }
  }

  private fun unlockIfNeeded() {
    synchronized(stateLock) {
      if (!locked) {
        return
      }

      @Suppress("UsePropertyAccessSyntax")  // inhibit weak warning, for property access is a warning
      if (sessions.isEmpty() && filesYetToStartHighlighting.isEmpty()) {
        LOG.info("""
          Highlighting done,
          Total opening time is : ${(System.nanoTime() - StartUpMeasurer.getStartTime()).nanoseconds.inWholeMilliseconds}
         """)
        for (job in waitingJobs) {
          job.complete(Unit)
        }
        waitingJobs.clear()
        locked = false
      }
      else {
        //Printing additional information to get information why highlighting was stuck
        sessions.forEach {
          val editor = it.key.editor
          printCodeAnalyzerStatistic(editor)
          printFileStatusMapInfo(editor)
        }
        LOG.info("Highlighting still in progress: ${sessions.keys.joinToString(separator = ",\n") { it.description }},\n" +
                 "files ${filesYetToStartHighlighting.keys.joinToString(separator = ",\n") { it.name }}")
      }
    }
  }

  /**
   * @throws TimeoutException when stopped due to provided [timeout]
   */
  suspend fun waitAnalysisToFinish(timeout: Duration? = 5.minutes, throws: Boolean = false) {
    LOG.info("Waiting for code analysis to finish in $timeout")
    val future = CompletableFuture<Unit>()
    if (timeout != null) {
      future.orTimeout(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
    }
    coroutineScope {
      launch {
        while (true) {
          @Suppress("TestOnlyProblems")
          if (!service<FUSProjectHotStartUpMeasurerService>().isHandlingFinished() && !future.isDone) {
            delay(500)
          }
          else {
            break
          }
        }

        if (future.isDone) {
          return@launch
        }

        // WaitForFinishedCodeAnalysisFileEditorListener.fileOpenedSync works on EDT,
        // so this is to ensure the reopened editor from startup would be caught by the listener before we ask ListenerState to wait
        withContext(Dispatchers.EDT) {
          // do nothing
        }

        if (!future.isDone) {
          registerToWaitForAnalysisToFinish(future)
        }
      }
    }

    try {
      future.join()
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (_: CompletionException) {
      val errorText = "Waiting for highlight to finish took more than $timeout."
      LOG.error(errorText)
      if (throws) {
        throw TimeoutException(errorText)
      }
    }
    LOG.info("Code analysis waiting finished")
  }

  private fun registerToWaitForAnalysisToFinish(future: CompletableFuture<Unit>) {
    if (LightEdit.owns(project)) {
      future.complete(Unit)
      return
    }
    if (!future.isDone) {
      registerWaiter(future)
    }
  }

  private fun registerWaiter(future: CompletableFuture<Unit>) {
    synchronized(stateLock) {
      if (!locked) {
        future.complete(Unit)
      }
      else {
        waitingJobs.add(future)
      }
    }
  }

  internal fun registerOpenedEditors(openedEditors: List<TextEditor>) {
    val listener = SimpleEditedDocumentsListener(project)
    synchronized(stateLock) {
      for (fileEditor in openedEditors) {
        filesYetToStartHighlighting.put(fileEditor.file, Unit)
        fileEditor.editor.document.addDocumentListener(listener, fileEditor)
      }
      ensureLockedIfNeeded()
    }
  }

  internal fun registerFileToHighlight(file: VirtualFile) {
    val hasWorthyEditor = FileEditorManager.getInstance(project).getEditorList(file).getWorthy().any { UIUtil.isShowing(it.editor.component) }
    if (!hasWorthyEditor) return
    synchronized(stateLock) {
      filesYetToStartHighlighting.put(file, Unit)
      ensureLockedIfNeeded()
    }
  }

  fun registerDaemonStarted(fileEditors: Collection<TextEditor>) {
    val errors = mutableListOf<AssertionError>()
    val isStartedInDumbMode = runReadAction { DumbService.isDumb(project) }
    synchronized(stateLock) {
      for (editor in fileEditors) {
        LOG.info("Daemon starting for ${editor.description}")
        val previousSessionStartTrace = sessions.put(editor, ExceptionWithTime.createForAnalysisStart(editor, isStartedInDumbMode))
        ExceptionWithTime.createIntersectionErrorIfNeeded(editor, previousSessionStartTrace)?.let { errors.add(it) }
        filesYetToStartHighlighting.remove(editor.file)
      }
      ensureLockedIfNeeded()
    }

    if (errors.isEmpty()) return
    cs.launch {
      errors.forEach {
        LOG.error(it)
        it.suppressed.forEach { suppressed -> LOG.error("   Suppressed exception: ", suppressed) }
      }
    }
  }

  interface HighlightedEditor {
    val editor: TextEditor
    val shouldWaitForNextHighlighting: Boolean

    private class IncompletelyHighlightedEditor(override val editor: TextEditor) : HighlightedEditor {
      override val shouldWaitForNextHighlighting
        get() = true
    }

    private class InvisibleEditor(override val editor: TextEditor) : HighlightedEditor {
      override val shouldWaitForNextHighlighting
        get() = false
    }

    private class VisibleEditor(override val editor: TextEditor, private val isHighlighted: Boolean) : HighlightedEditor {
      override val shouldWaitForNextHighlighting
        get() = !isHighlighted
    }

    companion object {
      @RequiresReadLock
      fun create(editor: TextEditor, project: Project, isCancelled: Boolean, isFinishedInDumbMode: Boolean): HighlightedEditor {
        if (!UIUtil.isShowing(editor.getComponent())) {
          LOG.info("Creating invisible editor ${editor.description}")
          return InvisibleEditor(editor)
        }
        else if (isFinishedInDumbMode || isCancelled) {
          LOG.info("Creating unfinished editor isFinishedInDumbMode=$isFinishedInDumbMode, isCancelled=$isCancelled ${editor.description}")
          return IncompletelyHighlightedEditor(editor)
        }
        else {
          val isHighlighted = DaemonCodeAnalyzerImpl.isHighlightingCompleted(editor, project)
          LOG.info("Creating visible editor ${editor.description}\nisHighlighted $isHighlighted")
          return VisibleEditor(editor, isHighlighted)
        }
      }
    }
  }

  internal fun registerDaemonFinishedOrCancelled(highlightedEditors: Map<TextEditor, HighlightedEditor>, status: String, traceId: UUID) {
    val currentTime = System.currentTimeMillis()
    synchronized(stateLock) {
      val iterator = sessions.entries.iterator()
      while (iterator.hasNext()) {
        val (editor, exceptionWithTime) = iterator.next()
        val highlightedEditor = highlightedEditors[editor]
        if (highlightedEditor == null) {
          if (!UIUtil.isShowing(editor.getComponent())) {
            iterator.remove()
          }
        }
        else {
          val shouldWait = highlightedEditor.shouldWaitForNextHighlighting || exceptionWithTime.wasStartedInLimitedSetup
          LOG.info(""" 
            Registering daemon finished or cancelled for:
              daemon $status for ${highlightedEditor.editor.description},
              shouldWaitForHighlighting = ${shouldWait},
              shouldWaitForNextHighlighting = ${highlightedEditor.shouldWaitForNextHighlighting},
              traceId = $traceId
        """.trimIndent())
          if (shouldWait) {
            ExceptionWithTime.markAnalysisFinished(exceptionWithTime)
          }
          else {
            iterator.remove()
            LOG.info(ExceptionWithTime.getLogHighlightingMessage(currentTime, highlightedEditor.editor, exceptionWithTime))
          }
        }
      }

      if (!filesYetToStartHighlighting.isEmpty()) {
        val fileEditorManager = FileEditorManager.getInstance(project)
        val filesIterator = filesYetToStartHighlighting.entries.iterator()
        while (filesIterator.hasNext()) {
          val (file, _) = filesIterator.next()
          val hasWorthyEditor = fileEditorManager.getEditors(file).toMutableList().getWorthy().any { UIUtil.isShowing(it.editor.getComponent()) }
          if (!hasWorthyEditor) {
            filesIterator.remove()
          }
        }
      }

      unlockIfNeeded()
    }
  }

  private fun printCodeAnalyzerStatistic(editor: Editor) {
    //Status can't be retrieved from EDT
    if (EDT.isCurrentThreadEdt()) return
    try {
      ReadAction.run<Throwable> {
        LOG.info("Analyzer status for ${editor.virtualFile.path}\n ${TrafficLightRenderer(project, editor.document).use { it.daemonCodeAnalyzerStatus }}")
      }
    }
    catch (ex: Throwable) {
      LOG.warn("Print Analyzer status failed", ex)
    }
  }

  internal fun printFileStatusMapInfo(editor: Editor) {
    try {
      val fileStatus = (DaemonCodeAnalyzerImpl.getInstance(project) as DaemonCodeAnalyzerImpl)
        .fileStatusMap
        .toString(editor.document)
      LOG.info("File status map $fileStatus")
    }
    catch (ex: Throwable) {
      LOG.warn("Print Analyzer status map failed", ex)
    }
  }

}

private class SimpleEditedDocumentsListener(private val project: Project) : BulkAwareDocumentListener.Simple {
  override fun beforeDocumentChangeNonBulk(event: DocumentEvent) {
    val file = FileDocumentManager.getInstance().getFile(event.document) ?: return
    project.service<CodeAnalysisStateListener>().registerFileToHighlight(file)
  }
}

internal class WaitForFinishedCodeAnalysisListener(private val project: Project) : DaemonCodeAnalyzer.DaemonListener {
  init {
    if (!ApplicationManagerEx.isInIntegrationTest()) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override fun daemonStarting(fileEditors: Collection<FileEditor>) {
    CodeAnalysisStateListener.LOG.info("Daemon starting with ${fileEditors.size} unfiltered editors: " +
                                       fileEditors.joinToString(separator = "\n") { it.description })
    project.service<CodeAnalysisStateListener>().registerDaemonStarted(fileEditors.getWorthy())
  }

  override fun daemonCanceled(reason: String, fileEditors: Collection<FileEditor>) {
    val traceId = UUID.randomUUID()
    CodeAnalysisStateListener.LOG.info("Daemon canceled by the reason of '$reason', traceId = $traceId")
    daemonFinishedOrCancelled(fileEditors, true, traceId)
  }

  override fun daemonFinished(fileEditors: Collection<FileEditor>) {
    val traceId = UUID.randomUUID()
    CodeAnalysisStateListener.LOG.info("Daemon finished, traceId = $traceId")
    daemonFinishedOrCancelled(fileEditors, false, traceId)
  }

  private fun daemonFinishedOrCancelled(fileEditors: Collection<FileEditor>, isCancelled: Boolean, traceId: UUID) {
    val status = if (isCancelled) "cancelled" else "stopped"
    printFileEditors(fileEditors, status, traceId)

    val worthy = fileEditors.getWorthy()
    if (worthy.isEmpty()) return

    val highlightedEditors: Map<TextEditor, CodeAnalysisStateListener.HighlightedEditor> = runReadAction {
      val isFinishedInDumbMode = DumbService.isDumb(project)
      worthy.associateWith { CodeAnalysisStateListener.HighlightedEditor.create(it, project, isCancelled = isCancelled, isFinishedInDumbMode = isFinishedInDumbMode) }
    }

    project.service<CodeAnalysisStateListener>().registerDaemonFinishedOrCancelled(highlightedEditors, status, traceId)
  }

  fun printFileEditors(fileEditors: Collection<FileEditor>, status: String, traceId: UUID) {
    try {
      CodeAnalysisStateListener.LOG.info("Daemon $status with ${fileEditors.size} unfiltered editors, traceId = $traceId")
      val editorsMessage = fileEditors.map { fileEditor -> fileEditor.description }.joinToString(separator = "\n")
      CodeAnalysisStateListener.LOG.info("Editors to finish\n$editorsMessage")
    }
    catch (_: Exception) {
    }
  }

}

internal class WaitForFinishedCodeAnalysisFileEditorListener : FileOpenedSyncListener {
  init {
    if (!ApplicationManagerEx.isInIntegrationTest()) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override fun fileOpenedSync(source: FileEditorManager, file: VirtualFile, editorsWithProviders: List<FileEditorWithProvider>) {
    source.project.service<CodeAnalysisStateListener>().registerOpenedEditors(editorsWithProviders.map { it.fileEditor }.getWorthy())
  }
}

private sealed class ExceptionWithTime(override val message: String?) : Exception(message) {
  val timestamp: Long = System.currentTimeMillis()
  abstract val wasStartedInLimitedSetup: Boolean

  companion object {
    private class DaemonAnalysisStarted(editor: TextEditor, override val wasStartedInLimitedSetup: Boolean) :
      ExceptionWithTime(message = "Previous daemon start trace (editor = ${editor.description})") {
      private var analysisFinished = false

      fun markAnalysisFinished() {
        analysisFinished = true
      }

      fun isNotFinished() = !analysisFinished
    }

    fun createForAnalysisStart(editor: TextEditor, isStartedInDumbMode: Boolean): ExceptionWithTime {
      return DaemonAnalysisStarted(editor, isStartedInDumbMode)
    }

    fun markAnalysisFinished(exceptionWithTime: ExceptionWithTime?) {
      (exceptionWithTime as? DaemonAnalysisStarted)?.markAnalysisFinished()
    }

    fun getLogHighlightingMessage(currentTime: Long, editor: TextEditor, exceptionWithTime: ExceptionWithTime?): String {
      return when (exceptionWithTime) {
        null -> "Editor ${editor} wasn't opened, and highlighting didn't start, but it finished, and the editor was highlighted"
        is DaemonAnalysisStarted -> {
          TelemetryManager.getTracer(Scope("highlighting"))
            .spanBuilder("highlighting_${editor.file.name}")
            .setStartTimestamp(exceptionWithTime.timestamp, TimeUnit.MILLISECONDS)
            .startSpan()
            .end(currentTime, TimeUnit.MILLISECONDS)
          "Total highlighting time is : ${currentTime - exceptionWithTime.timestamp} ms for ${editor.description}"
        }
      }
    }

    fun createIntersectionErrorIfNeeded(editor: FileEditor, previousSessionStartTrace: ExceptionWithTime?): AssertionError? {
      if (previousSessionStartTrace is DaemonAnalysisStarted && previousSessionStartTrace.isNotFinished()) {
        val err = AssertionError("Overlapping highlighting sessions")
        err.addSuppressed(Exception("Current daemon start trace (editor = ${editor.description} )"))
        err.addSuppressed(previousSessionStartTrace)
        return err
      }
      return null
    }
  }
}
