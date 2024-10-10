// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.buildtool

import com.intellij.build.BuildProgressListener
import com.intellij.build.DefaultBuildDescriptor
import com.intellij.build.FilePosition
import com.intellij.build.SyncViewManager
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.EventResult
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.MessageEventResult
import com.intellij.build.events.impl.*
import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.issue.BuildIssueException
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.util.ExceptionUtil
import com.intellij.util.ThreeState
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.idea.maven.buildtool.quickfix.MavenFullSyncQuickFix
import org.jetbrains.idea.maven.buildtool.quickfix.OffMavenOfflineModeQuickFix
import org.jetbrains.idea.maven.buildtool.quickfix.OpenMavenSettingsQuickFix
import org.jetbrains.idea.maven.execution.SyncBundle
import org.jetbrains.idea.maven.externalSystemIntegration.output.importproject.quickfixes.DownloadArtifactBuildIssue
import org.jetbrains.idea.maven.model.MavenProjectProblem
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

class MavenSyncConsole(private val myProject: Project) : MavenEventHandler {
  private val mySyncView: BuildProgressListener = myProject.getService(SyncViewManager::class.java)
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

  @Synchronized
  fun startImport(explicit: Boolean) {
    if (started) {
      return
    }
    val restartAction: AnAction = object : AnAction(SyncBundle.message("maven.sync.title")) {
      override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = !started || finished
        e.presentation.icon = AllIcons.Actions.Refresh
      }

      override fun actionPerformed(e: AnActionEvent) {
        FileDocumentManager.getInstance().saveAllDocuments()
        e.project?.let {
          MavenLog.LOG.info("${this.javaClass.simpleName} forceUpdateAllProjectsOrFindAllAvailablePomFiles")
          MavenProjectsManager.getInstance(it).forceUpdateAllProjectsOrFindAllAvailablePomFiles()
        }
      }

      override fun getActionUpdateThread() = ActionUpdateThread.BGT
    }
    started = true
    finished = false
    hasErrors = false
    hasUnresolved = false
    shownIssues.clear()
    mySyncId = createTaskId()

    val descriptor = DefaultBuildDescriptor(mySyncId, SyncBundle.message("maven.sync.title"), myProject.basePath!!,
                                            System.currentTimeMillis())
      .withRestartAction(restartAction)
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


  @Synchronized
  fun addText(@Nls text: String, stdout: Boolean) = doIfImportInProcess {
    addText(mySyncId, text, true)
  }

  @Synchronized
  fun addWrapperProgressText(@Nls text: String) = doIfImportInProcess {
    addText(SyncBundle.message("maven.sync.wrapper"), text, true)
  }

  @Synchronized
  private fun addText(parentId: Any, @Nls text: String, stdout: Boolean) = doIfImportInProcess {
    if (StringUtil.isEmpty(text)) {
      return
    }
    val toPrint = if (text.endsWith('\n')) text else "$text\n"
    mySyncView.onEvent(mySyncId, OutputBuildEventImpl(parentId, toPrint, stdout))
  }

  @Synchronized
  fun addBuildEvent(buildEvent: BuildEvent) = doIfImportInProcess {
    mySyncView.onEvent(mySyncId, buildEvent)
  }


  @Synchronized
  fun addWarning(@Nls text: String, @Nls description: String) = addWarning(text, description, null)

  fun addBuildIssue(issue: BuildIssue, kind: MessageEvent.Kind) = doIfImportInProcessOrPostpone {
    if (!newIssue(issue.title + issue.description)) return@doIfImportInProcessOrPostpone
    mySyncView.onEvent(mySyncId, BuildIssueEventImpl(mySyncId, issue, kind))
    hasErrors = hasErrors || kind == MessageEvent.Kind.ERROR
  }

