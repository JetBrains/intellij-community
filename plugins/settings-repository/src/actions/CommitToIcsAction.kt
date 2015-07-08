package org.jetbrains.settingsRepository.actions

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.StorageScheme
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectEx
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.actions.CommonCheckinFilesAction
import com.intellij.openapi.vcs.actions.VcsContext
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.openapi.vcs.checkin.BeforeCheckinDialogHandler
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.SmartList
import org.jetbrains.settingsRepository.CommitToIcsDialog
import org.jetbrains.settingsRepository.IcsBundle
import org.jetbrains.settingsRepository.ProjectId
import org.jetbrains.settingsRepository.icsManager
import java.util.UUID

class CommitToIcsAction : CommonCheckinFilesAction() {
  class IcsBeforeCommitDialogHandler : CheckinHandlerFactory() {
    override fun createHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler {
      return CheckinHandler.DUMMY
    }

    override fun createSystemReadyHandler(project: Project): BeforeCheckinDialogHandler? {
      return BEFORE_CHECKIN_DIALOG_HANDLER
    }

    companion object {
      private val BEFORE_CHECKIN_DIALOG_HANDLER = object : BeforeCheckinDialogHandler() {
        override fun beforeCommitDialogShown(project: Project, changes: List<Change>, executors: Iterable<CommitExecutor>, showVcsCommit: Boolean): Boolean {
          val collectConsumer = ProjectChangeCollectConsumer(project)
          collectProjectChanges(changes, collectConsumer)
          showDialog(project, collectConsumer, null)
          return true
        }
      }
    }
  }

  override fun getActionName(dataContext: VcsContext) = IcsBundle.message("action.CommitToIcs.text")

  override fun isApplicableRoot(file: VirtualFile, status: FileStatus, dataContext: VcsContext): Boolean {
    val project = dataContext.getProject()
    return project is ProjectEx && project.getStateStore().getStorageScheme() == StorageScheme.DIRECTORY_BASED && super.isApplicableRoot(file, status, dataContext) && !file.isDirectory() && isProjectConfigFile(file, dataContext.getProject()!!)
  }

  override fun prepareRootsForCommit(roots: Array<out FilePath>, project: Project) = roots as Array<FilePath>

  override fun performCheckIn(context: VcsContext, project: Project, roots: Array<out FilePath>) {
    val projectId = getProjectId(project)
    if (projectId == null) {
      return
    }

    val changes = context.getSelectedChanges()
    val collectConsumer = ProjectChangeCollectConsumer(project)
    if (changes != null && changes.isNotEmpty()) {
      for (change in changes) {
        collectConsumer.consume(change)
      }
    }
    else {
      val manager = ChangeListManager.getInstance(project)
      for (path in getRoots(context)) {
        collectProjectChanges(manager.getChangesIn(path), collectConsumer)
      }
    }

    showDialog(project, collectConsumer, projectId)
  }
}

private class ProjectChangeCollectConsumer(private val project: Project) {
  private var projectChanges: MutableList<Change>? = null

  fun consume(change: Change) {
    if (isProjectConfigFile(change.getVirtualFile(), project)) {
      if (projectChanges == null) {
        projectChanges = SmartList<Change>()
      }
      projectChanges!!.add(change)
    }
  }

  fun getResult() = if (projectChanges == null) listOf<Change>() else projectChanges!!

  fun hasResult() = projectChanges != null
}

private fun getProjectId(project: Project): String? {
  val projectId = ServiceManager.getService<ProjectId>(project, javaClass<ProjectId>())!!
  if (projectId.uid == null) {
    if (MessageDialogBuilder.yesNo("Settings Server Project Mapping", "Project is not mapped on Settings Server. Would you like to map?").project(project).doNotAsk(object : DialogWrapper.PropertyDoNotAskOption("") {
      override fun setToBeShown(value: Boolean, exitCode: Int) {
        icsManager.settings.doNoAskMapProject = !value
      }

      override fun isToBeShown(): Boolean {
        return !icsManager.settings.doNoAskMapProject
      }

      override fun canBeHidden(): Boolean {
        return true
      }
    }).show() == Messages.YES) {
      projectId.uid = UUID.randomUUID().toString()
    }
  }

  return projectId.uid
}

private fun showDialog(project: Project, collectConsumer: ProjectChangeCollectConsumer, projectId: String?) {
  if (!collectConsumer.hasResult()) {
    return
  }

  var effectiveProjectId = projectId
  if (effectiveProjectId == null) {
    effectiveProjectId = getProjectId(project)
    if (effectiveProjectId == null) {
      return
    }
  }

  CommitToIcsDialog(project, effectiveProjectId, collectConsumer.getResult()).show()
}

private fun collectProjectChanges(changes: Collection<Change>, collectConsumer: ProjectChangeCollectConsumer) {
  for (change in changes) {
    collectConsumer.consume(change)
  }
}

private fun isProjectConfigFile(file: VirtualFile?, project: Project): Boolean {
  if (file == null) {
    return false
  }

  val projectFile = project.getProjectFile()
  val projectConfigDir = projectFile?.getParent()
  return projectConfigDir != null && VfsUtilCore.isAncestor(projectConfigDir, file, true)
}
