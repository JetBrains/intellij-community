package com.jetbrains.performancePlugin.commands

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileOpenedSyncListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.ex.FileEditorWithProvider
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

class WaitForFinishedCodeAnalysis(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX = CMD_PREFIX + "waitForFinishedCodeAnalysis"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    context.project.service<ListenerState>().waitAnalysisToFinish()
  }

  override fun getName(): String {
    return PREFIX
  }
}

@Service(Service.Level.PROJECT)
class ListenerState(val project: Project, val cs: CoroutineScope) {

  private companion object {
    val LOG = logger<WaitForFinishedCodeAnalysis>()
  }

  private val stateLock = Any()
  private val sessions = ConcurrentHashMap<FileEditor, ExceptionWithTime>()
  private val highlightingFinishedEverywhere: Semaphore = Semaphore(1)
  private var locked: Boolean = false

  private fun ensureLockedIfNeeded() {
    synchronized(stateLock) {
      if (!sessions.isEmpty() && !locked) {
        LOG.info("Highlighting began")
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

      if (sessions.isEmpty()) {
        LOG.info("Highlighting done")
        LOG.info("Total opening time is : ${Duration.ofNanos(System.nanoTime() - StartUpMeasurer.getStartTime()).toMillis()}")
        highlightingFinishedEverywhere.release()
        locked = false
      }
      else {
        LOG.info("Highlighting still in progress: ${sessions.keys.joinToString(separator = ",\n") { it.description }}")
      }
    }
  }

  fun waitAnalysisToFinish() {
    LOG.info("Waiting for code analysis to finish")
    if (highlightingFinishedEverywhere.tryAcquire(1, TimeUnit.MINUTES)) {
      highlightingFinishedEverywhere.release()
    }
    LOG.info("Code analysis finished")
  }

  fun registerOpenedEditors(openedEditors: List<FileEditor>) {
    val worthy = openedEditors.getWorthy()
    if (worthy.isEmpty()) return

    synchronized(stateLock) {
      for (fileEditor in worthy) {
        sessions[fileEditor] = ExceptionWithTime.createForOpenedEditor(fileEditor)
      }
      ensureLockedIfNeeded()
    }
  }

  fun registerAnalysisStarted(fileEditors: Collection<FileEditor>) {
    val worthy = fileEditors.getWorthy()
    if (worthy.isEmpty()) return

    val errors = mutableListOf<AssertionError>()
    val isStartedInDumbMode = runReadAction { DumbService.isDumb(project) }
    synchronized(stateLock) {
      for (editor in worthy) {
        LOG.info("daemon starting for ${editor.description}")
        val previousSessionStartTrace = sessions.put(editor, ExceptionWithTime.createForAnalysisStart(editor, isStartedInDumbMode))
        ExceptionWithTime.createIntersectionErrorIfNeeded(editor, previousSessionStartTrace)?.let { errors.add(it) }
      }
      ensureLockedIfNeeded()
    }

    if (errors.isEmpty()) return
    cs.launch { errors.forEach { LOG.error(it) } }
  }

  interface HighlightedEditor {
    val editor: FileEditor
    val shouldWaitForHighlighting: Boolean

    private class IncompletelyHighlightedEditor(override val editor: FileEditor) : HighlightedEditor {
      override val shouldWaitForHighlighting
        get() = false
    }

    private class InvisibleEditor(override val editor: FileEditor) : HighlightedEditor {
      override val shouldWaitForHighlighting
        get() = false
    }

    private class VisibleEditor(override val editor: FileEditor, private val isHighlighted: Boolean) : HighlightedEditor {
      override val shouldWaitForHighlighting
        get() = !isHighlighted
    }

    companion object {
      @RequiresReadLock
      fun create(editor: FileEditor, project: Project): HighlightedEditor {
        if (UIUtil.isShowing(editor.getComponent())) {
          return VisibleEditor(editor, DaemonCodeAnalyzerImpl.isHighlightingCompleted(editor, project))
        }
        else {
          return InvisibleEditor(editor)
        }
      }

      fun createIncompletelyHighlighted(editor: FileEditor): HighlightedEditor {
        return IncompletelyHighlightedEditor(editor)
      }
    }
  }

  internal fun registerAnalysisFinished(highlightedEditors: List<HighlightedEditor>) {
    val currentTime = System.currentTimeMillis()
    synchronized(stateLock) {
      for (highlightedEditor in highlightedEditors) {
        LOG.info("daemon stopped for ${highlightedEditor.editor.description}, " +
                 "shouldWaitForHighlighting=${highlightedEditor.shouldWaitForHighlighting}")
        val exceptionWithTime: ExceptionWithTime? = sessions[highlightedEditor.editor]
        if (highlightedEditor.shouldWaitForHighlighting || exceptionWithTime?.wasStartedInLimitedSetup == true) {
          ExceptionWithTime.markAnalysisFinished(exceptionWithTime)
        }
        else {
          val oldData = sessions.remove(highlightedEditor.editor)
          LOG.info(ExceptionWithTime.getLogHighlightingMessage(currentTime, highlightedEditor.editor, oldData))
        }
      }
      unlockIfNeeded()
    }
  }
}

internal class WaitForFinishedCodeAnalysisListener(private val project: Project) : DaemonCodeAnalyzer.DaemonListener {
  init {
    if (!ApplicationManagerEx.isInIntegrationTest()) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override fun daemonStarting(fileEditors: Collection<FileEditor>) {
    project.service<ListenerState>().registerAnalysisStarted(fileEditors.getWorthy())
  }

  override fun daemonCanceled(reason: String, fileEditors: Collection<FileEditor>) {
    daemonStopped(fileEditors, true)
  }

  override fun daemonFinished(fileEditors: Collection<FileEditor>) {
    daemonStopped(fileEditors, false)
  }

  private fun daemonStopped(fileEditors: Collection<FileEditor>, isCancelled: Boolean) {
    val worthy = fileEditors.getWorthy()
    if (worthy.isEmpty()) return

    val highlightedEditors: List<ListenerState.HighlightedEditor> = runReadAction {
      if (isCancelled || DumbService.isDumb(project)) {
        worthy.map { ListenerState.HighlightedEditor.createIncompletelyHighlighted(it) }
      }
      else {
        worthy.map { ListenerState.HighlightedEditor.create(it, project) }
      }
    }

    project.service<ListenerState>().registerAnalysisFinished(highlightedEditors)
  }
}

// See DaemonFusReporter. Reality in DaemonCodeAnalyzerImpl is a bit more complicated, probably including other editors if file has Psi
private fun isWorthAnalyzing(fileEditor: FileEditor): Boolean =
  fileEditor is TextEditor &&
  fileEditor.editor.editorKind == EditorKind.MAIN_EDITOR

private fun Collection<FileEditor>.getWorthy() = filter(::isWorthAnalyzing)

internal class WaitForFinishedCodeAnalysisFileEditorListener : FileOpenedSyncListener {
  init {
    if (!ApplicationManagerEx.isInIntegrationTest()) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override fun fileOpenedSync(source: FileEditorManager, file: VirtualFile, editorsWithProviders: List<FileEditorWithProvider>) {
    source.project.service<ListenerState>().registerOpenedEditors(editorsWithProviders.map { it.fileEditor }.getWorthy())
  }
}

private sealed class ExceptionWithTime(override val message: String?) : Exception(message) {
  val timestamp: Long = System.currentTimeMillis()
  abstract val wasStartedInLimitedSetup: Boolean

