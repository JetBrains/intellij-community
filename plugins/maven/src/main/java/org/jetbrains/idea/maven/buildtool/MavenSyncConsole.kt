// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.buildtool

import com.intellij.build.BuildContentDescriptor
import com.intellij.build.BuildProgressListener
import com.intellij.build.DefaultBuildDescriptor
import com.intellij.build.FilePosition
import com.intellij.build.events.EventResult
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.MessageEventResult
import com.intellij.build.events.impl.*
import com.intellij.build.issue.BuildIssue
import com.intellij.build.issue.BuildIssueQuickFix
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.project.Project
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
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.project.MavenWorkspaceSettingsComponent
import org.jetbrains.idea.maven.server.MavenServerManager
import org.jetbrains.idea.maven.server.MavenServerProgressIndicator
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenUtil
import java.io.File
import javax.swing.JComponent

class MavenSyncConsole(private val myProject: Project) {
  @Volatile
  private var mySyncView: BuildProgressListener = BuildProgressListener { _, _ -> }
  private var mySyncId = ExternalSystemTaskId.create(MavenUtil.SYSTEM_ID, ExternalSystemTaskType.RESOLVE_PROJECT, myProject)
  private var finished = false
  private var started = false
  private var hasErrors = false
  private var hasUnresolved = false
  private val JAVADOC_AND_SOURCE_CLASSIFIERS = setOf("javadoc", "sources", "test-javadoc", "test-sources")

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
    mySyncId = ExternalSystemTaskId.create(MavenUtil.SYSTEM_ID, ExternalSystemTaskType.RESOLVE_PROJECT, myProject)
    val descriptor = DefaultBuildDescriptor(mySyncId, SyncBundle.message("maven.sync.title"), myProject.basePath!!,
                                            System.currentTimeMillis())
    mySyncView = syncView
    val runDescr = BuildContentDescriptor(null, null, object : JComponent() {}, SyncBundle.message("maven.sync.title"))
    runDescr.isActivateToolWindowWhenFailed = true
    runDescr.isActivateToolWindowWhenAdded = false
    mySyncView.onEvent(mySyncId,
                       StartBuildEventImpl(descriptor, SyncBundle.message("maven.sync.project.title", myProject.name))
                         .withContentDescriptorSupplier
                         {
                           runDescr
                         }.withRestartAction(restartAction))
    debugLog("maven sync: started importing $myProject")
  }

  @Synchronized
  fun addText(text: String) = doIfImportInProcess {
    addText(mySyncId, text, true)
  }

  @Synchronized
  private fun addText(parentId: Any, text: String, stdout: Boolean) = doIfImportInProcess {
    if (StringUtil.isEmpty(text)) {
      return
    }
    val toPrint = if (text.endsWith('\n')) text else "$text\n"
    mySyncView.onEvent(mySyncId, OutputBuildEventImpl(parentId, toPrint, stdout))
  }

  @Synchronized
  fun addWarning(@Nls text: String, @Nls description: String) = doIfImportInProcess {
    mySyncView.onEvent(mySyncId,
                       MessageEventImpl(mySyncId, MessageEvent.Kind.WARNING, SyncBundle.message("maven.sync.group.compiler"), text,
                                        description))
  }

  @Synchronized
  fun finishImport() {
    debugLog("Maven sync: finishImport")
    doFinish()
  }


  @Synchronized
  fun terminated(exitCode: Int) = doIfImportInProcess {
    val tasks = myStartedSet.toList().asReversed()
    debugLog("Tasks $tasks are not completed! Force complete")
    tasks.forEach { completeTask(it.first, it.second, FailureResultImpl(SyncBundle.message("maven.sync.failure.terminated", exitCode))) }

    mySyncView.onEvent(mySyncId, FinishBuildEventImpl(mySyncId, null, System.currentTimeMillis(), "",
                                                      FailureResultImpl(SyncBundle.message("maven.sync.failure.terminated", exitCode))))
    finished = true
    started = false

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
  @ApiStatus.Internal
  fun addException(e: Throwable, progressListener: BuildProgressListener) {
    if(started && !finished){
      MavenLog.LOG.warn(e)
      hasErrors = true
      mySyncView.onEvent(mySyncId,
                         MessageEventImpl(mySyncId, MessageEvent.Kind.ERROR, "Error", e.localizedMessage, ExceptionUtil.getThrowableText(e)));
    } else {
      this.startImport(progressListener);
      this.addException(e, progressListener)
      this.finishImport();
    }
  }

  fun getListener(type: MavenServerProgressIndicator.ResolveType): ArtifactSyncListener {
    return when (type) {
      MavenServerProgressIndicator.ResolveType.PLUGIN -> ArtifactSyncListenerImpl("maven.sync.plugins")
      MavenServerProgressIndicator.ResolveType.DEPENDENCY -> ArtifactSyncListenerImpl("maven.sync.dependencies")
    }
  }

  @Synchronized
  private fun doFinish() {
    val tasks = myStartedSet.toList().asReversed()
    debugLog("Tasks $tasks are not completed! Force complete")
    tasks.forEach { completeTask(it.first, it.second, DerivedResultImpl()) }
    mySyncView.onEvent(mySyncId, FinishBuildEventImpl(mySyncId, null, System.currentTimeMillis(), "",
                                                      if (hasErrors) FailureResultImpl() else DerivedResultImpl()))
    val generalSettings = MavenWorkspaceSettingsComponent.getInstance(myProject).settings.generalSettings
    if (hasUnresolved && generalSettings.isWorkOffline) {
      mySyncView.onEvent(mySyncId, BuildIssueEventImpl(mySyncId, object : BuildIssue{
        override val title: String = "Dependency Resolution Failed"
        override val description: String = "<a href=\"${OffMavenOfflineModeQuickFix.ID}\">Switch Off Offline Mode</a>\n"
        override val quickFixes: List<BuildIssueQuickFix> = listOf(OffMavenOfflineModeQuickFix())

        override fun getNavigatable(project: Project): Navigatable? = null
      }, MessageEvent.Kind.ERROR))
    }
    finished = true
    started = false
  }

  @Synchronized
  private fun showError(keyPrefix: String, dependency: String) = doIfImportInProcess {
    hasErrors = true
    hasUnresolved = true
    val umbrellaString = SyncBundle.message("${keyPrefix}.resolve")
    val errorString = SyncBundle.message("${keyPrefix}.resolve.error", dependency)
    startTask(mySyncId, umbrellaString)
    mySyncView.onEvent(mySyncId, MessageEventImpl(umbrellaString, MessageEvent.Kind.ERROR, "Error", errorString, errorString))
    addText(mySyncId, errorString, false)
  }

  @Synchronized
  private fun startTask(parentId: Any, taskName: String) = doIfImportInProcess {
    debugLog("Maven sync: start $taskName")
    if (myStartedSet.add(parentId to taskName)) {
      mySyncView.onEvent(mySyncId, StartEventImpl(taskName, parentId, System.currentTimeMillis(), taskName))
    }
  }


  @Synchronized
  private fun completeTask(parentId: Any, taskName: String, result: EventResult) = doIfImportInProcess {
    hasErrors = hasErrors || result is FailureResultImpl

    debugLog("Maven sync: complete $taskName with $result")
    if (myStartedSet.remove(parentId to taskName)) {
      mySyncView.onEvent(mySyncId, FinishEventImpl(taskName, parentId, System.currentTimeMillis(), taskName, result))
    }
  }


  private fun debugLog(s: String, exception: Throwable? = null) {
    MavenLog.LOG.debug(s, exception)
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
  private fun downloadEventFailed(keyPrefix: String, dependency: String, error: String, stackTrace: String?) = doIfImportInProcess {
    val downloadString = SyncBundle.message("${keyPrefix}.download")

    val downloadArtifactString = SyncBundle.message("${keyPrefix}.artifact.download", dependency)
    if (isJavadocOrSource(dependency)) {
      addText(downloadArtifactString, "$dependency not found", true)
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
    val bundledVersion = MavenServerManager.getInstance().getMavenVersion(MavenServerManager.BUNDLED_MAVEN_3)
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
  }

}

interface ArtifactSyncListener {
  fun showError(dependency: String)
  fun downloadStarted(dependency: String)
  fun downloadCompleted(dependency: String)
  fun downloadFailed(dependency: String, error: String, stackTrace: String?)
  fun finish()
}




