// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.buildtool

import com.intellij.build.BuildProgressListener
import com.intellij.build.DefaultBuildDescriptor
import com.intellij.build.FilePosition
import com.intellij.build.SyncViewManager
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.BuildIssueEvent
import com.intellij.build.events.EventResult
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.MessageEventResult
import com.intellij.build.events.impl.BuildIssueEventImpl
import com.intellij.build.events.impl.DerivedResultImpl
import com.intellij.build.events.impl.FailureResultImpl
import com.intellij.build.events.impl.FileMessageEventImpl
import com.intellij.build.events.impl.FinishBuildEventImpl
import com.intellij.build.events.impl.FinishEventImpl
import com.intellij.build.events.impl.MessageEventImpl
import com.intellij.build.events.impl.OutputBuildEventImpl
import com.intellij.build.events.impl.StartBuildEventImpl
import com.intellij.build.events.impl.StartEventImpl
import com.intellij.build.events.impl.SuccessResultImpl
import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.issue.BuildIssueException
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.util.ExceptionUtil
import com.intellij.util.ThreeState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.idea.maven.buildtool.quickfix.MavenFullSyncQuickFix
import org.jetbrains.idea.maven.buildtool.quickfix.OffMavenOfflineModeQuickFix
import org.jetbrains.idea.maven.buildtool.quickfix.OpenMavenSettingsQuickFix
import org.jetbrains.idea.maven.execution.SyncBundle
import org.jetbrains.idea.maven.externalSystemIntegration.output.importproject.quickfixes.DownloadArtifactBuildIssue
import org.jetbrains.idea.maven.model.MavenProjectProblem
import org.jetbrains.idea.maven.project.MavenProjectBundle
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.project.MavenWorkspaceSettingsComponent
import org.jetbrains.idea.maven.server.MavenArtifactEvent
import org.jetbrains.idea.maven.server.MavenArtifactEvent.ArtifactEventType
import org.jetbrains.idea.maven.server.MavenServerConsoleEvent
import org.jetbrains.idea.maven.server.MavenServerConsoleIndicator
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenUtil
import java.io.File
import java.text.MessageFormat
import java.util.concurrent.CancellationException

class MavenSyncConsole(private val myProject: Project, parentScope: CoroutineScope) : MavenEventHandler, MavenBuildIssueHandler {
  private val mySyncView: BuildProgressListener = myProject.getService(SyncViewManager::class.java)

  // Read synchronously from arbitrary threads via getTaskId(); mutated only on the consumer coroutine.
  @Volatile
  private var mySyncId = createTaskId()

  private var finished = false
  private var started = false
  private var syncTransactionStarted = false
  private var hasErrors = false
  private var hasUnresolved = false
  private val JAVADOC_AND_SOURCE_CLASSIFIERS = setOf("javadoc", "sources", "test-javadoc", "test-sources")
  private val shownIssues = HashSet<String>()

  private val myPostponed = ArrayList<() -> Unit>()

  private var myStartedSet = LinkedHashSet<Pair<Any, String>>()

  private val events = Channel<() -> Unit>(Channel.UNLIMITED)

  init {
    parentScope.launch {
      for (action in events) {
        try {
          action()
        }
        catch (e: Exception) {
          MavenLog.LOG.warn(e)
        }
      }
    }
  }

  private fun enqueue(action: () -> Unit) {
    events.trySend(action)
  }

  /**
   * Suspends until every action enqueued before this call has been executed on the consumer.
   * Used at the end of the import flow so that callers (and tests) observe a fully drained console
   * with all [mySyncView] events already emitted.
   */
  suspend fun awaitAllEventsProcessed() {
    val processed = CompletableDeferred<Unit>()
    if (events.trySend { processed.complete(Unit) }.isSuccess) {
      processed.await()
    }
  }

  class RescheduledMavenDownloadJobException(override val message: String?) : CancellationException(message)

