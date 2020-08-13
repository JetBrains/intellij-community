package com.intellij.space.vcs.share

import circlet.client.api.RepoDetails
import circlet.client.api.identifier
import circlet.client.api.impl.vcsPasswords
import circlet.client.td
import com.intellij.CommonBundle
import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.notification.NotificationListener
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.NlsContexts.NotificationContent
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.ui.SelectFilesDialog
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.space.actions.SpaceActionUtils
import com.intellij.space.components.space
import com.intellij.space.messages.SpaceBundle
import com.intellij.space.settings.CloneType
import com.intellij.space.settings.SpaceSettings
import com.intellij.space.vcs.SpaceHttpPasswordState
import com.intellij.space.vcs.SpaceProjectContext
import com.intellij.space.vcs.SpaceSetGitHttpPasswordDialog
import com.intellij.util.containers.ContainerUtil
import com.intellij.vcsUtil.VcsFileUtil
import com.intellij.xml.util.XmlStringUtil.formatLink
import com.intellij.xml.util.XmlStringUtil.wrapInHtmlLines
import git4idea.GitUtil
import git4idea.actions.GitInit
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.util.GitFileUtils
import libraries.coroutines.extra.Lifetime
import libraries.coroutines.extra.launch
import libraries.klogging.KLogger
import libraries.klogging.logger
import runtime.Ui
import java.util.*

class SpaceShareProjectAction : DumbAwareAction() {
  private val log: KLogger = logger<SpaceShareProjectAction>()

  private val git: Git = Git.getInstance()

