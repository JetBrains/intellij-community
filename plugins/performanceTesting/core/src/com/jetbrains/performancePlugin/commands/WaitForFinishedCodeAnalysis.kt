// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.codeInsight.daemon.impl.TrafficLightRenderer
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.ide.lightEdit.LightEdit
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.event.BulkAwareDocumentListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.ex.EditorMarkupModel
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileOpenedSyncListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.fileEditor.ex.FileEditorWithProvider
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.util.use
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.diagnostic.startUpPerformanceReporter.FUSProjectHotStartUpMeasurer
import com.intellij.util.StackTraceSnapshot
import com.intellij.util.asDisposable
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.ui.EDT
import com.intellij.util.ui.UIUtil
import com.jetbrains.performancePlugin.utils.HighlightingTestUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.measureTime

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

private fun isTrafficLightExists(editor: Editor): Boolean {
  //MD file in preview mode doesn't have traffic light.
  //TODO Learn how to determine MD file view mode
  val isMdFile = editor.virtualFile?.extension?.contains("md", ignoreCase = true) ?: false
  return (editor.markupModel as EditorMarkupModel).errorStripeRenderer != null || isMdFile
}

private fun checkTrafficLightRenderer() = java.lang.Boolean.getBoolean("is.test.traffic.light")

private fun isDumbModeHighlightingFinal() = java.lang.Boolean.getBoolean(CodeAnalysisStateListener.DUMB_MODE_HIGHLIGHTING_IS_FINAL_PROPERTY)

private val LOG = logger<WaitForFinishedCodeAnalysis>()
class WaitForFinishedCodeAnalysis(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: String = CMD_PREFIX + "waitForFinishedCodeAnalysis"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    LOG.info("WaitForFinishedCodeAnalysis started its execution")
    val duration = measureTime {
      context.project.service<CodeAnalysisStateListener>().waitAnalysisToFinish()
    }
    LOG.info("WaitForFinishedCodeAnalysis finished in ${duration.inWholeMilliseconds} ms")
  }

  override fun getName(): String {
    return PREFIX
  }
}

@Service(Service.Level.PROJECT)
class CodeAnalysisStateListener(val project: Project, val cs: CoroutineScope) {
  companion object {
    /**
     * A product that holds the project in dumb mode on purpose, such as JetBrains Light, highlights in dumb mode only.
     * When this system property is `true`, a daemon pass that finishes in dumb mode counts as the complete highlighting.
     */
    const val DUMB_MODE_HIGHLIGHTING_IS_FINAL_PROPERTY: String = "performance.testing.dumb.mode.highlighting.is.final"
  }

  private val stateLock = Any()
  private val filesYetToStartHighlighting = ConcurrentHashMap<VirtualFile, Unit>()
  private val sessions = ConcurrentHashMap<TextEditor, HighlightingSessionStart>()
  private val waitingJobs: MutableList<CompletableFuture<Unit>> = Collections.synchronizedList(mutableListOf<CompletableFuture<Unit>>())
  private var locked: Boolean = false