  fun startImport(explicit: Boolean) = enqueue {
    doStartImport(explicit)
  }

  private fun doStartImport(explicit: Boolean) {
    if (started) {
      return
    }
    started = true
    finished = false
    hasErrors = false
    hasUnresolved = false
    shownIssues.clear()
    mySyncId = createTaskId()

    val descriptor =
      DefaultBuildDescriptor(mySyncId, SyncBundle.message("maven.sync.title"), myProject.basePath!!, System.currentTimeMillis())
    descriptor.isActivateToolWindowWhenFailed = explicit
    descriptor.isActivateToolWindowWhenAdded = false
    descriptor.isNavigateToError = if (explicit) ThreeState.YES else ThreeState.NO

    mySyncView.onEvent(mySyncId, StartBuildEventImpl(descriptor, SyncBundle.message("maven.sync.project.title", myProject.name)))
    debugLog("maven sync: started importing $myProject")

    myPostponed.forEach(this::doIfImportInProcess)
    myPostponed.clear()
  }

  private fun createTaskId() = ExternalSystemTaskId.create(MavenUtil.SYSTEM_ID, ExternalSystemTaskType.RESOLVE_PROJECT, myProject)

  fun getTaskId() = mySyncId

  fun addText(@Nls text: String) = addText(text, true)

  fun addText(@Nls text: String, stdout: Boolean) = enqueue {
    printText(mySyncId, text, true)
  }

  fun addWrapperProgressText(@Nls text: String) = enqueue {
    printText(SyncBundle.message("maven.sync.wrapper"), text, true)
  }

  private fun printText(parentId: Any, @Nls text: String, stdout: Boolean): Unit = doIfImportInProcess {
    if (StringUtil.isEmpty(text)) {
      return
    }
    val toPrint = if (text.endsWith('\n')) text else "$text\n"
    mySyncView.onEvent(mySyncId, OutputBuildEventImpl(parentId, toPrint, stdout))
  }

  fun addBuildEvent(buildEvent: BuildEvent) = enqueue {
    doIfImportInProcess {
      if (buildEvent is BuildIssueEvent) {
        addBuildIssueImpl(buildEvent.issue, buildEvent.kind)
      }
      else {
        mySyncView.onEvent(mySyncId, buildEvent)
      }
    }
  }

  fun addWarning(@Nls text: String, @Nls description: String) = addWarning(text, description, null)

  override fun addBuildIssue(issue: BuildIssue, kind: MessageEvent.Kind) = enqueue {
    addBuildIssueImpl(issue, kind)
  }

  private fun addBuildIssueImpl(issue: BuildIssue, kind: MessageEvent.Kind): Unit = doIfImportInProcessOrPostpone {
    if (!newIssue(issue.title + issue.description)) return@doIfImportInProcessOrPostpone
    mySyncView.onEvent(mySyncId, BuildIssueEventImpl(mySyncId, issue, kind))
    hasErrors = hasErrors || kind == MessageEvent.Kind.ERROR
  }

  fun addWarning(@Nls text: String, @Nls description: String, filePosition: FilePosition?) = enqueue {
    addWarningImpl(text, description, filePosition)
  }

  private fun addWarningImpl(@Nls text: String, @Nls description: String, filePosition: FilePosition?): Unit = doIfImportInProcess {
    if (!newIssue(text + description + filePosition)) return
    if (filePosition == null) {
      mySyncView.onEvent(mySyncId,
                         MessageEventImpl(mySyncId, MessageEvent.Kind.WARNING, SyncBundle.message("maven.sync.group.compiler"), text,
                                          description))
    }
    else {
      mySyncView.onEvent(mySyncId,
                         FileMessageEventImpl(mySyncId, MessageEvent.Kind.WARNING, SyncBundle.message("maven.sync.group.compiler"), text,
                                              description, filePosition))
    }
  }

  private fun newIssue(s: String): Boolean {
    return shownIssues.add(s)
  }

