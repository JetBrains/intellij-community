// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs

import circlet.client.api.ProjectKey
import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.ide.CopyPasteManager
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
import java.awt.datatransfer.StringSelection
import com.intellij.openapi.util.Ref as Ref1

abstract class SpaceOpenInBrowserActionGroup<T>(@NlsActions.ActionText groupName: String,
                                                @NlsActions.ActionDescription val description: String) :
  ActionGroup(groupName, description, SpaceIcons.Main),
  DumbAware {

  abstract fun getData(dataContext: DataContext): List<T>?

  abstract fun buildAction(it: T): AnAction

  override fun isPopup(): Boolean = true

  override fun canBePerformed(context: DataContext): Boolean = getData(context)?.size == 1

  override fun disableIfNoVisibleChildren(): Boolean = false

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
  SpaceOpenInBrowserActionGroup<Pair<SpaceProjectInfo, String>>(groupName, SpaceBundle.message("open.in.browser.group.description")) {

  override fun update(e: AnActionEvent) {
    SpaceActionUtils.showIconInActionSearch(e)
    val project = e.project
    if (project != null) {
      val projectContext = SpaceProjectContext.getInstance(project)
      if (projectContext.currentContext.isAssociatedWithSpaceRepository) {
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
      val description = SpaceProjectContext.getInstance(project).currentContext

      return description.reposInProject.keys.map {
        it to urlsBuilder(it.key)
      }.toList()
    }
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

internal abstract class SpaceUrlBasedActionGroup(@NlsActions.ActionText groupName: String,
                                                 @NlsActions.ActionDescription description: String) :
  SpaceOpenInBrowserActionGroup<UrlData>(groupName, description) {

  override fun update(e: AnActionEvent) {
    val data = getData(e.dataContext)
    e.presentation.isEnabledAndVisible = (data != null)
  }

  override fun getData(dataContext: DataContext): List<UrlData>? {
    val project = dataContext.getData(CommonDataKeys.PROJECT) ?: return null

    return getDataFromHistory(dataContext, project)
           ?: getDataFromLog(dataContext, project)
           ?: getDataFromVirtualFile(dataContext, project)
  }

  private fun getDataFromVirtualFile(dataContext: DataContext, project: Project): List<UrlData>? {
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

    return repoDescription.projectInfos.map { UrlData.File(project, it, repoDescription.name, virtualFile, line, gitRepository) }
  }

  private fun getDataFromHistory(dataContext: DataContext, project: Project): List<UrlData>? {
    val filePath = dataContext.getData(VcsDataKeys.FILE_PATH) ?: return null
    val fileRevision = dataContext.getData(VcsDataKeys.VCS_FILE_REVISION) ?: return null
    if (fileRevision !is VcsFileRevisionEx) return null
    val gitRepository = GitUtil.getRepositoryManager(project).getRepositoryForFileQuick(filePath) ?: return null
    val repoDescription = findProjectInfo(gitRepository, project) ?: return null

    return repoDescription.projectInfos.map { UrlData.FileRevision(project, it, repoDescription.name, fileRevision) }
  }

  private fun getDataFromLog(dataContext: DataContext, project: Project): List<UrlData>? {
    val vcsLog = dataContext.getData(VcsLogDataKeys.VCS_LOG) ?: return null
    val selectedCommit = vcsLog.selectedCommits.firstOrNull() ?: return null
    val gitRepository = GitUtil.getRepositoryManager(project).getRepositoryForFileQuick(selectedCommit.root) ?: return null
    val repoDescription = findProjectInfo(gitRepository, project) ?: return null

    return repoDescription.projectInfos.map { UrlData.Commit(project, it, repoDescription.name, selectedCommit) }
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
}

internal class SpaceVcsOpenInBrowserActionGroup : SpaceUrlBasedActionGroup(SpaceBundle.message("open.in.browser.group.open.on.space"),
                                                                           SpaceBundle.message("open.in.browser.group.description")) {
  override fun buildAction(it: UrlData): AnAction = OpenAction(it)
}

internal class SpaceCopyLinkActionGroup : SpaceUrlBasedActionGroup(SpaceBundle.message("copy.link.to.space.action"),
                                                                   SpaceBundle.message("copy.link.to.space.action.description")) {
  override fun buildAction(it: UrlData): AnAction = CopyLinkAction(it)
}

internal class OpenAction(private val data: UrlData) : DumbAwareAction(
  SpaceBundle.message("open.in.browser.open.for.project.action", data.info.project.name)
) {
  override fun actionPerformed(e: AnActionEvent) {
    data.url?.let { BrowserUtil.browse(it) }
  }
}

internal class CopyLinkAction(private val data: UrlData) : DumbAwareAction(
  SpaceBundle.messagePointer("copy.link.in.project.action", data.info.project.name)
) {
  override fun actionPerformed(e: AnActionEvent) {
    data.url?.let {
      CopyPasteManager.getInstance().setContents(StringSelection(it))
    }
  }
}


internal sealed class UrlData(val project: Project,
                              val info: SpaceProjectInfo,
                              val repo: String
) {
  abstract val url: String?

  internal class Commit(project: Project,
                        projectKey: SpaceProjectInfo,
                        repo: String,
                        private val commit: CommitId) : UrlData(project, projectKey, repo) {
    override val url: String
      get() {
        return SpaceUrls.commits(info.key, repo, commit.hash.asString())
      }
  }

  internal class FileRevision(project: Project,
                              projectKey: SpaceProjectInfo,
                              repo: String,
                              private val vcsFileRevisionEx: VcsFileRevisionEx) : UrlData(project, projectKey, repo) {
    override val url: String
      get() {
        return SpaceUrls.revision(info.key, repo, vcsFileRevisionEx.revisionNumber.asString())
      }
  }

  internal class File(project: Project,
                      projectKey: SpaceProjectInfo,
                      repo: String,
                      private val virtualFile: VirtualFile,
                      private val selectedLine: Int? = null,
                      private val gitRepository: GitRepository) : UrlData(project, projectKey, repo) {
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
