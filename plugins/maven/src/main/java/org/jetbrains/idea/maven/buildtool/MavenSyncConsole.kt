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
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import org.jetbrains.idea.maven.buildtool.quickfix.OpenMavenSettingsQuickFix
import org.jetbrains.idea.maven.buildtool.quickfix.UseBundledMavenQuickFix
import org.jetbrains.idea.maven.execution.SyncBundle
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

  private val JAVADOC_AND_SOURCE_CLASSIFIERS = setOf("javadoc", "sources", "test-javadoc", "test-sources")

  private var myStartedSet = LinkedHashSet<Pair<Any, String>>()

  @Synchronized
  fun startImport(syncView: BuildProgressListener, fromAutoImport: Boolean) {
    if (started) {
      return
    }
    started = true
    finished = false
    hasErrors = false
    mySyncId = ExternalSystemTaskId.create(MavenUtil.SYSTEM_ID, ExternalSystemTaskType.RESOLVE_PROJECT, myProject)
    val descriptor = DefaultBuildDescriptor(mySyncId, "Sync", myProject.basePath!!, System.currentTimeMillis())
    mySyncView = syncView
    val runDescr = BuildContentDescriptor(null, null, object : JComponent() {}, "Sync")
    runDescr.isActivateToolWindowWhenFailed = !fromAutoImport
    runDescr.isActivateToolWindowWhenAdded = !fromAutoImport
    mySyncView.onEvent(mySyncId,
                       StartBuildEventImpl(descriptor, "Sync ${myProject.name}")
                         .withContentDescriptorSupplier {
                           runDescr
                         })
    debugLog("maven sync: started importing $myProject")
  }

  @Synchronized
  fun addText(text: String) = doIfImportInProcess {
    addText(mySyncId, text, true)
  }

  @Synchronized
  fun addText(parentId: Any, text: String, stdout: Boolean) = doIfImportInProcess {
    if (StringUtil.isEmpty(text)) {
      return
    }
    val toPrint = if (text.endsWith('\n')) text else "$text\n"
    mySyncView.onEvent(mySyncId, OutputBuildEventImpl(parentId, toPrint, stdout))
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
    tasks.forEach { completeTask(it.first, it.second, FailureResultImpl("Terminated with exit code = $exitCode")) }

    mySyncView.onEvent(mySyncId, FinishBuildEventImpl(mySyncId, null, System.currentTimeMillis(), "",
                                                      FailureResultImpl("Terminated with exit code = $exitCode")))
    finished = true
    started = false

  }

  @Synchronized
  fun notifyReadingProblems(file: VirtualFile) = doIfImportInProcess {
    debugLog("reading problems in $file")
    hasErrors = true
    mySyncView.onEvent(mySyncId, FileMessageEventImpl(mySyncId, MessageEvent.Kind.ERROR, "Error", "Error reading ${file.path}",
                                                      "Error reading ${file.path}",
                                                      FilePosition(File(file.path), -1, -1)))
  }

  fun getListener(type: MavenServerProgressIndicator.ResolveType): ArtifactSyncListener {
    return when (type) {
      MavenServerProgressIndicator.ResolveType.PLUGIN ->ArtifactSyncListenerImpl("maven.sync.plugins")
      MavenServerProgressIndicator.ResolveType.DEPENDENCY ->ArtifactSyncListenerImpl("maven.sync.dependencies")
    }
  }

  @Synchronized
  private fun doFinish() {
    val tasks = myStartedSet.toList().asReversed()
    debugLog("Tasks $tasks are not completed! Force complete")
    tasks.forEach { completeTask(it.first, it.second, DerivedResultImpl()) }
    mySyncView.onEvent(mySyncId, FinishBuildEventImpl(mySyncId, null, System.currentTimeMillis(), "",
                                                      if (hasErrors) FailureResultImpl() else DerivedResultImpl()))
    finished = true
    started = false
  }

  @Synchronized
  private fun showError(keyPrefix: String, dependency: String) = doIfImportInProcess {
    hasErrors = true
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
    hasErrors =  hasErrors || result is FailureResultImpl

    debugLog("Maven sync: complete $taskName with $result")
    if (myStartedSet.remove(parentId to taskName)) {
      mySyncView.onEvent(mySyncId, FinishEventImpl(taskName, parentId, System.currentTimeMillis(), taskName, result))
    }
  }


  private fun debugLog(s: String, exception: Throwable? = null) {
    MavenLog.LOG.debug(s, exception)
  }

  @Synchronized
  private fun completeUmbrellaEvents(keyPrefix: String) = doIfImportInProcess{
    val taskName = SyncBundle.message("${keyPrefix}.resolve")
    completeTask(mySyncId, taskName, DerivedResultImpl())
  }

  @Synchronized
  private fun downloadEventStarted(keyPrefix: String, dependency: String) = doIfImportInProcess{
    val downloadString = SyncBundle.message("${keyPrefix}.download")
    val downloadArtifactString = SyncBundle.message("${keyPrefix}.artifact.download", dependency)
    startTask(mySyncId, downloadString)
    startTask(downloadString, downloadArtifactString)
  }

  @Synchronized
  private fun downloadEventCompleted(keyPrefix: String, dependency: String) = doIfImportInProcess{
    val downloadString = SyncBundle.message("${keyPrefix}.download")
    val downloadArtifactString = SyncBundle.message("${keyPrefix}.artifact.download", dependency)
    addText(downloadArtifactString, downloadArtifactString, true)
    completeTask(downloadString, downloadArtifactString, SuccessResultImpl(false))
  }


  @Synchronized
  private fun downloadEventFailed(keyPrefix: String, dependency: String, error: String, stackTrace: String?) = doIfImportInProcess{
    val downloadString = SyncBundle.message("${keyPrefix}.download")

    val downloadArtifactString = SyncBundle.message("${keyPrefix}.artifact.download", dependency)
    if (isJavadocOrSource(dependency)) {
      addText(downloadArtifactString, "$dependency not found", true)
      completeTask(downloadString, downloadArtifactString, object : MessageEventResult {
        override fun getKind(): MessageEvent.Kind {
          return MessageEvent.Kind.WARNING
        }

        override fun getDetails(): String? {
          return "$dependency not found"
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
      override val title = "Maven version issue"
      override val description: String = "${message}\n" +
                                         "- <a href=\"${OpenMavenSettingsQuickFix.ID}\">Open Settings</a>\n" +
                                         "- <a href=\"${UseBundledMavenQuickFix.ID}\">Use Bundled (${bundledVersion})</a>\n"

      override val quickFixes: List<BuildIssueQuickFix> = listOf(OpenMavenSettingsQuickFix(), UseBundledMavenQuickFix())
      override fun getNavigatable(project: Project): Navigatable? = null
    }, kind))
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