  fun finishImport(showFullSyncQuickFix: Boolean = false) = enqueue {
    finishImportImpl(showFullSyncQuickFix)
  }

  private fun finishImportImpl(showFullSyncQuickFix: Boolean = false) {
    debugLog("Maven sync: finishImport")
    doFinish(showFullSyncQuickFix)
  }

  fun terminated(exitCode: Int) = enqueue {
    doIfImportInProcess {
      if (EXIT_CODE_OK == exitCode || EXIT_CODE_SIGTERM == exitCode) doFinish() else doTerminate(exitCode)
    }
  }

  private fun doTerminate(exitCode: Int) {
    if (syncTransactionStarted) {
      debugLog("Maven sync: sync transaction is still not finished, postpone build finish event")
      return
    }
    val tasks = myStartedSet.toList().asReversed()
    debugLog("Tasks $tasks are not completed! Force complete")
    tasks.forEach { completeTask(it.first, it.second, FailureResultImpl(SyncBundle.message("maven.sync.failure.terminated", exitCode))) }

    mySyncView.onEvent(mySyncId, FinishBuildEventImpl(mySyncId, null, System.currentTimeMillis(), "",
                                                      FailureResultImpl(SyncBundle.message("maven.sync.failure.terminated", exitCode))))
    finished = true
    started = false
  }

  fun startWrapperResolving() = enqueue {
    startWrapperResolvingImpl()
  }

  private fun startWrapperResolvingImpl() {
    if (!started || finished) {
      doStartImport(true)
    }
    startTask(mySyncId, SyncBundle.message("maven.sync.wrapper"))
  }

  fun finishWrapperResolving(e: Throwable? = null) = enqueue {
    finishWrapperResolvingImpl(e)
  }

  private fun finishWrapperResolvingImpl(e: Throwable?) {
    if (e != null) {
      addBuildIssueImpl(object : BuildIssue {
        override val title: String = SyncBundle.message("maven.sync.wrapper.failure")
        override val description: String = SyncBundle.message("maven.sync.wrapper.failure.description",
                                                              e.localizedMessage, OpenMavenSettingsQuickFix.ID)
        override val quickFixes: List<BuildIssueQuickFix> = listOf(OpenMavenSettingsQuickFix())
        override fun getNavigatable(project: Project): Navigatable? = null
      }, MessageEvent.Kind.WARNING)
    }
    completeTask(mySyncId, SyncBundle.message("maven.sync.wrapper"), SuccessResultImpl())
  }

  fun notifyReadingProblems(file: VirtualFile) = enqueue {
    notifyReadingProblemsImpl(file)
  }

  private fun notifyReadingProblemsImpl(file: VirtualFile): Unit = doIfImportInProcess {
    debugLog("reading problems in $file")
    hasErrors = true
    val desc = SyncBundle.message("maven.sync.failure.error.reading.file", file.path)
    mySyncView.onEvent(mySyncId,
                       FileMessageEventImpl(mySyncId, MessageEvent.Kind.ERROR, SyncBundle.message("maven.sync.group.error"), desc, desc,
                                            FilePosition(File(file.path), -1, -1)))
  }

  fun notifyDownloadSourcesProblem(e: Exception) = enqueue {
    notifyDownloadSourcesProblemImpl(e)
  }

  private fun notifyDownloadSourcesProblemImpl(e: Exception) {
    val messageEvent = when (e) {
      // a new job was submitted so no need to show anything to the user
      is RescheduledMavenDownloadJobException -> null
      // a normal cancellation happened
      is CancellationException -> {
        val message = MavenProjectBundle.message("maven.downloading.cancelled")
        MessageEventImpl(mySyncId, MessageEvent.Kind.INFO, SyncBundle.message("build.event.title.error"), message, message)
      }
      else -> {
        hasErrors = true
        createMessageEvent(myProject, mySyncId, e)
      }
    }
    if (messageEvent != null) {
      mySyncView.onEvent(mySyncId, messageEvent)
    }
  }

