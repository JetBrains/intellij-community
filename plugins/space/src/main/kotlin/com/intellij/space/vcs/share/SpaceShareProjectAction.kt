// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.share

import circlet.client.api.RepoDetails
import circlet.client.api.identifier
import circlet.client.api.impl.vcsPasswords
import com.intellij.CommonBundle
import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationListener
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
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
import com.intellij.space.components.SpaceWorkspaceComponent
import com.intellij.space.messages.SpaceBundle
import com.intellij.space.notification.SpaceNotificationIdsHolder.Companion.GIT_REPO_INIT_ERROR
import com.intellij.space.notification.SpaceNotificationIdsHolder.Companion.PROJECT_SHARED_SUCCESSFULLY
import com.intellij.space.notification.SpaceNotificationIdsHolder.Companion.SHARING_NOT_FINISHED
import com.intellij.space.settings.CloneType
import com.intellij.space.settings.SpaceSettings
import com.intellij.space.stats.SpaceStatsCounterCollector
import com.intellij.space.utils.SpaceUrls
import com.intellij.space.vcs.SpaceHttpPasswordState
import com.intellij.space.vcs.SpaceProjectContext
import com.intellij.space.vcs.SpaceSetGitHttpPasswordDialog
import com.intellij.util.Consumer
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
import libraries.coroutines.extra.runBlocking
import libraries.coroutines.extra.using
import libraries.klogging.logger
import runtime.Ui

private class SpaceShareProjectAction : DumbAwareAction() {
  companion object {
    private val LOG = logger<SpaceShareProjectAction>()
  }

  private val git: Git = Git.getInstance()


