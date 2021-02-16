// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs

import circlet.client.api.ProjectKey
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
import com.intellij.space.messages.SpaceBundle
import com.intellij.space.utils.SpaceUrls
import com.intellij.vcs.log.CommitId
import com.intellij.vcs.log.VcsLogDataKeys
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitRevisionNumber
import git4idea.GitUtil
import git4idea.history.GitHistoryUtils
import git4idea.repo.GitRepository
import icons.SpaceIcons
import com.intellij.openapi.util.Ref as Ref1

abstract class SpaceOpenInBrowserActionGroup<T>(@NlsActions.ActionText groupName: String) :
  ActionGroup(groupName, SpaceBundle.message("open.in.browser.group.description"), SpaceIcons.Main),
  DumbAware {

  abstract fun getData(dataContext: DataContext): List<T>?

  abstract fun buildAction(it: T): AnAction

  override fun isPopup(): Boolean = true

  override fun canBePerformed(context: DataContext): Boolean = getData(context)?.size == 1

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
    internal fun getProjectAwareUrls(
      context: DataContext,
      urlsBuilder: (ProjectKey) -> String
    ): List<Pair<SpaceProjectInfo, String>>? {
      val project = context.getData(CommonDataKeys.PROJECT) ?: return null
      val description = SpaceProjectContext.getInstance(project).context.value

      return description.reposInProject.keys.map {
        it to urlsBuilder(it.key)
      }.toList()
    }
  }
}

class OpenReviews : SpaceOpenInBrowserAction(SpaceBundle.message("open.in.browser.group.code.reviews")) {
  override fun getData(dataContext: DataContext): List<Pair<SpaceProjectInfo, String>>? {
    return getProjectAwareUrls(dataContext, SpaceUrls::reviews)
  }
}

class OpenChecklists : SpaceOpenInBrowserAction(SpaceBundle.message("open.in.browser.group.checklists")) {
  override fun getData(dataContext: DataContext): List<Pair<SpaceProjectInfo, String>>? {
    return getProjectAwareUrls(dataContext, SpaceUrls::checklists)
  }
}

class OpenIssues : SpaceOpenInBrowserAction(SpaceBundle.message("open.in.browser.group.issues")) {
  override fun getData(dataContext: DataContext): List<Pair<SpaceProjectInfo, String>>? {
    return getProjectAwareUrls(dataContext, SpaceUrls::issues)
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

    return getDataFromHistory(dataContext, project)
           ?: getDataFromLog(dataContext, project)
           ?: getDataFromVirtualFile(dataContext, project)
  }

  private fun getDataFromVirtualFile(dataContext: DataContext, project: Project): List<OpenData>? {
    val virtualFile = dataContext.getData(CommonDataKeys.VIRTUAL_FILE) ?: return null
    val gitRepository = VcsRepositoryManager.getInstance(project).getRepositoryForFileQuick(virtualFile) ?: return null
    if (gitRepository !is GitRepository) return null

    val changeListManager = ChangeListManager.getInstance(project)
    if (changeListManager.isUnversioned(virtualFile)) return null
    if (changeListManager.isIgnoredFile(virtualFile)) return null

    val change = changeListManager.getChange(virtualFile)
    if (change != null && change.type == Change.Type.NEW) return null

    val repoDescription = findProjectInfo(gitRepository, project) ?: return null

    val editor = dataContext.getData(CommonDataKeys.EDITOR)
    val line = if (editor != null && editor.document.lineCount >= 1) {
      editor.caretModel.currentCaret.logicalPosition.line
    }
    else null

    return repoDescription.projectInfos.map { OpenData.File(project, it, repoDescription.name, virtualFile, line, gitRepository) }
  }

  private fun getDataFromHistory(dataContext: DataContext, project: Project): List<OpenData>? {
    val filePath = dataContext.getData(VcsDataKeys.FILE_PATH) ?: return null
    val fileRevision = dataContext.getData(VcsDataKeys.VCS_FILE_REVISION) ?: return null
    if (fileRevision !is VcsFileRevisionEx) return null
    val gitRepository = GitUtil.getRepositoryManager(project).getRepositoryForFileQuick(filePath) ?: return null
    val repoDescription = findProjectInfo(gitRepository, project) ?: return null

    return repoDescription.projectInfos.map { OpenData.FileRevision(project, it, repoDescription.name, fileRevision) }
  }

  private fun getDataFromLog(dataContext: DataContext, project: Project): List<OpenData>? {
    val vcsLog = dataContext.getData(VcsLogDataKeys.VCS_LOG) ?: return null
    val selectedCommit = vcsLog.selectedCommits.firstOrNull() ?: return null
    val gitRepository = GitUtil.getRepositoryManager(project).getRepositoryForFileQuick(selectedCommit.root) ?: return null
    val repoDescription = findProjectInfo(gitRepository, project) ?: return null

    return repoDescription.projectInfos.map { OpenData.Commit(project, it, repoDescription.name, selectedCommit) }
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
                          val info: SpaceProjectInfo,
                          val repo: String
    ) {
      abstract val url: String?

      class Commit(project: Project,
                   projectKey: SpaceProjectInfo,
                   repo: String,
                   private val commit: CommitId) : OpenData(project, projectKey, repo) {

        override val url: String
          get() {
            return SpaceUrls.commits(info.key, repo, commit.hash.asString())
          }
      }

      class FileRevision(project: Project,
                         projectKey: SpaceProjectInfo,
                         repo: String,
                         private val vcsFileRevisionEx: VcsFileRevisionEx) : OpenData(project, projectKey, repo) {

        override val url: String
          get() {
            return SpaceUrls.revision(info.key, repo, vcsFileRevisionEx.revisionNumber.asString())
          }
      }

      class File(project: Project,
                 projectKey: SpaceProjectInfo,
                 repo: String,
                 private val virtualFile: VirtualFile,
                 private val selectedLine: Int? = null,
                 private val gitRepository: GitRepository) : OpenData(project, projectKey, repo) {
        override val url: String?
          get() {
            val relativePath = VfsUtilCore.getRelativePath(virtualFile, gitRepository.root) ?: return null
            val hash = getCurrentFileRevisionHash(project, virtualFile) ?: return null

            return SpaceUrls.fileAnnotate(info.key, repo, hash, relativePath, selectedLine)
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
