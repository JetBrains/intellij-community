package circlet.vcs.share

import circlet.actions.*
import circlet.client.*
import circlet.client.api.*
import circlet.components.*
import circlet.settings.*
import circlet.vcs.*
import com.intellij.*
import com.intellij.dvcs.repo.*
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.*
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.progress.*
import com.intellij.openapi.project.*
import com.intellij.openapi.vcs.*
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.changes.ui.*
import com.intellij.openapi.vfs.*
import com.intellij.util.containers.*
import com.intellij.vcsUtil.*
import git4idea.*
import git4idea.actions.*
import git4idea.commands.*
import git4idea.i18n.*
import git4idea.repo.*
import git4idea.util.*
import libraries.coroutines.extra.*
import libraries.klogging.*
import runtime.*
import java.util.HashSet

class CircletShareProjectAction : DumbAwareAction() {
    private val log: KLogger = logger<CircletShareProjectAction>()

    private val git: Git = Git.getInstance()

    override fun update(e: AnActionEvent) {
        CircletActionUtils.showIconInActionSearch(e)
        val project = e.project
        if (project == null || project.isDefault) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val context = CircletProjectContext.getInstance(project)
        val descriptions = context.context.value.empty
        if (descriptions) {
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
            if (CircletSettings.getInstance().cloneType == CloneType.HTTP) {
                if (checkAndSetGitHttpPassword() is CircletHttpPasswordState.Set) {
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
        val result = createCircletProject(project) ?: return
        val (repoInfo, repoDetails, url) = result

        object : Task.Backgroundable(project, "Sharing Project on Space...") {
            override fun run(indicator: ProgressIndicator) {
                indicator.text = "Search for git repository"
                val repository = if (file != null) {
                    VcsRepositoryManager.getInstance(project).getRepositoryForFile(file, false)
                } else {
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

                // final notification
                CircletNotification.showInfoWithURL(project, "Successfully shared project on Space", repoInfo.name, url)
            }
        }.queue()
    }

    private suspend fun checkAndSetGitHttpPassword(): CircletHttpPasswordState {
        val client = circletWorkspace.workspace.value?.client ?: return CircletHttpPasswordState.NotSet
        val me = circletWorkspace.workspace.value?.me?.value ?: return CircletHttpPasswordState.NotSet
        val td = client.td
        val gitHttpPassword = td.getVcsPassword(me.id)
        if (gitHttpPassword == null) {
            val passwordDialog = CircletSetGitHttpPasswordDialog(me, td, client.repoService)
            if (passwordDialog.showAndGet() && passwordDialog.result is CircletHttpPasswordState.Set) {
                return passwordDialog.result
            }
            return CircletHttpPasswordState.NotSet
        }
        else {
            return CircletHttpPasswordState.Set(gitHttpPassword)
        }
    }

    private fun createCircletProject(project: Project): CircletShareProjectDialog.Result? {
        log.info("Creating repository on Space")
        val shareProjectDialog = CircletShareProjectDialog(project)
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
        indicator.text = "Init new git repository"
        val result = Git.getInstance().init(project, root)
        if (!result.success()) {
            VcsNotifier.getInstance(project).notifyError(GitBundle.getString("initializing.title"), result.errorOutputAsHtmlString)
            log.info { "Failed to create empty git repo: " + result.errorOutputAsJoinedString }
            return false
        }
        GitInit.refreshAndConfigureVcsMappings(project, root, root.path)
        GitUtil.generateGitignoreFileIfNeeded(project, root)
        return true
    }

    private fun addRemoteUrl(repoDetails: RepoDetails, git: Git, gitRepo: GitRepository, indicator: ProgressIndicator): Pair<String, String> {
        val remoteUrl = when (CircletSettings.getInstance().cloneType) {
            CloneType.HTTP -> repoDetails.urls.httpUrl
            CloneType.SSH -> repoDetails.urls.sshUrl
        } as String

        val remoteName = "origin"
        indicator.text = "Adding remote url"
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
            indicator.text = "Adding files to git..."

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
            indicator.text = "Performing commit..."
            val handler = GitLineHandler(project, root, GitCommand.COMMIT)
            handler.setStdoutSuppressed(false)
            handler.addParameters("-m", "Initial commit")
            handler.endOptions()
            Git.getInstance().runCommand(handler).throwOnError()

            VcsFileUtil.markFilesDirty(project, affectedFiles)
        } catch (e: VcsException) {
            log.warn(e)
            CircletNotification.showErrorWithURL(project, Messages.CANT_FINISH_SHARING, Messages.CREATED_PROJECT, "'$name'",
                                                 " on Space, but initial commit failed:<br/>" + e.messages,
                                                 url)
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
            selectFilesDialog.title = "Add Files For Initial Commit"
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
        indicator.text = "Pushing to master..."

        val currentBranch = repository.currentBranch
        if (currentBranch == null) {
            CircletNotification.showErrorWithURL(project, Messages.CANT_FINISH_SHARING, Messages.CREATED_PROJECT, "'$name'",
                                                 " on Space, but initial push failed: no current branch", url)
            return false
        }
        val result = git.push(repository, remoteName, remoteUrl, currentBranch.name, true)
        if (!result.success()) {
            CircletNotification.showErrorWithURL(project, Messages.CANT_FINISH_SHARING, Messages.CREATED_PROJECT, "'$name'",
                                                 " on Space, but initial push failed:<br/>" + result.errorOutputAsHtmlString, url)
            return false
        }
        return true
    }


    object Messages {
        const val CANT_FINISH_SHARING = "Sharing not finished"
        const val CREATED_PROJECT = "Created repository "
    }
}