  companion object {
    private const val DAEMON_ANALYSIS_START_MESSAGE = "Previous daemon start trace (editor ="
    private const val EDITOR_OPENED_MESSAGE = "Previous editor opening trace (editor ="

    private class DaemonAnalysisStarted(editor: FileEditor, override val wasStartedInLimitedSetup: Boolean) :
      ExceptionWithTime(message = "$DAEMON_ANALYSIS_START_MESSAGE $editor)") {
      private var analysisFinished = false

      fun markAnalysisFinished() {
        analysisFinished = true
      }

      fun isNotFinished() = !analysisFinished
    }

    private class EditorOpened(editor: FileEditor) : ExceptionWithTime(message = "$EDITOR_OPENED_MESSAGE $editor)") {
      override val wasStartedInLimitedSetup: Boolean
        get() = true //because it's unknown
    }

    fun createForOpenedEditor(editor: FileEditor): ExceptionWithTime {
      return EditorOpened(editor)
    }

    fun createForAnalysisStart(editor: FileEditor, isStartedInDumbMode: Boolean): ExceptionWithTime {
      return DaemonAnalysisStarted(editor, isStartedInDumbMode)
    }

    fun markAnalysisFinished(exceptionWithTime: ExceptionWithTime?) {
      (exceptionWithTime as? DaemonAnalysisStarted)?.markAnalysisFinished()
    }

    fun getLogHighlightingMessage(currentTime: Long, editor: FileEditor, exceptionWithTime: ExceptionWithTime?): String {
      return when (exceptionWithTime) {
        null -> "Editor ${editor} wasn't opened, and highlighting didn't start, but it finished, and the editor was highlighted"
        is DaemonAnalysisStarted -> "Total highlighting time is : ${currentTime - exceptionWithTime.timestamp} ms for ${editor.description}"
        is EditorOpened -> "Total time from opening to highlighting is : ${currentTime - exceptionWithTime.timestamp} ms; \n" +
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

private val FileEditor.description: String
  get() = "${hashCode()} ${toString()}"
