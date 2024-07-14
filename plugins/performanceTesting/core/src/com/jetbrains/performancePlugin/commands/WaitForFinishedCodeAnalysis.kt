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
import com.intellij.platform.ide.diagnostic.startUpPerformanceReporter.FUSProjectHotStartUpMeasurerService
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.*
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

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
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val checkingJob = coroutineScope {
      launch {
        while (true) {
          @Suppress("TestOnlyProblems")
          if (!service<FUSProjectHotStartUpMeasurerService>().isHandlingFinished()) {
            delay(500)
          }
          else {
            return@launch
          }
        }
      }
    }
    checkingJob.join()
    // WaitForFinishedCodeAnalysisFileEditorListener.fileOpenedSync works on EDT,
    // so this is to ensure the reopened editor from startup would be caught by the listener before we ask ListenerState to wait
    withContext(Dispatchers.EDT) {
      // do nothing
    }
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
  private val sessions = ConcurrentHashMap<TextEditor, ExceptionWithTime>()
  private val highlightingFinishedEverywhere: Semaphore = Semaphore(1)
  private var locked: Boolean = false

  private fun ensureLockedIfNeeded() {
    synchronized(stateLock) {
      @Suppress("UsePropertyAccessSyntax") // inhibit weak warning, for property access is a warning
      if (!sessions.isEmpty() && !locked) {
        LOG.info("Highlighting began with ${sessions.keys.joinToString(separator = ",\n") { it.description }}")
        highlightingFinishedEverywhere.acquire()
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
      if (sessions.isEmpty()) {
        LOG.info("Highlighting done")
        LOG.info("Total opening time is : ${Duration.ofNanos(System.nanoTime() - StartUpMeasurer.getStartTime()).toMillis()}")
        highlightingFinishedEverywhere.release()
        locked = false
      }
      else {
        //Printing additional information to get information why highlighting was stuck
        sessions.forEach {
          val editor = it.key.editor
          printCodeAnalyzerStatistic(editor)
          printFileStatusMapInfo(editor)
          printEditorInfo(editor)
        }
        LOG.info("Highlighting still in progress: ${sessions.keys.joinToString(separator = ",\n") { it.description }}")
      }
    }
  }

  fun waitAnalysisToFinish() {
    LOG.info("Waiting for code analysis to finish")
    if (LightEdit.owns(project)) {
      return
    }
    val timeout: Long = 5
    if (highlightingFinishedEverywhere.tryAcquire(timeout, TimeUnit.MINUTES)) {
      highlightingFinishedEverywhere.release()
    }
    else {
      LOG.error("Waiting for highlight to finish took more than $timeout minutes.")
    }
    LOG.info("Code analysis finished")
  }

  fun registerOpenedEditors(openedEditors: List<TextEditor>) {
    val listener = SimpleEditedDocumentsListener(project)
    synchronized(stateLock) {
      for (fileEditor in openedEditors.getWorthy()) {
        sessions[fileEditor] = ExceptionWithTime.createForOpenedEditor(fileEditor)
        fileEditor.editor.document.addDocumentListener(listener, fileEditor)
      }
      ensureLockedIfNeeded()
    }
  }

  fun registerEditedDocuments(textEditors: List<TextEditor>) {
    synchronized(stateLock) {
      for (fileEditor in textEditors.getWorthy()) {
        sessions[fileEditor] = ExceptionWithTime.createForEditedEditor(fileEditor)
      }
      ensureLockedIfNeeded()
    }
  }

  fun registerAnalysisStarted(fileEditors: Collection<TextEditor>) {
    val errors = mutableListOf<AssertionError>()
    val isStartedInDumbMode = runReadAction { DumbService.isDumb(project) }
    synchronized(stateLock) {
      for (editor in fileEditors) {
        LOG.info("daemon starting for ${editor.description}")
        val previousSessionStartTrace = sessions.put(editor, ExceptionWithTime.createForAnalysisStart(editor, isStartedInDumbMode))
        ExceptionWithTime.createIntersectionErrorIfNeeded(editor, previousSessionStartTrace)?.let { errors.add(it) }
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
          LOG.info("Invisible editor ${editor.description}")
          return InvisibleEditor(editor)
        }
        else if (isFinishedInDumbMode || isCancelled) {
          LOG.info("Unfinished editor isFinishedInDumbMode=$isFinishedInDumbMode, isCancelled=$isCancelled ${editor.description}")
          return IncompletelyHighlightedEditor(editor)
        }
        else {
          val isHighlighted = DaemonCodeAnalyzerImpl.isHighlightingCompleted(editor, project)
          LOG.info("Visible editor ${editor.description}\nisHighlighted $isHighlighted")
          return VisibleEditor(editor, isHighlighted)
        }
      }
    }
  }

  internal fun registerAnalysisFinished(highlightedEditors: Map<TextEditor, HighlightedEditor>) {
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
          LOG.info("daemon stopped for ${highlightedEditor.editor.description}, " +
                   "shouldWaitForHighlighting=${shouldWait}, " +
                   "editor.shouldWaitForNextHighlighting=${highlightedEditor.shouldWaitForNextHighlighting}")
          LOG.info("""daemon stopped for ${highlightedEditor.editor.description}, 
                   shouldWaitForHighlighting=${shouldWait},
                   highlightedEditor.shouldWaitForNextHighlighting=${highlightedEditor.shouldWaitForNextHighlighting} 
                  """
                     .trimIndent()
          )
          if (shouldWait) {
            ExceptionWithTime.markAnalysisFinished(exceptionWithTime)
          }
          else {
            iterator.remove()
            LOG.info(ExceptionWithTime.getLogHighlightingMessage(currentTime, highlightedEditor.editor, exceptionWithTime))
          }
        }
      }
      unlockIfNeeded()
    }
  }

  private fun printCodeAnalyzerStatistic(editor: Editor) {
    try {
      ReadAction.run<Throwable> {
        LOG.info("Analyzer status for ${editor.virtualFile.path}\n ${TrafficLightRenderer(project, editor.document).use { it.daemonCodeAnalyzerStatus }}")
      }
    }
    catch (_: Throwable) {
      LOG.warn("Print Analyzer status failed")
    }
  }

  internal fun printFileStatusMapInfo(editor: Editor) {
    try {
      val fileStatus = (DaemonCodeAnalyzerImpl.getInstance(project) as DaemonCodeAnalyzerImpl)
        .fileStatusMap
        .toString(editor.document)
      LOG.info("File status map $fileStatus")
    }
    catch (_: Throwable) {
      LOG.warn("Print Analyzer status map failed")
    }
  }

  internal fun printEditorInfo(editor: Editor) {
    try {
      LOG.info("""
        Editor document ${editor.virtualFile.path}
            document is in bulkUpdate ${editor.document.isInBulkUpdate}
      """.trimIndent());
    }
    catch (_: Throwable) {
      LOG.warn("Print editor info failed")
    }
  }

}

