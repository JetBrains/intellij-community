package com.intellij.space.vcs

import circlet.client.api.Navigator
import circlet.client.api.ProjectLocation
import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.history.VcsFileRevisionEx
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.space.actions.SpaceActionUtils
import com.intellij.space.components.space
import com.intellij.space.messages.SpaceBundle
import com.intellij.vcs.log.CommitId
import com.intellij.vcs.log.VcsLogDataKeys
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitRevisionNumber
import git4idea.GitUtil
import git4idea.history.GitHistoryUtils
import git4idea.repo.GitRepository
import icons.SpaceIcons
import runtime.routing.Location
import com.intellij.openapi.util.Ref as Ref1

abstract class SpaceOpenInBrowserActionGroup<T>(@NlsActions.ActionText groupName: String) :
  ActionGroup(groupName, SpaceBundle.message("open.in.browser.group.description"), SpaceIcons.Main),
  DumbAware {

  abstract fun getData(dataContext: DataContext): List<T>?

  abstract fun buildAction(it: T): AnAction

  override fun isPopup(): Boolean = true

  override fun disableIfNoVisibleChildren(): Boolean = false

  override fun canBePerformed(context: DataContext) = getData(context)?.size == 1

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    e ?: return emptyArray()
    val data = getData(e.dataContext) ?: return emptyArray()
    if (data.size <= 1) return emptyArray()

    return data.mapNotNull { buildAction(it) }.toTypedArray()
  }

  override fun actionPerformed(e: AnActionEvent) {
    getData(e.dataContext)?.let { buildAction(it.first()).actionPerformed(e) }
  }
}

abstract class SpaceOpenInBrowserAction(@NlsActions.ActionText groupName: String) :
  SpaceOpenInBrowserActionGroup<Pair<SpaceProjectInfo, String>>(groupName) {

  override fun update(e: AnActionEvent) {
    SpaceActionUtils.showIconInActionSearch(e)
    val project = e.project
    if (project != null) {
      val projectContext = SpaceProjectContext.getInstance(project)
      if (projectContext.context.value.isAssociatedWithSpaceRepository) {
        e.presentation.isEnabledAndVisible = true

        return
      }
    }
    e.presentation.isEnabledAndVisible = false
  }

  override fun buildAction(it: Pair<SpaceProjectInfo, String>): AnAction =
    object : AnAction(SpaceBundle.message("open.in.browser.open.for.project.action", it.first.key.key)) {
      override fun actionPerformed(e: AnActionEvent) = BrowserUtil.browse(it.second)
    }

  companion object {
    internal fun getProjectAwareUrls(endpoint: (ProjectLocation) -> Location,
                                     context: DataContext): List<Pair<SpaceProjectInfo, String>>? {
      val project = context.getData(CommonDataKeys.PROJECT) ?: return null
      val server = space.workspace.value?.client?.server?.removeSuffix("/") ?: return null
      val description = SpaceProjectContext.getInstance(project).context.value

      return description.reposInProject.keys.map {
        val projectLocation = Navigator.p.project(it.key)
        val url = endpoint(projectLocation).absoluteHref(server)

        it to url
      }.toList()
    }
  }
}

class OpenReviews : SpaceOpenInBrowserAction(SpaceBundle.message("open.in.browser.group.code.reviews")) {
  override fun getData(dataContext: DataContext): List<Pair<SpaceProjectInfo, String>>? {
    return getProjectAwareUrls(ProjectLocation::reviews, dataContext)
  }
}

class OpenChecklists : SpaceOpenInBrowserAction(SpaceBundle.message("open.in.browser.group.checklists")) {
  override fun getData(dataContext: DataContext): List<Pair<SpaceProjectInfo, String>>? {
    return getProjectAwareUrls(ProjectLocation::checklists, dataContext)
  }
}

class OpenIssues : SpaceOpenInBrowserAction(SpaceBundle.message("open.in.browser.group.issues")) {
  override fun getData(dataContext: DataContext): List<Pair<SpaceProjectInfo, String>>? {
    return getProjectAwareUrls(ProjectLocation::issues, dataContext)
  }
}