  fun showProblem(problem: MavenProjectProblem) = enqueue {
    showProblemImpl(problem)
  }

  private fun showProblemImpl(problem: MavenProjectProblem): Unit = doIfImportInProcess {
    hasErrors = hasErrors || problem.isError
    val group = if (problem.isError) SyncBundle.message("maven.sync.group.error") else SyncBundle.message("maven.sync.group.warning")
    val kind = if (problem.isError) MessageEvent.Kind.ERROR else MessageEvent.Kind.WARNING
    val position = problem.getFilePosition()
    val message = problem.description ?: SyncBundle.message("maven.sync.failure.error.undefined.message")
    val detailedMessage = problem.description ?: SyncBundle.message("maven.sync.failure.error.undefined.detailed.message", problem.path)
    val eventImpl = FileMessageEventImpl(mySyncId, kind, group, message, detailedMessage, position)
    mySyncView.onEvent(mySyncId, eventImpl)
  }

  private fun MavenProjectProblem.getFilePosition(): FilePosition {
    val (line, column) = getPositionFromDescription() ?: getPositionFromPath() ?: (-1 to -1)
    val pathWithoutPosition = path.substringBeforeLast(":${line + 1}:$column")
    return FilePosition(File(pathWithoutPosition), line, column)
  }

  private fun MavenProjectProblem.getPositionFromDescription(): Pair<Int, Int>? {
    return getPosition(description, Regex("@(\\d+):(\\d+)"))
  }

  private fun MavenProjectProblem.getPositionFromPath(): Pair<Int, Int>? {
    return getPosition(path, Regex(":(\\d+):(\\d+)"))
  }

  private fun MavenProjectProblem.getPosition(source: String?, pattern: Regex): Pair<Int, Int>? {
    if (source == null) return null
    if (type == MavenProjectProblem.ProblemType.STRUCTURE) {
      val matchResults = pattern.findAll(source)
      val matchResult = matchResults.lastOrNull() ?: return null
      val (_, line, offset) = matchResult.groupValues
      return line.toInt() - 1 to offset.toInt()
    }
    return null
  }

  @ApiStatus.Internal
  fun addException(e: Throwable) = enqueue {
    addExceptionImpl(e)
  }

  private fun addExceptionImpl(e: Throwable) {
    if (started && !finished) {
      MavenLog.LOG.warn(e)
      hasErrors = true
      val buildIssueException = ExceptionUtil.findCause(e, BuildIssueException::class.java)
      if (buildIssueException != null) {
        addBuildIssueImpl(buildIssueException.buildIssue, MessageEvent.Kind.ERROR)
      }
      else {
        mySyncView.onEvent(mySyncId, createMessageEvent(myProject, mySyncId, e))
      }

    }
    else {
      doStartImport(true)
      addExceptionImpl(e)
      finishImportImpl()
    }
  }

  private fun getKeyPrefix(type: MavenServerConsoleIndicator.ResolveType): String {
    return when (type) {
      MavenServerConsoleIndicator.ResolveType.PLUGIN -> "maven.sync.plugins"
      MavenServerConsoleIndicator.ResolveType.DEPENDENCY -> "maven.sync.dependencies"
    }
  }

  private fun doFinish(showFullSyncQuickFix: Boolean = false) {
    if (syncTransactionStarted) {
      debugLog("Maven sync: sync transaction is still not finished, postpone build finish event")
      return
    }
    val tasks = myStartedSet.toList().asReversed()
    debugLog("Tasks $tasks are not completed! Force complete")
    tasks.forEach { completeTask(it.first, it.second, DerivedResultImpl()) }
    mySyncView.onEvent(mySyncId, FinishBuildEventImpl(mySyncId, null, System.currentTimeMillis(), "",
                                                      if (hasErrors) FailureResultImpl() else DerivedResultImpl()))

    attachOfflineQuickFix()
    if (showFullSyncQuickFix) {
      attachFullSyncQuickFix()
    }
    finished = true
    started = false
  }