  override fun update(e: AnActionEvent) {
    SpaceActionUtils.showIconInActionSearch(e)
    val project = e.project
    if (project == null || project.isDefault) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    val context = SpaceProjectContext.getInstance(project)
    if (context.context.value.isAssociatedWithSpaceRepository) {
      e.presentation.isEnabledAndVisible = false
      return
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.getData(CommonDataKeys.PROJECT)
    val file = e.getData(CommonDataKeys.VIRTUAL_FILE)

    if (project == null || project.isDefault || project.isDisposed) {
      return
    }
    launch(Lifetime.Eternal, Ui) {
      // check that http password set before start sharing process
      if (SpaceSettings.getInstance().cloneType == CloneType.HTTP) {
        if (checkAndSetGitHttpPassword() is SpaceHttpPasswordState.Set) {
          shareProject(project, file)
        }
      }
      else {
        shareProject(project, file)
      }
    }
  }

  private fun shareProject(project: Project, file: VirtualFile?) {
    FileDocumentManager.getInstance().saveAllDocuments()

    // create project and repository on Space
    val result = createSpaceProject(project) ?: return
    val (repoInfo, repoDetails, url) = result

    object : Task.Backgroundable(project, SpaceBundle.message("share.project.action.progress.title.sharing.title")) {
      override fun run(indicator: ProgressIndicator) {
        indicator.text = SpaceBundle.message("share.project.action.progress.title.searching.repository.title")
        val repository = if (file != null) {
          VcsRepositoryManager.getInstance(project).getRepositoryForFile(file, false)
        }
        else {
          VcsRepositoryManager.getInstance(project).getRepositoryForFile(project.baseDir, false)
        }
        log.info("Found repository: $repository")
        val root = repository?.root ?: project.guessProjectDir() ?: return
        log.info("Found project root: $root")

        // create repository if needed
        if (repository == null) {
          log.info { "No git repo detected, creating empty git repository" }
          if (!createEmptyGitRepository(project, root, indicator)) {
            log.error("Unable to init git repository")
            return
          }
        }

        val gitRepoManager = GitUtil.getRepositoryManager(project)
        val gitRepo = gitRepoManager.getRepositoryForRoot(root) ?: throw Exception("Can't find Git repository")
        val git = Git.getInstance()

        // add remote url
        val (remoteUrl, remoteName) = addRemoteUrl(repoDetails, git, gitRepo, indicator)

        // commit
        if (!performFirstCommitIfRequired(project, root, gitRepo, indicator, repoInfo.name, url)) {
          log.info("Commit not finished")
          return
        }

        // push
        if (!pushCurrentBranch(project, gitRepo, remoteName, remoteUrl, repoInfo.name, url, indicator)) {
          log.info("Push not finished")
          return
        }

        VcsNotifier.getInstance(project).notifySuccess(
          "space.project.shared.successfully",
          SpaceBundle.message("share.project.success.notification.title"),
          formatLink(url, repoInfo.name),
          NotificationListener.URL_OPENING_LISTENER
        )
      }
    }.queue()
  }

  private suspend fun checkAndSetGitHttpPassword(): SpaceHttpPasswordState {
    val client = space.workspace.value?.client ?: return SpaceHttpPasswordState.NotSet
    val me = space.workspace.value?.me?.value ?: return SpaceHttpPasswordState.NotSet
    val td = client.td
    val gitHttpPassword = client.api.vcsPasswords().getVcsPassword(me.identifier)
    if (gitHttpPassword == null) {
      val passwordDialog = SpaceSetGitHttpPasswordDialog(me, client)
      if (passwordDialog.showAndGet() && passwordDialog.result is SpaceHttpPasswordState.Set) {
        return passwordDialog.result
      }
      return SpaceHttpPasswordState.NotSet
    }
    else {
      return SpaceHttpPasswordState.Set(gitHttpPassword)
    }
  }

  private fun createSpaceProject(project: Project): SpaceShareProjectDialog.Result? {
    log.info("Creating repository on Space")
    val shareProjectDialog = SpaceShareProjectDialog(project)
    if (shareProjectDialog.showAndGet()) {
      val result = shareProjectDialog.result
      if (result == null) {
        log.info("Repository not created")
        return null
      }
      log.info("Repository created successfully: $result")
      return result
    }
    return null
  }

  private fun createEmptyGitRepository(project: Project, root: VirtualFile, indicator: ProgressIndicator): Boolean {
    indicator.text = SpaceBundle.message("share.project.action.progress.title.initializing.repository.title")
    val result = Git.getInstance().init(project, root)
    if (!result.success()) {
      VcsNotifier.getInstance(project).notifyError("space.git.repo.init.error",
                                                   GitBundle.getString("initializing.title"),
                                                   result.errorOutputAsHtmlString)
      log.info { "Failed to create empty git repo: " + result.errorOutputAsJoinedString }
      return false
    }
    GitInit.refreshAndConfigureVcsMappings(project, root, root.path)
    GitUtil.generateGitignoreFileIfNeeded(project, root)
    return true
  }

  private fun addRemoteUrl(repoDetails: RepoDetails, git: Git, gitRepo: GitRepository, indicator: ProgressIndicator): Pair<String, String> {
    val remoteUrl = when (SpaceSettings.getInstance().cloneType) {
      CloneType.HTTP -> repoDetails.urls.httpUrl
      CloneType.SSH -> repoDetails.urls.sshUrl
    } as String

    val remoteName = "origin"
    indicator.text = SpaceBundle.message("share.project.action.progress.title.adding.remote.title")
    val commandResult = git.addRemote(gitRepo, remoteName, remoteUrl)
    if (commandResult.success()) {
      gitRepo.update()
    }
    else {
      log.error {
        commandResult.errorOutputAsJoinedString
      }
    }
    return Pair(remoteUrl, remoteName)
  }

  private fun performFirstCommitIfRequired(project: Project,
                                           root: VirtualFile,
                                           repository: GitRepository,
                                           indicator: ProgressIndicator,
                                           name: String,
                                           url: String): Boolean {
    if (!repository.isFresh) { // check if there is no commits
      return true
    }

    try {
      log.info("Adding files for commit")
      indicator.text = SpaceBundle.message("share.project.action.progress.title.adding.files.title")

      // ask for files to add
      val changeListManager = ChangeListManager.getInstance(project)
      val vcsManager = ProjectLevelVcsManager.getInstance(project)
      val untrackedFiles = repository.untrackedFilesHolder.retrieveUntrackedFilePaths()
        .mapNotNull(FilePath::getVirtualFile)
        .filter { file -> !vcsManager.isIgnored(file) && !changeListManager.isIgnoredFile(file) }
      val trackedFiles = changeListManager.affectedFiles
      trackedFiles.removeAll(untrackedFiles)

      val allFiles = trackedFiles + untrackedFiles
      val filesToCommit = selectFilesToCommit(project, indicator, allFiles, trackedFiles)
      if (filesToCommit.isEmpty()) return false

      val filesToAdd = ContainerUtil.intersection(untrackedFiles, filesToCommit)
      val filesToDelete = ContainerUtil.subtract(trackedFiles, filesToCommit)
      val affectedFiles = HashSet(trackedFiles + filesToCommit)

      GitFileUtils.addFiles(project, root, filesToAdd)
      GitFileUtils.deleteFilesFromCache(project, root, filesToDelete)

      // commit
      log.info("Performing commit")
      indicator.text = SpaceBundle.message("share.project.action.progress.title.committing.title")
      val handler = GitLineHandler(project, root, GitCommand.COMMIT)
      handler.setStdoutSuppressed(false)
      handler.addParameters("-m", "Initial commit")
      handler.endOptions()
      Git.getInstance().runCommand(handler).throwOnError()

      VcsFileUtil.markFilesDirty(project, affectedFiles)
    }
    catch (e: VcsException) {
      log.warn(e)
      val repositoryLink = formatLink(url, "'$name'")
      notifyError(project, wrapInHtmlLines(
        SpaceBundle.message("share.project.error.notification.initial.commit.failed.message", repositoryLink),
        *e.messages
      ))
      return false
    }

    log.info("Successfully created initial commit")
    return true
  }

  private fun selectFilesToCommit(project: Project,
                                  indicator: ProgressIndicator,
                                  allFiles: List<VirtualFile>,
                                  preselectedFiles: MutableList<VirtualFile>): Collection<VirtualFile> {
    val dialog = invokeAndWaitIfNeeded(indicator.modalityState) {
      val selectFilesDialog = SelectFilesDialog.init(project, allFiles, null, null, true, false,
                                                     CommonBundle.getAddButtonText(),
                                                     CommonBundle.getCancelButtonText())
      selectFilesDialog.title = SpaceBundle.message("share.project.action.progress.title.adding.files.to.commit.title")
      if (preselectedFiles.isNotEmpty()) {
        selectFilesDialog.selectedFiles = preselectedFiles
      }
      selectFilesDialog.showAndGet()
      selectFilesDialog
    }

    if (!dialog.isOK) return emptyList()
    return dialog.selectedFiles
  }

  private fun pushCurrentBranch(project: Project,
                                repository: GitRepository,
                                remoteName: String,
                                remoteUrl: String,
                                name: String,
                                url: String,
                                indicator: ProgressIndicator): Boolean {
    log.info("Pushing to master")
    indicator.text = SpaceBundle.message("share.project.action.progress.title.pushing.title")

    val currentBranch = repository.currentBranch
    val repositoryLink = formatLink(url, "'$name'")
    if (currentBranch == null) {
      notifyError(project, SpaceBundle.message("share.project.error.notification.no.current.branch.message", repositoryLink))
      return false
    }
    val result = git.push(repository, remoteName, remoteUrl, currentBranch.name, true)
    if (!result.success()) {
      notifyError(project, wrapInHtmlLines(
        SpaceBundle.message("share.project.error.notification.push.failed.message", repositoryLink),
        result.errorOutputAsHtmlString
      ))
      return false
    }
    return true
  }

  private fun notifyError(project: Project, @NotificationContent message: String) {
    VcsNotifier.getInstance(project).notifyError(
      "space.sharing.not.finished",
      SpaceBundle.message("share.project.error.notification.title"),
      message,
      NotificationListener.URL_OPENING_LISTENER
    )
  }
}

