// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.execution.ExecutionException
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.util.ExceptionUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.idea.maven.buildtool.quickfix.OffMavenOfflineModeQuickFix
import org.jetbrains.idea.maven.buildtool.quickfix.OpenMavenSettingsQuickFix
import org.jetbrains.idea.maven.buildtool.quickfix.UseBundledMavenQuickFix
import org.jetbrains.idea.maven.execution.SyncBundle
import org.jetbrains.idea.maven.externalSystemIntegration.output.importproject.quickfixes.DownloadArtifactBuildIssue
import org.jetbrains.idea.maven.model.MavenProjectProblem
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.project.MavenResolveResultProcessor
import org.jetbrains.idea.maven.project.MavenWorkspaceSettingsComponent
import org.jetbrains.idea.maven.server.CannotStartServerException
import org.jetbrains.idea.maven.server.MavenServerManager
import org.jetbrains.idea.maven.server.MavenServerProgressIndicator
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenUtil
import java.io.File

class MavenSyncConsole(private val myProject: Project) {
  @Volatile
  private var mySyncView: BuildProgressListener = BuildProgressListener { _, _ -> }
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
  fun startImport(syncView: BuildProgressListener) {
    if (started) {
      return
    }
    val restartAction: AnAction = object : AnAction() {
      override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = !started || finished
        e.presentation.icon = AllIcons.Actions.Refresh
      }

      override fun actionPerformed(e: AnActionEvent) {
        e.project?.let {
          MavenProjectsManager.getInstance(it).forceUpdateAllProjectsOrFindAllAvailablePomFiles()
        }
      }
    }
    started = true
    finished = false
    hasErrors = false
    hasUnresolved = false
    mySyncView = syncView
    shownIssues.clear()
    mySyncId = createTaskId()

    val descriptor = DefaultBuildDescriptor(mySyncId, SyncBundle.message("maven.sync.title"), myProject.basePath!!,
                                            System.currentTimeMillis())
      .withRestartAction(restartAction)
    descriptor.isActivateToolWindowWhenFailed = true
    descriptor.isActivateToolWindowWhenAdded = false

    mySyncView.onEvent(mySyncId, StartBuildEventImpl(descriptor, SyncBundle.message("maven.sync.project.title", myProject.name)))
    debugLog("maven sync: started importing $myProject")