  @Synchronized
  fun addWarning(@Nls text: String, @Nls description: String, filePosition: FilePosition?) = doIfImportInProcess {
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

  @Synchronized
  fun finishImport(showFullSyncQuickFix: Boolean = false) {
    debugLog("Maven sync: finishImport")
    doFinish(showFullSyncQuickFix)
  }

  fun terminated(exitCode: Int) = doIfImportInProcess {
    if (EXIT_CODE_OK == exitCode || EXIT_CODE_SIGTERM == exitCode) doFinish() else doTerminate(exitCode)
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

  @Synchronized
  fun startWrapperResolving() {
    if (!started || finished) {
      startImport(true)
    }
    startTask(mySyncId, SyncBundle.message("maven.sync.wrapper"))
  }

  @Synchronized
  fun finishWrapperResolving(e: Throwable? = null) {
    if (e != null) {
      addBuildIssue(object : BuildIssue {
        override val title: String = SyncBundle.message("maven.sync.wrapper.failure")
        override val description: String = SyncBundle.message("maven.sync.wrapper.failure.description",
                                                              e.localizedMessage, OpenMavenSettingsQuickFix.ID)
        override val quickFixes: List<BuildIssueQuickFix> = listOf(OpenMavenSettingsQuickFix())
        override fun getNavigatable(project: Project): Navigatable? = null
      }, MessageEvent.Kind.WARNING)
    }
    completeTask(mySyncId, SyncBundle.message("maven.sync.wrapper"), SuccessResultImpl())
  }

  @Synchronized
  fun notifyReadingProblems(file: VirtualFile) = doIfImportInProcess {
    debugLog("reading problems in $file")
    hasErrors = true
    val desc = SyncBundle.message("maven.sync.failure.error.reading.file", file.path)
    mySyncView.onEvent(mySyncId,
                       FileMessageEventImpl(mySyncId, MessageEvent.Kind.ERROR, SyncBundle.message("maven.sync.group.error"), desc, desc,
                                            FilePosition(File(file.path), -1, -1)))
  }

  @Synchronized
  fun showProblem(problem: MavenProjectProblem) = doIfImportInProcess {
    hasErrors = true
    val group = SyncBundle.message("maven.sync.group.error")
    val position = problem.getFilePosition()
    val message = problem.description ?: SyncBundle.message("maven.sync.failure.error.undefined.message")
    val detailedMessage = problem.description ?: SyncBundle.message("maven.sync.failure.error.undefined.detailed.message", problem.path)
    val eventImpl = FileMessageEventImpl(mySyncId, MessageEvent.Kind.ERROR, group, message, detailedMessage, position)
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

  @Synchronized
  @ApiStatus.ScheduledForRemoval
  @ApiStatus.Internal
  @Deprecated("use {@link #addException(Throwable)}", ReplaceWith("addException(e)"))
  fun addException(e: Throwable, ignoredProgressListener: BuildProgressListener) {
    addException(e)
  }

  @Synchronized
  @ApiStatus.Internal
  fun addException(e: Throwable) {
    if (started && !finished) {
      MavenLog.LOG.warn(e)
      hasErrors = true
      val buildIssueException = ExceptionUtil.findCause(e, BuildIssueException::class.java)
      if (buildIssueException != null) {
        addBuildIssue(buildIssueException.buildIssue, MessageEvent.Kind.ERROR)
      }
      else {
        mySyncView.onEvent(mySyncId, createMessageEvent(myProject, mySyncId, e))
      }

    }
    else {
      this.startImport(true)
      this.addException(e)
      this.finishImport()
    }
  }

  private fun getKeyPrefix(type: MavenServerConsoleIndicator.ResolveType): String {
    return when (type) {
      MavenServerConsoleIndicator.ResolveType.PLUGIN -> "maven.sync.plugins"
      MavenServerConsoleIndicator.ResolveType.DEPENDENCY -> "maven.sync.dependencies"
    }
  }

  @Synchronized
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
        override val title: String = "Incremental Sync Finished"
        override val description: String = "Incremental sync finished. If there is something wrong with the project model, <a href=\"${MavenFullSyncQuickFix.ID}\">run full sync</a>\n"
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

  @Synchronized
  private fun showArtifactBuildIssue(keyPrefix: String, dependency: String, errorMessage: String?) = doIfImportInProcess {
    hasErrors = true
    hasUnresolved = true
    val umbrellaString = SyncBundle.message("${keyPrefix}.resolve")
    val errorString = SyncBundle.message("${keyPrefix}.resolve.error", dependency)
    startTask(mySyncId, umbrellaString)
    val buildIssue = DownloadArtifactBuildIssue.getIssue(errorString, errorMessage ?: errorString)
    mySyncView.onEvent(mySyncId, BuildIssueEventImpl(umbrellaString, buildIssue, MessageEvent.Kind.ERROR))
    addText(mySyncId, errorString, false)
  }

  fun showArtifactBuildIssue(type: MavenServerConsoleIndicator.ResolveType, dependency: String, errorMessage: String?) {
    showArtifactBuildIssue(getKeyPrefix(type), dependency, errorMessage)
  }

  @Synchronized
  fun showBuildIssue(buildIssue: BuildIssue) = doIfImportInProcess {
    hasErrors = true
    hasUnresolved = true
    val key = getKeyPrefix(MavenServerConsoleIndicator.ResolveType.DEPENDENCY)
    startTask(mySyncId, key)
    mySyncView.onEvent(mySyncId, BuildIssueEventImpl(key, buildIssue, MessageEvent.Kind.ERROR))
  }

  @Synchronized
  fun showBuildIssue(buildIssue: BuildIssue, kind: MessageEvent.Kind) = doIfImportInProcess {
    hasErrors =  hasErrors || kind == MessageEvent.Kind.ERROR
    val key = getKeyPrefix(MavenServerConsoleIndicator.ResolveType.DEPENDENCY)
    startTask(mySyncId, key)
    mySyncView.onEvent(mySyncId, BuildIssueEventImpl(key, buildIssue, kind))
  }

  @Synchronized
  private fun startTask(parentId: Any, @NlsSafe taskName: String) = doIfImportInProcess {
    debugLog("Maven sync: start $taskName")
    if (myStartedSet.add(parentId to taskName)) {
      mySyncView.onEvent(mySyncId, StartEventImpl(taskName, parentId, System.currentTimeMillis(), taskName))
    }
  }


  @Synchronized
  private fun completeTask(parentId: Any, @NlsSafe taskName: String, result: EventResult) = doIfImportInProcess {
    hasErrors = hasErrors || result is FailureResultImpl

    debugLog("Maven sync: complete $taskName with $result")
    if (myStartedSet.remove(parentId to taskName)) {
      mySyncView.onEvent(mySyncId, FinishEventImpl(taskName, parentId, System.currentTimeMillis(), taskName, result))
    }
  }

  fun finishPluginResolution() {
    completeUmbrellaEvents(getKeyPrefix(MavenServerConsoleIndicator.ResolveType.PLUGIN))
  }

  fun finishArtifactsDownload() {
    completeUmbrellaEvents(getKeyPrefix(MavenServerConsoleIndicator.ResolveType.DEPENDENCY))
  }

  @Synchronized
  private fun completeUmbrellaEvents(keyPrefix: String) = doIfImportInProcess {
    val taskName = SyncBundle.message("${keyPrefix}.resolve")
    completeTask(mySyncId, taskName, DerivedResultImpl())
  }

  @Synchronized
  private fun downloadEventStarted(keyPrefix: String, dependency: String) = doIfImportInProcess {
    val downloadString = SyncBundle.message("${keyPrefix}.download")
    val downloadArtifactString = SyncBundle.message("${keyPrefix}.artifact.download", dependency)
    startTask(mySyncId, downloadString)
    startTask(downloadString, downloadArtifactString)
  }

  @Synchronized
  private fun downloadEventCompleted(keyPrefix: String, dependency: String) = doIfImportInProcess {
    val downloadString = SyncBundle.message("${keyPrefix}.download")
    val downloadArtifactString = SyncBundle.message("${keyPrefix}.artifact.download", dependency)
    addText(downloadArtifactString, downloadArtifactString, true)
    completeTask(downloadString, downloadArtifactString, SuccessResultImpl(false))
  }


  @Synchronized
  private fun downloadEventFailed(keyPrefix: String,
                                  @NlsSafe dependency: String,
                                  @NlsSafe error: String,
                                  @NlsSafe stackTrace: String?) = doIfImportInProcess {
    val downloadString = SyncBundle.message("${keyPrefix}.download")

    val downloadArtifactString = SyncBundle.message("${keyPrefix}.artifact.download", dependency)
    if (isJavadocOrSource(dependency)) {
      addText(downloadArtifactString, SyncBundle.message("maven.sync.failure.dependency.not.found", dependency), true)
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
        addText(downloadArtifactString, stackTrace, false)
      }
      else {
        addText(downloadArtifactString, error, true)
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
  @Synchronized
  fun startTransaction() {
    syncTransactionStarted = true
  }

  @ApiStatus.Experimental
  @Synchronized
  fun finishTransaction(showFullSyncQuickFix: Boolean) {
    syncTransactionStarted = false
    finishImport(showFullSyncQuickFix)
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

  @Synchronized
  override fun handleDownloadEvents(downloadEvents: List<MavenArtifactEvent>) {
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

  override fun handleConsoleEvents(consoleEvents: List<MavenServerConsoleEvent>) {
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
    addText(text, type == OutputType.NORMAL)
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