  private fun attachFullSyncQuickFix() {
    try {
      mySyncView.onEvent(mySyncId, BuildIssueEventImpl(mySyncId, object : BuildIssue {
        override val title: String = "Sync Finished"
        override val description: String =
          "Sync finished. If there is something wrong with the project model, <a href=\"${MavenFullSyncQuickFix.ID}\">reload all projects</a>\n"
        override val quickFixes: List<BuildIssueQuickFix> = listOf(MavenFullSyncQuickFix())
        override fun getNavigatable(project: Project): Navigatable? = null
      }, MessageEvent.Kind.INFO))
    }
    catch (ignore: Exception) {
    }
  }

  private fun attachOfflineQuickFix() {
    try {
      val generalSettings = MavenWorkspaceSettingsComponent.getInstance(myProject).settings.generalSettings
      if (hasUnresolved && generalSettings.isWorkOffline) {
        mySyncView.onEvent(mySyncId, BuildIssueEventImpl(mySyncId, object : BuildIssue {
          override val title: String = "Dependency Resolution Failed"
          override val description: String = "<a href=\"${OffMavenOfflineModeQuickFix.ID}\">Switch Off Offline Mode</a>\n"
          override val quickFixes: List<BuildIssueQuickFix> = listOf(OffMavenOfflineModeQuickFix())
          override fun getNavigatable(project: Project): Navigatable? = null
        }, MessageEvent.Kind.ERROR))
      }
    }
    catch (ignore: Exception) {
    }
  }

  fun showArtifactBuildIssue(type: MavenServerConsoleIndicator.ResolveType, dependency: String, errorMessage: String?) = enqueue {
    showArtifactBuildIssueImpl(getKeyPrefix(type), dependency, errorMessage)
  }

  private fun showArtifactBuildIssueImpl(keyPrefix: String, dependency: String, errorMessage: String?): Unit = doIfImportInProcess {
    hasErrors = true
    hasUnresolved = true
    val umbrellaString = SyncBundle.message("${keyPrefix}.resolve")
    val errorString = SyncBundle.message("${keyPrefix}.resolve.error", dependency)
    startTask(mySyncId, umbrellaString)
    val buildIssue = DownloadArtifactBuildIssue.getIssue(errorString, errorMessage ?: errorString)
    mySyncView.onEvent(mySyncId, BuildIssueEventImpl(umbrellaString, buildIssue, MessageEvent.Kind.ERROR))
    printText(mySyncId, errorString, false)
  }

  fun showBuildIssue(buildIssue: BuildIssue) = enqueue {
    showBuildIssueImpl(buildIssue)
  }

  private fun showBuildIssueImpl(buildIssue: BuildIssue): Unit = doIfImportInProcess {
    hasErrors = true
    hasUnresolved = true
    val key = getKeyPrefix(MavenServerConsoleIndicator.ResolveType.DEPENDENCY)
    startTask(mySyncId, key)
    mySyncView.onEvent(mySyncId, BuildIssueEventImpl(key, buildIssue, MessageEvent.Kind.ERROR))
  }

  fun showBuildIssue(buildIssue: BuildIssue, kind: MessageEvent.Kind) = enqueue {
    showBuildIssueImpl(buildIssue, kind)
  }

  private fun showBuildIssueImpl(buildIssue: BuildIssue, kind: MessageEvent.Kind): Unit = doIfImportInProcess {
    hasErrors = hasErrors || kind == MessageEvent.Kind.ERROR
    val key = getKeyPrefix(MavenServerConsoleIndicator.ResolveType.DEPENDENCY)
    startTask(mySyncId, key)
    mySyncView.onEvent(mySyncId, BuildIssueEventImpl(key, buildIssue, kind))
  }