class SpaceVcsOpenInBrowserActionGroup :
  SpaceOpenInBrowserActionGroup<SpaceVcsOpenInBrowserActionGroup.Companion.OpenData>(
    SpaceBundle.message("open.in.browser.group.open.on.space")
  ) {

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = (getData(e.dataContext) != null)
  }

  override fun buildAction(it: OpenData): AnAction = OpenAction(it)

  override fun getData(dataContext: DataContext): List<OpenData>? {
    val project = dataContext.getData(CommonDataKeys.PROJECT) ?: return null
    val server = space.workspace.value?.client?.server?.removeSuffix("/") ?: return null

    return getDataFromHistory(dataContext, project, server)
           ?: getDataFromLog(dataContext, project, server)
           ?: getDataFromVirtualFile(dataContext, project, server)
  }

  private fun getDataFromVirtualFile(dataContext: DataContext, project: Project, server: String): List<OpenData>? {
    val virtualFile = dataContext.getData(CommonDataKeys.VIRTUAL_FILE) ?: return null
    val gitRepository = VcsRepositoryManager.getInstance(project).getRepositoryForFileQuick(virtualFile) ?: return null
    if (gitRepository !is GitRepository) return null

    val changeListManager = ChangeListManager.getInstance(project)
    if (changeListManager.isUnversioned(virtualFile)) return null
    if (changeListManager.isIgnoredFile(virtualFile)) return null

    val change = changeListManager.getChange(virtualFile)
    if (change != null && change.type == Change.Type.NEW) return null

    val repoDescription = findProjectInfo(gitRepository, project) ?: return null

    return repoDescription.projectInfos.map { OpenData.File(project, server, it, repoDescription.name, virtualFile, gitRepository) }

  }

  private fun getDataFromHistory(dataContext: DataContext, project: Project, server: String): List<OpenData>? {
    val filePath = dataContext.getData(VcsDataKeys.FILE_PATH) ?: return null
    val fileRevision = dataContext.getData(VcsDataKeys.VCS_FILE_REVISION) ?: return null
    if (fileRevision !is VcsFileRevisionEx) return null
    val gitRepository = GitUtil.getRepositoryManager(project).getRepositoryForFileQuick(filePath) ?: return null
    val repoDescription = findProjectInfo(gitRepository, project) ?: return null

    return repoDescription.projectInfos.map { OpenData.FileRevision(project, server, it, repoDescription.name, fileRevision) }
  }

  private fun getDataFromLog(dataContext: DataContext, project: Project, server: String): List<OpenData>? {
    val vcsLog = dataContext.getData(VcsLogDataKeys.VCS_LOG) ?: return null
    val selectedCommit = vcsLog.selectedCommits.firstOrNull() ?: return null
    val gitRepository = GitUtil.getRepositoryManager(project).getRepositoryForFileQuick(selectedCommit.root) ?: return null
    val repoDescription = findProjectInfo(gitRepository, project) ?: return null

    return repoDescription.projectInfos.map { OpenData.Commit(project, server, it, repoDescription.name, selectedCommit) }
  }

  private fun findProjectInfo(gitRepository: GitRepository, project: Project): SpaceRepoInfo? {
    val spaceContext = SpaceProjectContext.getInstance(project)

    return getRemoteUrls(gitRepository)
      .mapNotNull { spaceContext.getRepoDescriptionByUrl(it) }
      .firstOrNull()
  }

  private fun getRemoteUrls(gitRepository: GitRepository): List<String> {
    return gitRepository.remotes.flatMap { it.urls }.toList()
  }

  companion object {
    class OpenAction(private val data: OpenData) : DumbAwareAction(
      SpaceBundle.message("open.in.browser.open.for.project.action", data.info.project.name)
    ) {

      override fun actionPerformed(e: AnActionEvent) {
        data.url?.let { BrowserUtil.browse(it) }
      }
    }

    sealed class OpenData(val project: Project,
                          val server: String,
                          val info: SpaceProjectInfo,
                          val repo: String
    ) {
      abstract val url: String?

      class Commit(project: Project,
                   server: String,
                   projectKey: SpaceProjectInfo,
                   repo: String,
                   private val commit: CommitId) : OpenData(project, server, projectKey, repo) {

        override val url: String?
          get() {
            return Navigator.p.project(info.key)
              .commits(repo, "", commit.hash.asString())
              .absoluteHref(server)
          }
      }

      class FileRevision(project: Project,
                         server: String,
                         projectKey: SpaceProjectInfo,
                         repo: String,
                         private val vcsFileRevisionEx: VcsFileRevisionEx) : OpenData(project, server, projectKey, repo) {

        override val url: String?
          get() {
            return Navigator.p.project(info.key)
              .revision(repo, vcsFileRevisionEx.revisionNumber.asString())
              .absoluteHref(server)
          }
      }

      class File(project: Project,
                 server: String,
                 projectKey: SpaceProjectInfo,
                 repo: String,
                 private val virtualFile: VirtualFile, private val gitRepository: GitRepository) : OpenData(project, server, projectKey,
                                                                                                            repo) {
        override val url: String?
          get() {
            val relativePath = VfsUtilCore.getRelativePath(virtualFile, gitRepository.root) ?: return null
            val hash = getCurrentFileRevisionHash(project, virtualFile) ?: return null

            return Navigator.p.project(info.key)
              .fileAnnotate(repo, hash, relativePath)
              .absoluteHref(server)
          }

        private fun getCurrentFileRevisionHash(project: Project, file: VirtualFile): String? {
          val ref = Ref1<GitRevisionNumber>()
          object : Task.Modal(project, SpaceBundle.message("open.file.on.space.getting.last.revision.indicator.text"), true) {
            override fun run(indicator: ProgressIndicator) {
              ref.set(GitHistoryUtils.getCurrentRevision(project, VcsUtil.getFilePath(file), GitUtil.HEAD) as GitRevisionNumber?)
            }

            override fun onThrowable(error: Throwable) {
            }
          }.queue()
          return if (ref.isNull) null else ref.get().rev
        }
      }
    }
  }
}