    myPostponed.forEach(this::doIfImportInProcess)
    myPostponed.clear();
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
    if (!newIssue(issue.title + issue.description)) return@doIfImportInProcessOrPostpone;
    mySyncView.onEvent(mySyncId, BuildIssueEventImpl(mySyncId, issue, kind))
    hasErrors = hasErrors || kind == MessageEvent.Kind.ERROR;
  }

  @Synchronized
  fun addWarning(@Nls text: String, @Nls description: String, filePosition: FilePosition?) = doIfImportInProcess {
    if (!newIssue(text + description + filePosition)) return;
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
  fun finishImport() {
    debugLog("Maven sync: finishImport")
    doFinish()
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
      startImport(myProject.getService(SyncViewManager::class.java))
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
    val (line, columns) = getPosition() ?: (-1 to -1)
    return FilePosition(File(path), line, columns)
  }

  private fun MavenProjectProblem.getPosition(): Pair<Int, Int>? {
    val description = description ?: return null
    if (type == MavenProjectProblem.ProblemType.STRUCTURE) {
      val pattern = Regex("@(\\d+):(\\d+)")
      val matchResults = pattern.findAll(description)
      val matchResult = matchResults.lastOrNull() ?: return null
      val (_, line, offset) = matchResult.groupValues
      return line.toInt() - 1 to offset.toInt()
    }
    return null
  }

  @Synchronized
  @ApiStatus.Internal
  fun addException(e: Throwable, progressListener: BuildProgressListener) {
    if (started && !finished) {
      MavenLog.LOG.warn(e)
      hasErrors = true
      @Suppress("HardCodedStringLiteral")
      mySyncView.onEvent(mySyncId, createMessageEvent(e))
    }
    else {
      this.startImport(progressListener)
      this.addException(e, progressListener)
      this.finishImport()
    }
  }

  private fun createMessageEvent(e: Throwable): MessageEventImpl {
    val csse = ExceptionUtil.findCause(e, CannotStartServerException::class.java)
    if (csse != null) {
      val cause = ExceptionUtil.findCause(csse, ExecutionException::class.java)
      if (cause != null) {
        return MessageEventImpl(mySyncId, MessageEvent.Kind.ERROR, SyncBundle.message("build.event.title.internal.server.error"),
                                getExceptionText(cause), getExceptionText(cause))
      }
      else {
        return MessageEventImpl(mySyncId, MessageEvent.Kind.ERROR, SyncBundle.message("build.event.title.internal.server.error"),
                                getExceptionText(csse), getExceptionText(csse))
      }
    }
    return MessageEventImpl(mySyncId, MessageEvent.Kind.ERROR, SyncBundle.message("build.event.title.error"),
                            getExceptionText(e), getExceptionText(e))
  }


  private fun getExceptionText(e: Throwable): @NlsSafe String {
    if (MavenWorkspaceSettingsComponent.getInstance(myProject).settings.getGeneralSettings().isPrintErrorStackTraces) {
      return ExceptionUtil.getThrowableText(e)
    }

    if(!e.localizedMessage.isNullOrEmpty()) return e.localizedMessage
    return if (StringUtil.isEmpty(e.message)) SyncBundle.message("build.event.title.error") else e.message!!
  }

  fun getListener(type: MavenServerProgressIndicator.ResolveType): ArtifactSyncListener {
    return when (type) {
      MavenServerProgressIndicator.ResolveType.PLUGIN -> ArtifactSyncListenerImpl("maven.sync.plugins")
      MavenServerProgressIndicator.ResolveType.DEPENDENCY -> ArtifactSyncListenerImpl("maven.sync.dependencies")
    }
  }

  @Synchronized
  private fun doFinish() {
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
    finished = true
    started = false
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
    } catch (ignore: Exception){}

  }

  @Synchronized
  private fun showError(keyPrefix: String, dependency: String) = doIfImportInProcess {
    hasErrors = true
    hasUnresolved = true
    val umbrellaString = SyncBundle.message("${keyPrefix}.resolve")
    val errorString = SyncBundle.message("${keyPrefix}.resolve.error", dependency)
    startTask(mySyncId, umbrellaString)
    mySyncView.onEvent(mySyncId, MessageEventImpl(umbrellaString, MessageEvent.Kind.ERROR,
                                                  SyncBundle.message("maven.sync.group.error"), errorString, errorString))
    addText(mySyncId, errorString, false)
  }

  @Synchronized
  private fun showBuildIssue(keyPrefix: String, dependency: String, quickFix: BuildIssueQuickFix) = doIfImportInProcess {
    hasErrors = true
    hasUnresolved = true
    val umbrellaString = SyncBundle.message("${keyPrefix}.resolve")
    val errorString = SyncBundle.message("${keyPrefix}.resolve.error", dependency)
    startTask(mySyncId, umbrellaString)
    val buildIssue = DownloadArtifactBuildIssue.getIssue(errorString, quickFix)
    mySyncView.onEvent(mySyncId, BuildIssueEventImpl(umbrellaString, buildIssue, MessageEvent.Kind.ERROR))
    addText(mySyncId, errorString, false)
  }

  @Synchronized
  private fun showBuildIssueNode(key: String, buildIssue: BuildIssue) = doIfImportInProcess {
    hasErrors = true
    hasUnresolved = true
    startTask(mySyncId, key)
    mySyncView.onEvent(mySyncId, BuildIssueEventImpl(key, buildIssue, MessageEvent.Kind.ERROR))
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

  @Synchronized
  fun showQuickFixBadMaven(message: String, kind: MessageEvent.Kind) {
    val bundledVersion = MavenServerManager.getMavenVersion(MavenServerManager.BUNDLED_MAVEN_3)
    mySyncView.onEvent(mySyncId, BuildIssueEventImpl(mySyncId, object : BuildIssue {
      override val title = SyncBundle.message("maven.sync.version.issue.title")
      override val description: String = "${message}\n" +
                                         "- <a href=\"${OpenMavenSettingsQuickFix.ID}\">" +
                                         SyncBundle.message("maven.sync.version.open.settings") + "</a>\n" +
                                         "- <a href=\"${UseBundledMavenQuickFix.ID}\">" +
                                         SyncBundle.message("maven.sync.version.use.bundled", bundledVersion) + "</a>\n"

      override val quickFixes: List<BuildIssueQuickFix> = listOf(OpenMavenSettingsQuickFix(), UseBundledMavenQuickFix())
      override fun getNavigatable(project: Project): Navigatable? = null
    }, kind))
  }

  fun <Result> runTask(@NlsSafe taskName: String, task: () -> Result): Result {
    startTask(mySyncId, taskName)
    try {
      return task().also {
        completeTask(mySyncId, taskName, SuccessResultImpl())
      }
    }
    catch (e: Exception) {
      completeTask(mySyncId, taskName, FailureResultImpl(e))
      throw e
    }
  }

  @Synchronized
  fun showQuickFixJDK(version: String) {
    mySyncView.onEvent(mySyncId, BuildIssueEventImpl(mySyncId, object : BuildIssue {
      override val title = SyncBundle.message("maven.sync.quickfixes.maven.jdk.version.title")
      override val description: String = SyncBundle.message("maven.sync.quickfixes.upgrade.to.jdk7", version) + "\n" +
                                         "- <a href=\"${OpenMavenSettingsQuickFix.ID}\">" +
                                         SyncBundle.message("maven.sync.quickfixes.open.settings") +
                                         "</a>\n"
      override val quickFixes: List<BuildIssueQuickFix> = listOf(OpenMavenSettingsQuickFix())
      override fun getNavigatable(project: Project): Navigatable? = null
    }, MessageEvent.Kind.ERROR))
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

  private inner class ArtifactSyncListenerImpl(val keyPrefix: String) : ArtifactSyncListener {
    override fun downloadStarted(dependency: String) {
      downloadEventStarted(keyPrefix, dependency)
    }

    override fun downloadCompleted(dependency: String) {
      downloadEventCompleted(keyPrefix, dependency)
    }

    override fun downloadFailed(dependency: String, error: String, stackTrace: String?) {
      downloadEventFailed(keyPrefix, dependency, error, stackTrace)
    }

    override fun finish() {
      completeUmbrellaEvents(keyPrefix)
    }

    override fun showError(dependency: String) {
      showError(keyPrefix, dependency)
    }

    override fun showBuildIssue(dependency: String, quickFix: BuildIssueQuickFix) {
      showBuildIssue(keyPrefix, dependency, quickFix)
    }

    override fun showBuildIssue(dependency: String, buildIssue: BuildIssue) {
      showBuildIssueNode(keyPrefix, buildIssue)
    }
  }

  companion object {
    val EXIT_CODE_OK = 0
    val EXIT_CODE_SIGTERM = 143

    @ApiStatus.Experimental
    @JvmStatic
    fun startTransaction(project: Project) {
      debugLog("Maven sync: start sync transaction")
      val syncConsole = MavenProjectsManager.getInstance(project).syncConsole
      synchronized(syncConsole) {
        syncConsole.syncTransactionStarted = true
      }
    }

    @ApiStatus.Experimental
    @JvmStatic
    fun finishTransaction(project: Project) {
      debugLog("Maven sync: finish sync transaction")
      MavenResolveResultProcessor.notifyMavenProblems(project)
      val syncConsole = MavenProjectsManager.getInstance(project).syncConsole
      synchronized(syncConsole) {
        syncConsole.syncTransactionStarted = false
        syncConsole.finishImport()
      }
    }

    private fun debugLog(s: String, exception: Throwable? = null) {
      MavenLog.LOG.debug(s, exception)
    }
  }
}

interface ArtifactSyncListener {
  fun showError(dependency: String)
  fun showBuildIssue(dependency: String, quickFix: BuildIssueQuickFix)
  fun showBuildIssue(dependency: String, buildIssue: BuildIssue)
  fun downloadStarted(dependency: String)
  fun downloadCompleted(dependency: String)
  fun downloadFailed(dependency: String, error: String, stackTrace: String?)
  fun finish()
}




