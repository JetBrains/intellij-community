package circlet.vcs

import circlet.actions.CircletActionUtils
import circlet.client.api.Navigator
import circlet.client.api.ProjectLocation
import circlet.components.circletWorkspace
import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.history.VcsFileRevisionEx
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
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

abstract class CircletOpenInBrowserActionGroup<T>(groupName: String) :
  ActionGroup(groupName, "Open link in browser", SpaceIcons.Main),
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

abstract class CircletOpenInBrowserAction(groupName: String) :
    CircletOpenInBrowserActionGroup<Pair<CircletProjectDescription, String>>(groupName) {

    override fun update(e: AnActionEvent) {
        CircletActionUtils.showIconInActionSearch(e)
        val project = e.project
        if (project != null) {
            val projectContext = CircletProjectContext.getInstance(project)
            val projectDescriptions = projectContext.projectDescriptions
            if (projectDescriptions != null) {
                e.presentation.isEnabled = true

                return
            }
        }
        e.presentation.isEnabled = false
    }

    override fun buildAction(it: Pair<CircletProjectDescription, String>): AnAction =
        object : AnAction("Open for ${it.first.projectKey.key} project") {
            override fun actionPerformed(e: AnActionEvent) = BrowserUtil.browse(it.second)
        }

    companion object {
        internal fun getProjectAwareUrls(endpoint: (ProjectLocation) -> Location, context: DataContext): List<Pair<CircletProjectDescription, String>>? {
            val project = context.getData(CommonDataKeys.PROJECT) ?: return null
            val server = circletWorkspace.workspace.value?.client?.server?.removeSuffix("/") ?: return null
            val description = CircletProjectContext.getInstance(project).projectDescriptions ?: return null

            return description.second.map {
                val projectLocation = Navigator.p.project(it.projectKey)
                val url = endpoint(projectLocation).absoluteHref(server)

                it to url
            }.toList()
        }
    }
}

class OpenReviews : CircletOpenInBrowserAction("Code reviews") {
    override fun getData(dataContext: DataContext): List<Pair<CircletProjectDescription, String>>? {
        return getProjectAwareUrls(ProjectLocation::reviews, dataContext)
    }
}

class OpenChecklists : CircletOpenInBrowserAction("Checklists") {
    override fun getData(dataContext: DataContext): List<Pair<CircletProjectDescription, String>>? {
        return getProjectAwareUrls(ProjectLocation::checklists, dataContext)
    }
}

class OpenIssues : CircletOpenInBrowserAction("Issues") {
    override fun getData(dataContext: DataContext): List<Pair<CircletProjectDescription, String>>? {
        return getProjectAwareUrls(ProjectLocation::issues, dataContext)
    }
}

class CircletVcsOpenInBrowserActionGroup :
    CircletOpenInBrowserActionGroup<CircletVcsOpenInBrowserActionGroup.Companion.OpenData>("Open on Space") {

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = (getData(e.dataContext) != null)
    }

    override fun buildAction(it: OpenData): AnAction = OpenAction(it)

    override fun getData(dataContext: DataContext): List<OpenData>? {
        val project = dataContext.getData(CommonDataKeys.PROJECT) ?: return null
        val server = circletWorkspace.workspace.value?.client?.server?.removeSuffix("/") ?: return null

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

        val repoKeysInfo = findProjectInfo(gitRepository, project) ?: return null

        return repoKeysInfo.second.map { OpenData.File(project, server, it, repoKeysInfo.first, virtualFile, gitRepository) }

    }

    private fun getDataFromHistory(dataContext: DataContext, project: Project, server: String): List<OpenData>? {
        val filePath = dataContext.getData(VcsDataKeys.FILE_PATH) ?: return null
        val fileRevision = dataContext.getData(VcsDataKeys.VCS_FILE_REVISION) ?: return null
        if (fileRevision !is VcsFileRevisionEx) return null
        val gitRepository = GitUtil.getRepositoryManager(project).getRepositoryForFileQuick(filePath) ?: return null
        val repoKeysInfo = findProjectInfo(gitRepository, project) ?: return null

        return repoKeysInfo.second.map { OpenData.FileRevision(project, server, it, repoKeysInfo.first, fileRevision) }
    }

    private fun getDataFromLog(dataContext: DataContext, project: Project, server: String): List<OpenData>? {
        val vcsLog = dataContext.getData(VcsLogDataKeys.VCS_LOG) ?: return null
        val selectedCommit = vcsLog.selectedCommits.firstOrNull() ?: return null
        val gitRepository = GitUtil.getRepositoryManager(project).getRepositoryForFileQuick(selectedCommit.root) ?: return null
        val repoKeysInfo = findProjectInfo(gitRepository, project) ?: return null

        return repoKeysInfo.second.map { OpenData.Commit(project, server, it, repoKeysInfo.first, selectedCommit) }
    }

    private fun findProjectInfo(gitRepository: GitRepository, project: Project): Pair<String, List<CircletProjectDescription>>? {
        val circletContext = CircletProjectContext.getInstance(project)

        return getRemoteUrls(gitRepository)
            .mapNotNull { circletContext.findProjectInfo(it) }
            .firstOrNull() ?: return null
    }

    private fun getRemoteUrls(gitRepository: GitRepository): List<String> {
        return gitRepository.remotes.flatMap { it.urls }.toList()
    }

    companion object {
        class OpenAction(private val data: OpenData) : DumbAwareAction("Open for ${data.description.project.name} project") {

            override fun actionPerformed(e: AnActionEvent) {
                data.url?.let { BrowserUtil.browse(it) }
            }
        }

        sealed class OpenData(val project: Project,
                              val server: String,
                              val description: CircletProjectDescription,
                              val repo: String
        ) {
            abstract val url: String?

            class Commit(project: Project,
                         server: String,
                         projectKey: CircletProjectDescription,
                         repo: String,
                         private val commit: CommitId) : OpenData(project, server, projectKey, repo) {

                override val url: String?
                    get() {
                        return Navigator.p.project(description.projectKey)
                            .commits(repo, "", commit.hash.asString())
                            .absoluteHref(server)
                    }
            }

            class FileRevision(project: Project,
                               server: String,
                               projectKey: CircletProjectDescription,
                               repo: String,
                               private val vcsFileRevisionEx: VcsFileRevisionEx) : OpenData(project, server, projectKey, repo) {

                override val url: String?
                    get() {
                        return Navigator.p.project(description.projectKey)
                            .revision(repo, vcsFileRevisionEx.revisionNumber.asString())
                            .absoluteHref(server)
                    }
            }

            class File(project: Project,
                       server: String,
                       projectKey: CircletProjectDescription,
                       repo: String,
                       private val virtualFile: VirtualFile, private val gitRepository: GitRepository) : OpenData(project, server, projectKey, repo) {
                override val url: String?
                    get() {
                        val relativePath = VfsUtilCore.getRelativePath(virtualFile, gitRepository.root) ?: return null
                        val hash = getCurrentFileRevisionHash(project, virtualFile) ?: return null

                        return Navigator.p.project(description.projectKey)
                            .fileAnnotate(repo, hash, relativePath)
                            .absoluteHref(server)
                    }

                private fun getCurrentFileRevisionHash(project: Project, file: VirtualFile): String? {
                    val ref = Ref1<GitRevisionNumber>()
                    object : Task.Modal(project, "Getting Last Revision", true) {
                        override fun run(indicator: ProgressIndicator) {
                            ref.set(GitHistoryUtils.getCurrentRevision(project, VcsUtil.getFilePath(file), "HEAD") as GitRevisionNumber?)
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