  init {
    EditorFactory.getInstance().addEditorFactoryListener(object : EditorFactoryListener {
      override fun editorReleased(event: EditorFactoryEvent) {
        forgetReleasedEditor(event.editor)
      }
    }, cs.asDisposable())
  }

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
          Total opening time is : ${(System.nanoTime() - StartUpMeasurer.getStartTime()).nanoseconds.inWholeMilliseconds} ms
         """)
        for (job in waitingJobs) {
          job.complete(Unit)
        }
        waitingJobs.clear()
        locked = false
      }
      else {
        //Printing additional information to get information why highlighting was stuck
        //TODO Temporary disable printStatistic(). AT-2276
        LOG.info("""Highlighting still in progress. 
                  Remaining sessions:
                     ${sessions.keys.joinToString(separator = ";\n    ") { it.description }}
                  Remaining files:
                    ${filesYetToStartHighlighting.keys.joinToString(separator = ";\n    ") { it.name }}
                  """)
      }
    }
  }

  /**
   * @throws TimeoutException when stopped due to provided [timeout]
   */
  @RequiresBackgroundThread(generateAssertion = false /* IJPL-115548 */)
  suspend fun waitAnalysisToFinish(timeout: Duration = 5.minutes, throws: Boolean = false, logsError: Boolean = true) {
    if (EDT.isCurrentThreadEdt()) {
      throw AssertionError("waitAnalysisToFinish should not be called from EDT otherwise there will be freezes")
    }
    LOG.info("Waiting for code analysis to finish in $timeout")
    val future = CompletableFuture<Unit>()
    future.orTimeout(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
    coroutineScope {
      launch {
        while (true) {
          @Suppress("TestOnlyProblems")
          if (!ApplicationManagerEx.getApplication().isHeadlessEnvironment && !FUSProjectHotStartUpMeasurer.isHandlingFinished() && !future.isDone) {
            delay(500.milliseconds)
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
    catch (e: CompletionException) {
      val errorText = "Waiting for highlight to finish took more than $timeout."
      printCurrentStatus()

      if (logsError) {
        LOG.error(errorText, e)
      }
      if (throws) {
        throw TimeoutException(errorText)
      }
    }
    LOG.info("Code analysis complete")
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
    val hasWorthyEditor =
      FileEditorManager.getInstance(project).getEditorList(file).getWorthy().any { UIUtil.isShowing(it.editor.component) }
    if (!hasWorthyEditor) return
    synchronized(stateLock) {
      filesYetToStartHighlighting.put(file, Unit)
      ensureLockedIfNeeded()
    }
  }

  fun registerDaemonStarted(fileEditors: Collection<TextEditor>) {
    val errors = mutableListOf<AssertionError>()
    val isStartedInDumbMode = !isDumbModeHighlightingFinal() && runReadActionBlocking { DumbService.isDumb(project) }
    synchronized(stateLock) {
      for (editor in fileEditors) {
        val previousSessionStartTrace = sessions.put(editor, HighlightingSessionStart.createForAnalysisStart(editor, isStartedInDumbMode))
        HighlightingSessionStart.createIntersectionErrorIfNeeded(editor, previousSessionStartTrace)?.let { errors.add(it) }
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
      fun createCancelled(editor: TextEditor): HighlightedEditor {
        if (!UIUtil.isShowing(editor.getComponent())) {
          return createInvisible(editor)
        }
        LOG.info("Creating unfinished editor isCancelled=true ${editor.description}")
        return IncompletelyHighlightedEditor(editor)
      }

      @RequiresReadLock(generateAssertion = false /* IJPL-115548 */)
      fun create(editor: TextEditor, project: Project, isFinishedInDumbMode: Boolean): HighlightedEditor {
        if (!UIUtil.isShowing(editor.getComponent())) {
          return createInvisible(editor)
        }
        else if (isFinishedInDumbMode) {
          LOG.info("Creating unfinished editor isFinishedInDumbMode=true ${editor.description}")
          return IncompletelyHighlightedEditor(editor)
        }
        else {
          val isHighlighted = DaemonCodeAnalyzerImpl.isHighlightingCompleted(editor, project)
          LOG.info("Creating visible editor ${editor.description}\nisHighlighted $isHighlighted")
          return VisibleEditor(editor, isHighlighted)
        }
      }

      private fun createInvisible(editor: TextEditor): HighlightedEditor {
        LOG.info("Creating invisible editor ${editor.description}")
        return InvisibleEditor(editor)
      }
    }
  }

  internal fun registerDaemonFinishedOrCancelled(highlightedEditors: Map<TextEditor, HighlightedEditor>, status: String, traceId: UUID) {
    val currentTime = System.currentTimeMillis()
    synchronized(stateLock) {
      val iterator = sessions.entries.iterator()
      while (iterator.hasNext()) {
        val (editor, sessionStart) = iterator.next()
        val isHiglighted = DaemonCodeAnalyzerImpl.isHighlightingCompleted(editor, project)
        val highlightedEditor = highlightedEditors[editor]

        if (status == "stopped" && checkTrafficLightRenderer() && !isTrafficLightExists(editor.editor)) {
          LOG.error("Highlighting traffic light should be shown in the top right corner of the editor, in case of $status")
          takeFullScreenshot("traffic-light-screenshot")
        }

        if (highlightedEditor != null) {
          val shouldWait = highlightedEditor.shouldWaitForNextHighlighting || sessionStart.wasStartedInLimitedSetup
          LOG.info(""" 
            Registering daemon $status for:
              for ${highlightedEditor.editor.description},
              shouldWaitForHighlighting = ${shouldWait},
              shouldWaitForNextHighlighting = ${highlightedEditor.shouldWaitForNextHighlighting},
              traceId = $traceId
        """.trimIndent())

          if (shouldWait) {
            HighlightingSessionStart.markAnalysisFinished(sessionStart)
          }
          else {
            iterator.remove()
            LOG.info(HighlightingSessionStart.getLogHighlightingMessage(currentTime, highlightedEditor.editor, sessionStart))
          }
        }
        else if (!UIUtil.isShowing(editor.getComponent())) {
          LOG.info("Editor ${editor.description} will be removed, it's not shown")
          iterator.remove()
        }
        else if (isHiglighted) {
          LOG.info("Editor ${editor.description} was highlighted and should be removed")
          iterator.remove()
        }
      }

      if (!filesYetToStartHighlighting.isEmpty()) {
        val fileEditorManager = FileEditorManager.getInstance(project)
        val filesIterator = filesYetToStartHighlighting.entries.iterator()
        while (filesIterator.hasNext()) {
          val (file, _) = filesIterator.next()
          val hasWorthyEditor =
            fileEditorManager.getEditors(file).toMutableList().getWorthy().any { UIUtil.isShowing(it.editor.getComponent()) }
          if (!hasWorthyEditor) {
            filesIterator.remove()
          }
        }
      }

      unlockIfNeeded()
    }
  }

  /**
   * A released editor gets no more daemon events, so its release ends what this service tracks for it.
   * The release ends the highlighting session of the editor.
   * The release of a main editor also ends the wait for its file when the project has no other main editor of the file,
   * shown or not: a main editor that is not shown yet still gets highlighted.
   */
  private fun forgetReleasedEditor(released: Editor) {
    @Suppress("UsePropertyAccessSyntax")  // inhibit weak warning, for property access is a warning
    if (sessions.isEmpty() && filesYetToStartHighlighting.isEmpty()) {
      return
    }

    synchronized(stateLock) {
      val isSessionEnded = sessions.keys.removeIf { it.editor === released }
      val file = FileDocumentManager.getInstance().getFile(released.document)
      val isFileEnded = file != null && released.editorKind == EditorKind.MAIN_EDITOR &&
                        filesYetToStartHighlighting.containsKey(file) && !hasOtherMainEditor(released)
      if (isFileEnded) {
        filesYetToStartHighlighting.remove(file)
      }
      if (isSessionEnded || isFileEnded) {
        LOG.info("Released editor of ${file?.name} ends its highlighting session: $isSessionEnded, its file: $isFileEnded")
        unlockIfNeeded()
      }
    }
  }

  private fun hasOtherMainEditor(released: Editor): Boolean {
    return EditorFactory.getInstance().editors(released.document, project).anyMatch {
      it !== released && it.editorKind == EditorKind.MAIN_EDITOR
    }
  }

  internal fun printCurrentStatus() {
    sessions.forEach {
      val editor = it.key
      printCodeAnalyzerStatus(editor)
      printFileStatusMapInfo(editor.editor)
    }
  }

  internal fun printCodeAnalyzerStatus(editor: TextEditor) {
    //Status can't be retrieved from EDT
    if (EDT.isCurrentThreadEdt()) return
    try {
      ReadAction.run<Throwable> {
        LOG.info("Analyzer status for ${editor.description}\n ${
          TrafficLightRenderer(project, editor.editor).use { it.daemonCodeAnalyzerStatus }
        }")
      }
    }
    catch (ex: CancellationException) {
      throw ex
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
    LOG.info(""""Daemon starting, editors size = ${fileEditors.size}, editors:
                                       ${fileEditors.joinToString(separator = "\n    ") { it.description }}
                                       """)
    project.service<CodeAnalysisStateListener>().registerDaemonStarted(fileEditors.getWorthy())
  }

  override fun daemonCanceled(reason: String, fileEditors: Collection<FileEditor>) {
    val traceId = UUID.randomUUID()
    daemonFinishedOrCancelled(fileEditors, true, traceId, reason)
  }

  override fun daemonFinished(fileEditors: Collection<FileEditor>) {
    val traceId = UUID.randomUUID()
    daemonFinishedOrCancelled(fileEditors, false, traceId)
  }

  private fun daemonFinishedOrCancelled(fileEditors: Collection<FileEditor>, isCancelled: Boolean, traceId: UUID, reason: String = "") {
    val status = if (isCancelled) "cancelled" else "stopped"
    printFileEditors(fileEditors, status, traceId, reason)

    val worthy = fileEditors.getWorthy()
    if (worthy.isEmpty()) return

    val highlightedEditors: Map<TextEditor, CodeAnalysisStateListener.HighlightedEditor> =
      if (isCancelled) {
        // Take no read lock here. daemonCanceled can run under the DaemonCodeAnalyzerImpl monitor.
        worthy.associateWith { CodeAnalysisStateListener.HighlightedEditor.createCancelled(it) }
      }
      else {
        runReadActionBlocking {
          val isFinishedInDumbMode = !isDumbModeHighlightingFinal() && DumbService.isDumb(project)
          worthy.associateWith {
            CodeAnalysisStateListener.HighlightedEditor.create(it, project, isFinishedInDumbMode = isFinishedInDumbMode)
          }
        }
      }

    project.service<CodeAnalysisStateListener>().registerDaemonFinishedOrCancelled(highlightedEditors, status, traceId)
  }

  fun printFileEditors(fileEditors: Collection<FileEditor>, status: String, traceId: UUID, reason: String) {
    try {
      val editorsMessage = fileEditors.joinToString(separator = "\n    ") { fileEditor -> fileEditor.description }
      // Each typed character cancels the daemon on the EDT, and the file status map prints every document of the IDE.
      val fileStatusMap = if (reason.isEmpty()) "; fileStatusMap:${DaemonCodeAnalyzerEx.getInstanceEx(project).fileStatusMap}" else ""
      LOG.info("Daemon analyzer status $status, editors size = ${fileEditors.size}, reason = $reason, traceId = $traceId, editors = $editorsMessage$fileStatusMap")
    }
    catch (e: Exception) {
      LOG.warn(e)
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

/**
 * The start of the highlighting session of an editor. A session lives as long as its editor, so it keeps the stack of the daemon start
 * as [StackTraceSnapshot] data. The backtrace of a kept Throwable would keep each plugin on that stack loaded.
 */
private sealed class HighlightingSessionStart(private val message: String) {
  val timestamp: Long = System.currentTimeMillis()
  private val startTrace = StackTraceSnapshot.captureCallerOf(HighlightingSessionStart::class.java, Int.MAX_VALUE)
  abstract val wasStartedInLimitedSetup: Boolean

  /** The stack of the daemon start as a new Throwable with the message of this start, for an error report. */
  fun toThrowable(): Throwable = startTrace.toThrowable(message)

  companion object {
    val highlightingScopeName: String = "highlighting"

    private class DaemonAnalysisStarted(editor: TextEditor, override val wasStartedInLimitedSetup: Boolean) :
      HighlightingSessionStart(message = "Previous daemon start trace (editor = ${editor.description})") {
      private var analysisFinished = false

      fun markAnalysisFinished() {
        analysisFinished = true
      }

      fun isNotFinished() = !analysisFinished
    }

    fun createForAnalysisStart(editor: TextEditor, isStartedInDumbMode: Boolean): HighlightingSessionStart {
      return DaemonAnalysisStarted(editor, isStartedInDumbMode)
    }

    fun markAnalysisFinished(sessionStart: HighlightingSessionStart?) {
      (sessionStart as? DaemonAnalysisStarted)?.markAnalysisFinished()
    }

    fun getLogHighlightingMessage(currentTime: Long, editor: TextEditor, sessionStart: HighlightingSessionStart?): String {
      return when (sessionStart) {
        null -> "Editor ${editor} wasn't opened, and highlighting didn't start, but it finished, and the editor was highlighted"
        is DaemonAnalysisStarted -> {
          HighlightingTestUtil.storeProcessFinishedTime(highlightingScopeName, "highlighting_${editor.file.name}")
          "Total highlighting time is : ${currentTime - sessionStart.timestamp} ms for ${editor.description}"
        }
      }
    }

    fun createIntersectionErrorIfNeeded(editor: FileEditor, previousSessionStartTrace: HighlightingSessionStart?): AssertionError? {
      if (previousSessionStartTrace is DaemonAnalysisStarted && previousSessionStartTrace.isNotFinished()) {
        val err = AssertionError("Overlapping highlighting sessions")
        err.addSuppressed(Exception("Current daemon start trace (editor = ${editor.description} )"))
        err.addSuppressed(previousSessionStartTrace.toThrowable())
        return err
      }
      return null
    }
  }
}