  private fun startTask(parentId: Any, @NlsSafe taskName: String): Unit = doIfImportInProcess {
    debugLog("Maven sync: start $taskName")
    if (myStartedSet.add(parentId to taskName)) {
      mySyncView.onEvent(mySyncId, StartEventImpl(taskName, parentId, System.currentTimeMillis(), taskName))
    }
  }


  private fun completeTask(parentId: Any, @NlsSafe taskName: String, result: EventResult): Unit = doIfImportInProcess {
    hasErrors = hasErrors || result is FailureResultImpl

    debugLog("Maven sync: complete $taskName with $result")
    if (myStartedSet.remove(parentId to taskName)) {
      mySyncView.onEvent(mySyncId, FinishEventImpl(taskName, parentId, System.currentTimeMillis(), taskName, result))
    }
  }

  fun finishPluginResolution() = enqueue {
    completeUmbrellaEvents(getKeyPrefix(MavenServerConsoleIndicator.ResolveType.PLUGIN))
  }

  fun finishArtifactsDownload() = enqueue {
    completeUmbrellaEvents(getKeyPrefix(MavenServerConsoleIndicator.ResolveType.DEPENDENCY))
  }

  private fun completeUmbrellaEvents(keyPrefix: String): Unit = doIfImportInProcess {
    val taskName = SyncBundle.message("${keyPrefix}.resolve")
    completeTask(mySyncId, taskName, DerivedResultImpl())
  }

  private fun downloadEventStarted(keyPrefix: String, dependency: String): Unit = doIfImportInProcess {
    val downloadString = SyncBundle.message("${keyPrefix}.download")
    val downloadArtifactString = SyncBundle.message("${keyPrefix}.artifact.download", dependency)
    startTask(mySyncId, downloadString)
    startTask(downloadString, downloadArtifactString)
  }

  private fun downloadEventCompleted(keyPrefix: String, dependency: String): Unit = doIfImportInProcess {
    val downloadString = SyncBundle.message("${keyPrefix}.download")
    val downloadArtifactString = SyncBundle.message("${keyPrefix}.artifact.download", dependency)
    printText(downloadArtifactString, downloadArtifactString, true)
    completeTask(downloadString, downloadArtifactString, SuccessResultImpl(false))
  }


  private fun downloadEventFailed(
    keyPrefix: String,
    @NlsSafe dependency: String,
    @NlsSafe error: String,
    @NlsSafe stackTrace: String?,
  ): Unit = doIfImportInProcess {
    val downloadString = SyncBundle.message("${keyPrefix}.download")

    val downloadArtifactString = SyncBundle.message("${keyPrefix}.artifact.download", dependency)
    if (isJavadocOrSource(dependency)) {
      printText(downloadArtifactString, SyncBundle.message("maven.sync.failure.dependency.not.found", dependency), true)
      completeTask(downloadString, downloadArtifactString, object : MessageEventResult {
        override fun getKind(): MessageEvent.Kind {
          return MessageEvent.Kind.WARNING
        }

        override fun getDetails(): String? {
          return SyncBundle.message("maven.sync.failure.dependency.not.found", dependency)
        }
      })

    }
    else {
      if (stackTrace != null && Registry.`is`("maven.spy.events.debug")) {
        printText(downloadArtifactString, stackTrace, false)
      }
      else {
        printText(downloadArtifactString, error, true)
      }
      completeTask(downloadString, downloadArtifactString, FailureResultImpl(error))
    }
  }

  private fun isJavadocOrSource(dependency: String): Boolean {
    val split = dependency.split(':')
    if (split.size < 4) {
      return false
    }
    val classifier = split.get(2)
    return JAVADOC_AND_SOURCE_CLASSIFIERS.contains(classifier)
  }

  private inline fun doIfImportInProcess(action: () -> Unit) {
    if (!started || finished) return
    action.invoke()
  }