private class SimpleEditedDocumentsListener(private val project: Project) : BulkAwareDocumentListener.Simple {
  override fun beforeDocumentChangeNonBulk(event: DocumentEvent) {
    val file = FileDocumentManager.getInstance().getFile(event.document) ?: return

    val worthy = FileEditorManager.getInstance(project).getEditorList(file).getWorthy()
    if (worthy.isEmpty()) return
    project.service<CodeAnalysisStateListener>().registerEditedDocuments(worthy)
  }
}

internal class WaitForFinishedCodeAnalysisListener(private val project: Project) : DaemonCodeAnalyzer.DaemonListener {
  init {
    if (!ApplicationManagerEx.isInIntegrationTest()) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override fun daemonStarting(fileEditors: Collection<FileEditor>) {
    CodeAnalysisStateListener.LOG.info("daemon starting with ${fileEditors.size} unfiltered editors: " +
                                       fileEditors.joinToString(separator = "\n") { it.description })
    project.service<CodeAnalysisStateListener>().registerAnalysisStarted(fileEditors.getWorthy())
  }

  override fun daemonCanceled(reason: String, fileEditors: Collection<FileEditor>) {
    CodeAnalysisStateListener.LOG.info("daemon canceled by the reason of '$reason'")
    daemonStopped(fileEditors, true)
  }

  override fun daemonFinished(fileEditors: Collection<FileEditor>) {
    daemonStopped(fileEditors, false)
  }

  private fun daemonStopped(fileEditors: Collection<FileEditor>, isCancelled: Boolean) {
    val status = if(isCancelled) "cancelled" else "stopped"
    CodeAnalysisStateListener.LOG.info("daemon $status with ${fileEditors.size} unfiltered editors")
    val worthy = fileEditors.getWorthy()
    if (worthy.isEmpty()) return

    val highlightedEditors: Map<TextEditor, CodeAnalysisStateListener.HighlightedEditor> = runReadAction {
      val isFinishedInDumbMode = DumbService.isDumb(project)
      worthy.associateWith { CodeAnalysisStateListener.HighlightedEditor.create(it, project, isCancelled = isCancelled, isFinishedInDumbMode = isFinishedInDumbMode) }
    }

    project.service<CodeAnalysisStateListener>().registerAnalysisFinished(highlightedEditors)
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

    private class EditorOpened(editor: TextEditor) : ExceptionWithTime(message = "Previous editor opening trace (editor = ${editor.description})") {
      override val wasStartedInLimitedSetup: Boolean
        get() = true //because it's unknown
    }

    private class EditorEdited(editor: TextEditor) : ExceptionWithTime(message = "Previous editor edited trace (editor = ${editor.description})") {
      override val wasStartedInLimitedSetup: Boolean
        get() = true //because it's unknown
    }

    fun createForOpenedEditor(editor: TextEditor): ExceptionWithTime {
      return EditorOpened(editor)
    }

    fun createForEditedEditor(fileEditor: TextEditor): ExceptionWithTime {
      return EditorEdited(fileEditor)
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
        is DaemonAnalysisStarted -> "Total highlighting time is : ${currentTime - exceptionWithTime.timestamp} ms for ${editor.description}"
        is EditorOpened -> "Total time from opening to highlighting is : ${currentTime - exceptionWithTime.timestamp} ms; \n" +
                           "daemon start was not reported for editor ${editor.description}"
        is EditorEdited -> "Total time from editing to highlighting is : ${currentTime - exceptionWithTime.timestamp} ms; \n" +
                           "daemon start was not reported for editor ${editor.description}"
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
