package circlet.ui.clone

import circlet.client.*
import circlet.client.api.*
import circlet.components.*
import circlet.platform.api.oauth.*
import circlet.platform.client.*
import circlet.projects.*
import circlet.runtime.*
import circlet.settings.*
import com.intellij.dvcs.*
import com.intellij.dvcs.repo.*
import com.intellij.dvcs.ui.*
import com.intellij.openapi.*
import com.intellij.openapi.fileChooser.*
import com.intellij.openapi.project.*
import com.intellij.openapi.rd.*
import com.intellij.openapi.ui.*
import com.intellij.openapi.util.text.*
import com.intellij.openapi.vcs.*
import com.intellij.openapi.vcs.ui.cloneDialog.*
import com.intellij.openapi.vfs.*
import com.intellij.ui.*
import com.intellij.ui.components.*
import com.intellij.ui.components.panels.*
import com.intellij.ui.layout.*
import com.intellij.ui.speedSearch.*
import com.intellij.util.ui.*
import git4idea.*
import git4idea.checkout.*
import git4idea.commands.*
import libraries.coroutines.extra.*
import runtime.*
import runtime.reactive.*
import java.nio.file.*
import java.util.*
import javax.swing.*
import javax.swing.event.*
import kotlin.properties.*

internal class CircletCloneComponent(val project: Project,
                                     private val checkoutListener: CheckoutProvider.Listener,
                                     private val git: Git) : VcsCloneDialogExtensionComponent() {
    // temp local configuration
    private val showProjectWithoutRepositories: Boolean = false

    // state
    private val uiLifetime: LifetimeSource = LifetimeSource()

    private var state = mutableProperty(initialState())

    private fun initialState(): CircletLoginState {
        val workspace = circletWorkspace.workspace.value ?: return CircletLoginState.Disconnected("", null)
        return CircletLoginState.Connected(workspace.client.server, workspace)
    }

    private var selectedUrl: String? by Delegates.observable<String?>(null) { _, _, _ ->
        onSelectedUrlChanged()
    }
    private val allProjects: LinkedHashSet<PR_ProjectRepos> = linkedSetOf()
    private val reposInfo: MutableMap<PR_RepositoryInfo, RepoDetails> = mutableMapOf()

    // api
    private lateinit var projectsVM: ProjectsListViewVM

    // UI
    private val directoryField: SelectChildTextFieldWithBrowseButton = SelectChildTextFieldWithBrowseButton(
        ClonePathProvider.defaultParentDirectoryPath(project, DvcsRememberedInputs())).apply {
        val fcd = FileChooserDescriptorFactory.createSingleFolderDescriptor()
        fcd.isShowFileSystemRoots = true
        fcd.isHideIgnored = false
        addBrowseFolderListener(DvcsBundle.getString("clone.destination.directory.browser.title"),
                                DvcsBundle.getString("clone.destination.directory.browser.description"),
                                project,
                                fcd)
    }

    private val wrapper: Wrapper = Wrapper()

    private val searchTextField: SearchTextField = SearchTextField(false).apply {
        textEditor.emptyText.appendText("Search repository")
    }

    private val listModel: CollectionListModel<CircletCloneListItem> = CollectionListModel()
    private val list: JBList<CircletCloneListItem> = JBList(listModel).apply {
        cellRenderer = CircletCloneListItemRenderer()
        ScrollingUtil.installActions(this)
        addListSelectionListener {
            if (it.valueIsAdjusting) return@addListSelectionListener
            val selected = selectedValue
            if (selected == null) {
                selectedUrl = null
                return@addListSelectionListener
            }
            selectedUrl = selected.repoDetails.urls.sshUrl
        }
    }

    init {
        this.attachChild(Disposable { uiLifetime.terminate() })

        val speedSearch = SpeedSearch()
        val filteringListModel = NameFilteringListModel<CircletCloneListItem>(listModel,
                                                                              { it.repoInfo.name },
                                                                              speedSearch::shouldBeShowing,
                                                                              { speedSearch.filter ?: "" })
        filteringListModel.setFilter {
            it != null && speedSearch.shouldBeShowing(it.repoInfo.name)
        }
        list.model = filteringListModel
        searchTextField.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                speedSearch.updatePattern(searchTextField.text)
                filteringListModel.refilter()
                onSelectedUrlChanged()
            }
        })

        val settings = CircletServerSettingsComponent.getInstance().settings
        circletWorkspace.workspace.forEach(uiLifetime) { workspace ->
            if (workspace == null) {
                state.value = CircletLoginState.Disconnected(settings.value.server, "")
            }
            else {
                state.value = CircletLoginState.Connected(workspace.client.server, workspace)
            }
        }

        state.forEach(uiLifetime) { st ->
            val view = createView(st)
            view.border = JBUI.Borders.empty(UIUtil.REGULAR_PANEL_TOP_BOTTOM_INSET, UIUtil.REGULAR_PANEL_LEFT_RIGHT_INSET)
            wrapper.setContent(view)
            wrapper.repaint()
        }
    }

    private fun createView(st: CircletLoginState): JComponent {
        dialogStateListener.onOkActionEnabled(false)
        return when (st) {
            is CircletLoginState.Connected -> {
                dialogStateListener.onListItemChanged()
                val repositoriesPanel = panel {
                    row {
                        cell(isFullWidth = true) {
                            searchTextField.textEditor(pushX, growX)
                        }
                    }
                    row {
                        val scrollableList = ScrollPaneFactory.createScrollPane(list,
                                                                                ScrollPaneFactory.VERTICAL_SCROLLBAR_AS_NEEDED,
                                                                                ScrollPaneFactory.HORIZONTAL_SCROLLBAR_NEVER)
                        scrollableList(push, grow)
                    }
                    row("Directory:") {
                        directoryField(growX, pushX)
                    }
                }

                searchTextField.addDocumentListener(object : DocumentAdapter() {
                    override fun textChanged(event: DocumentEvent) {
                    }
                })

                val client: KCircletClient = st.workspace.client
                projectsVM = ProjectsListViewVM(null,
                                                client.pr,
                                                client.repoService,
                                                client.star,
                                                client,
                                                uiLifetime)
                list.setPaintBusy(true)

                projectsVM.starredProjectsWithRepos.forEach(uiLifetime) { listPrRepos ->
                    if (listPrRepos == null) return@forEach
                    launch(uiLifetime, ApplicationUiDispatch.coroutineContext) {
                        listPrRepos.forEach { addProjectWithRepos(it, true) }
                        loadAllProject()
                    }
                }

                repositoriesPanel
            }
            is CircletLoginState.Connecting -> {
                buildConnectingPanel(st) {
                    st.lt.terminate()
                    state.value = CircletLoginState.Disconnected(st.server, null)
                }
            }
            is CircletLoginState.Disconnected -> {
                buildLoginPanel(st) { serverName ->
                    login(serverName)
                }
            }
        }
    }

    private fun login(serverName: String) {
        launch(uiLifetime, Ui) {
            uiLifetime.usingSource { connectLt ->
                try {
                    state.value = CircletLoginState.Connecting(serverName, connectLt)
                    when (val response = circletWorkspace.signIn(connectLt, serverName)) {
                        is OAuthTokenResponse.Error -> {
                            state.value = CircletLoginState.Disconnected(serverName, response.description)
                        }
                    }
                }
                catch (th: Throwable) {
                    circlet.settings.log.error(th)
                    state.value = CircletLoginState.Disconnected(serverName, th.message ?: "error of type ${th.javaClass.simpleName}")
                }
            }
        }
    }

    private fun loadAllProject() {
        projectsVM.allProjectsWithReposFlux.forEach(uiLifetime) { it ->
            if (it == null) return@forEach
            it.hasMore.forEach(uiLifetime) { list.setPaintBusy(it) }

            it.elements.forEach(uiLifetime) { prRepoList ->
                launch(uiLifetime, ApplicationUiDispatch.coroutineContext) {
                    prRepoList.list.forEach { prRepo -> addProjectWithRepos(prRepo, false) }
                    it.more()
                }
            }
        }
    }

    private suspend fun addProjectWithRepos(prRepo: PR_ProjectRepos, starred: Boolean) {
        if (prRepo.repos.isEmpty() && !showProjectWithoutRepositories) return
        if (allProjects.add(prRepo)) {
            prRepo.repos.forEach {
                val details = reposInfo.getOrPut(it) { projectsVM.repo.repositoryDetails(prRepo.project.key, it.name) }
                listModel.add(CircletCloneListItem(prRepo.project, starred, it, details))
            }
        }
    }

    override fun doClone() {
        selectedUrl ?: return
        val parent = Paths.get(directoryField.text).toAbsolutePath().parent
        val lfs = LocalFileSystem.getInstance()
        var destinationParent = lfs.findFileByIoFile(parent.toFile())
        if (destinationParent == null) {
            destinationParent = lfs.refreshAndFindFileByIoFile(parent.toFile())
        }
        destinationParent ?: return
        val directoryName = Paths.get(directoryField.text).fileName.toString()
        val parentDirectory = parent.toAbsolutePath().toString()
        GitCheckoutProvider.clone(project,
                                  git,
                                  checkoutListener,
                                  destinationParent,
                                  selectedUrl,
                                  directoryName,
                                  parentDirectory)
    }

    override fun onComponentSelected() {
        onSelectedUrlChanged()
    }

    override fun doValidateAll(): List<ValidationInfo> = emptyList() // todo: add validation

    override fun getView(): Wrapper = wrapper

    private fun onSelectedUrlChanged() {
        val urlSelected = selectedUrl != null
        dialogStateListener.onOkActionEnabled(urlSelected)
        directoryField.isEnabled = urlSelected
        if (urlSelected) {
            val path = StringUtil.trimEnd(ClonePathProvider.relativeDirectoryPathForVcsUrl(project, selectedUrl!!), GitUtil.DOT_GIT)
            directoryField.trySetChildPath(path)
        }
    }
}