  private fun doIfImportInProcessOrPostpone(action: () -> Unit) {
    if (!started || finished) {
      myPostponed.add(action)
    }
    else {
      action.invoke()
    }
  }

  @ApiStatus.Experimental
  fun startTransaction() = enqueue {
    syncTransactionStarted = true
  }

  @ApiStatus.Experimental
  fun finishTransaction(showFullSyncQuickFix: Boolean) = enqueue {
    syncTransactionStarted = false
    finishImportImpl(showFullSyncQuickFix)
  }

  companion object {
    val EXIT_CODE_OK = 0
    val EXIT_CODE_SIGTERM = 143
    private val LINE_SEPARATOR: String = System.lineSeparator()
    private val LEVEL_TO_PREFIX = mapOf(
      MavenServerConsoleIndicator.LEVEL_DEBUG to "DEBUG",
      MavenServerConsoleIndicator.LEVEL_INFO to "INFO",
      MavenServerConsoleIndicator.LEVEL_WARN to "WARNING",
      MavenServerConsoleIndicator.LEVEL_ERROR to "ERROR",
      MavenServerConsoleIndicator.LEVEL_FATAL to "FATAL_ERROR"
    )

    private enum class OutputType {
      NORMAL, ERROR
    }

    private fun debugLog(s: String, exception: Throwable? = null) {
      MavenLog.LOG.debug(s, exception)
    }
  }

  override fun handleDownloadEvents(downloadEvents: List<MavenArtifactEvent>) = enqueue {
    for (e in downloadEvents) {
      val key = getKeyPrefix(e.resolveType)
      val id = e.dependencyId
      when (e.artifactEventType) {
        ArtifactEventType.DOWNLOAD_STARTED -> downloadEventStarted(key, id)
        ArtifactEventType.DOWNLOAD_COMPLETED -> downloadEventCompleted(key, id)
        ArtifactEventType.DOWNLOAD_FAILED -> downloadEventFailed(key, id, e.errorMessage, e.stackTrace)
      }
    }
  }

  override fun handleConsoleEvents(consoleEvents: List<MavenServerConsoleEvent>) = enqueue {
    for (e in consoleEvents) {
      printMessage(e.level, e.message, e.throwable)
    }
  }

  private fun printMessage(level: Int, string: String, throwable: Throwable?) {
    if (isSuppressed(level)) return

    var type = OutputType.NORMAL
    if (throwable != null || level == MavenServerConsoleIndicator.LEVEL_WARN || level == MavenServerConsoleIndicator.LEVEL_ERROR || level == MavenServerConsoleIndicator.LEVEL_FATAL) {
      type = OutputType.ERROR
    }

    doPrint(composeLine(level, string), type)

    if (throwable != null) {
      val throwableText = ExceptionUtil.getThrowableText(throwable)
      if (Registry.`is`("maven.print.import.stacktraces") || ApplicationManager.getApplication().isUnitTestMode) { //NO-UT-FIX
        doPrint(LINE_SEPARATOR + composeLine(MavenServerConsoleIndicator.LEVEL_ERROR, throwableText), type)
      }
      else {
        doPrint(LINE_SEPARATOR + composeLine(MavenServerConsoleIndicator.LEVEL_ERROR, throwable.message), type)
      }
    }
  }

  private fun doPrint(@NlsSafe text: String, type: OutputType) {
    printText(mySyncId, text, type == OutputType.NORMAL)
  }

  private fun isSuppressed(level: Int): Boolean {
    return level < MavenProjectsManager.getInstance(myProject).generalSettings.outputLevel.level
  }

  private fun composeLine(level: Int, message: String?): String {
    return MessageFormat.format("[{0}] {1}", getPrefixByLevel(level), message)
  }

  private fun getPrefixByLevel(level: Int): String? {
    return LEVEL_TO_PREFIX[level]
  }
}