  override fun update(e: AnActionEvent) {
    SpaceActionUtils.showIconInActionSearch(e)
    val project = e.project
    if (project == null || project.isDefault) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    val context = SpaceProjectContext.getInstance(project)
    if (context.currentContext.isAssociatedWithSpaceRepository) {
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

    SpaceStatsCounterCollector.OPEN_SHARE_PROJECT.log(
      SpaceStatsCounterCollector.LoginState.convert(SpaceWorkspaceComponent.getInstance().loginState.value)
    )
    ApplicationManager.getApplication().invokeLater(
      {
        FileDocumentManager.getInstance().saveAllDocuments()
        SpaceWorkspaceComponent.getInstance().lifetime.using { lt ->
          when (val creationResult = createSpaceProject(project)) {
            is SpaceShareProjectDialog.Result.ProjectCreated -> shareProjectOnSpace(project, file, creationResult)

            SpaceShareProjectDialog.Result.NotCreated -> {
              notifyError(project, SpaceBundle.message("share.project.error.notification.repository.not.created"))
            }
          }
        }
      },
      ModalityState.NON_MODAL
    )
  }

  private fun shareProjectOnSpace(project: Project, file: VirtualFile?, details: SpaceShareProjectDialog.Result.ProjectCreated) {
    val (repoInfo, repoDetails, url) = details

    object : Task.Backgroundable(project, SpaceBundle.message("share.project.action.progress.title.sharing.title")) {
      override fun run(indicator: ProgressIndicator) {
        indicator.text = SpaceBundle.message("share.project.action.progress.title.searching.repository.title")
        val repository = if (file != null) {
          VcsRepositoryManager.getInstance(project).getRepositoryForFile(file, false)
        }
        else {
          VcsRepositoryManager.getInstance(project).getRepositoryForFile(project.baseDir, false)
        }
        LOG.info("Found repository: $repository")
        val root = repository?.root ?: project.guessProjectDir() ?: return
        LOG.info("Found project root: $root")

        // create repository if needed
        if (repository == null) {
          LOG.info { "No git repo detected, creating empty git repository" }
          if (!createEmptyGitRepository(project, root, indicator)) {
            LOG.error("Unable to init git repository")
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
          LOG.info("Commit not finished")
          return
        }

        val gitAccessExists = SpaceWorkspaceComponent.getInstance().lifetime.using { lt ->
          runBlocking(lt, Ui) {
            checkPassword(project)
          }
        }

        if (gitAccessExists) {
          // push
          if (!pushCurrentBranch(project, gitRepo, remoteName, remoteUrl, repoInfo.name, url, indicator)) {
            LOG.info("Push not finished")
            return
          }

          VcsNotifier.getInstance(project).notifySuccess(
            PROJECT_SHARED_SUCCESSFULLY,
            SpaceBundle.message("share.project.success.notification.title"),
            formatLink(url, repoInfo.name), // NON-NLS
            NotificationListener.URL_OPENING_LISTENER
          )
        }
        else {
          val notification = VcsNotifier.IMPORTANT_ERROR_NOTIFICATION.createNotification(
            SpaceBundle.message("share.project.error.notification.title"),
            SpaceBundle.message("share.project.error.notification.not.pushed.git.access", formatLink(url, repoInfo.name)), // NON-NLS
            NotificationType.ERROR,
            NotificationListener.URL_OPENING_LISTENER,
            SHARING_NOT_FINISHED
          )
          val workspace = SpaceWorkspaceComponent.getInstance().workspace.value
          if (workspace != null) {
            notification.addAction(
              NotificationAction.create(SpaceBundle.message("share.project.error.notification.action.configure.text"), Consumer {
                val gitAccessConfigurationPageUrl = SpaceUrls.git(workspace.me.value.username)
                BrowserUtil.browse(gitAccessConfigurationPageUrl)
              }))
          }
          VcsNotifier.getInstance(project).notify(notification)
        }
      }
    }.queue()
  }

  private fun createSpaceProject(project: Project): SpaceShareProjectDialog.Result {
    LOG.info("Creating repository on Space")
    val shareProjectDialog = SpaceShareProjectDialog(project)
    if (shareProjectDialog.showAndGet()) {
      val result = shareProjectDialog.result
      if (result == null) {
        LOG.info("Repository not created")
        return SpaceShareProjectDialog.Result.NotCreated
      }
      LOG.info("Repository created successfully: $result")
      return result
    }
    return SpaceShareProjectDialog.Result.Canceled
  }

  private fun createEmptyGitRepository(project: Project, root: VirtualFile, indicator: ProgressIndicator): Boolean {
    indicator.text = SpaceBundle.message("share.project.action.progress.title.initializing.repository.title")
    val result = Git.getInstance().init(project, root)
    if (!result.success()) {
      VcsNotifier.getInstance(project).notifyError(GIT_REPO_INIT_ERROR,
                                                   GitBundle.message("initializing.title"),
                                                   result.errorOutputAsHtmlString)
      LOG.info { "Failed to create empty git repo: " + result.errorOutputAsJoinedString }
      return false
    }
    GitInit.refreshAndConfigureVcsMappings(project, root, root.path)
    GitUtil.generateGitignoreFileIfNeeded(project, root)
    return true
  }

  private fun addRemoteUrl(repoDetails: RepoDetails, git: Git, gitRepo: GitRepository, indicator: ProgressIndicator): Pair<String, String> {
    val remoteUrl = when (SpaceSettings.getInstance().cloneType) {
      CloneType.HTTPS -> repoDetails.urls.httpUrl
      CloneType.SSH -> repoDetails.urls.sshUrl
    } as String

    val remoteName = "space"
    indicator.text = SpaceBundle.message("share.project.action.progress.title.adding.remote.title")
    val commandResult = git.addRemote(gitRepo, remoteName, remoteUrl)
    if (commandResult.success()) {
      gitRepo.update()
    }
    else {
      LOG.error {
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
      LOG.info("Adding files for commit")
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
      LOG.info("Performing commit")
      indicator.text = SpaceBundle.message("share.project.action.progress.title.committing.title")
      val handler = GitLineHandler(project, root, GitCommand.COMMIT)
      handler.setStdoutSuppressed(false)
      handler.addParameters("-m", "Initial commit")
      handler.endOptions()
      Git.getInstance().runCommand(handler).throwOnError()

      VcsFileUtil.markFilesDirty(project, affectedFiles)
    }
    catch (e: VcsException) {
      LOG.warn(e)
      val repositoryLink = formatLink(url, "'$name'") // NON-NLS
      notifyError(project, wrapInHtmlLines( // NON-NLS
        SpaceBundle.message("share.project.error.notification.initial.commit.failed.message", repositoryLink),
        *e.messages
      ))
      return false
    }

    LOG.info("Successfully created initial commit")
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
    LOG.info("Pushing to master")
    indicator.text = SpaceBundle.message("share.project.action.progress.title.pushing.title")

    val currentBranch = repository.currentBranch
    val repositoryLink = formatLink(url, "'$name'") // NON-NLS
    if (currentBranch == null) {
      notifyError(project, SpaceBundle.message("share.project.error.notification.no.current.branch.message", repositoryLink))
      return false
    }
    val result = git.push(repository, remoteName, remoteUrl, currentBranch.name, true)
    if (!result.success()) {
      notifyError(project, wrapInHtmlLines( // NON-NLS
        SpaceBundle.message("share.project.error.notification.push.failed.message", repositoryLink),
        result.errorOutputAsHtmlString
      ))
      return false
    }
    return true
  }

  private fun notifyError(project: Project, @NotificationContent message: String) {
    VcsNotifier.getInstance(project).notifyError(
      SHARING_NOT_FINISHED,
      SpaceBundle.message("share.project.error.notification.title"),
      message,
      NotificationListener.URL_OPENING_LISTENER
    )
  }

  private suspend fun checkPassword(project: Project): Boolean {
    return when (SpaceSettings.getInstance().cloneType) {
      CloneType.HTTPS -> {
        checkAndSetGitHttpPassword(project) is SpaceHttpPasswordState.Set
      }
      CloneType.SSH -> false
    }
  }

  private suspend fun checkAndSetGitHttpPassword(project: Project): SpaceHttpPasswordState {
    val space = SpaceWorkspaceComponent.getInstance()
    val workspace = space.workspace.value ?: return SpaceHttpPasswordState.NotSet
    val client = workspace.client
    val me = workspace.me.value

    try {
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
    catch (e: Exception) {
      LOG.error(e, "Unable to check git https password")
      notifyError(project, SpaceBundle.message("share.project.error.unable.to.check.git.https.password"))
    }
    return SpaceHttpPasswordState.NotSet
  }

}

