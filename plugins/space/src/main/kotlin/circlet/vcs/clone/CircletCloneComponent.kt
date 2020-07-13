package circlet.vcs.clone

import circlet.client.api.Navigator
import circlet.client.api.englishFullName
import circlet.components.CircletUserAvatarProvider
import circlet.components.circletWorkspace
import circlet.platform.api.oauth.OAuthTokenResponse
import circlet.platform.client.KCircletClient
import circlet.settings.*
import circlet.ui.*
import circlet.vcs.CircletHttpPasswordState
import circlet.vcs.CircletKeysState
import circlet.vcs.CircletSetGitHttpPasswordDialog
import com.intellij.dvcs.DvcsRememberedInputs
import com.intellij.dvcs.repo.ClonePathProvider
import com.intellij.dvcs.ui.CloneDvcsValidationUtils
import com.intellij.dvcs.ui.DvcsBundle
import com.intellij.dvcs.ui.SelectChildTextFieldWithBrowseButton
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.CheckoutProvider
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogComponentStateListener
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogExtensionComponent
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.wm.IdeFrame
import com.intellij.ui.*
import com.intellij.ui.components.JBList
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.layout.*
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.cloneDialog.ListWithSearchComponent
import com.intellij.util.ui.cloneDialog.VcsCloneDialogUiSpec
import git4idea.GitUtil
import git4idea.checkout.GitCheckoutProvider
import git4idea.commands.Git
import libraries.coroutines.extra.*
import runtime.Ui
import runtime.reactive.MutableProperty
import runtime.reactive.SequentialLifetimes
import runtime.reactive.mutableProperty
import runtime.reactive.view
import java.awt.event.AdjustmentEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.nio.file.Paths
import java.util.concurrent.CancellationException
import javax.swing.*
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

internal class CircletCloneComponent(val project: Project) : VcsCloneDialogExtensionComponent() {
    // state
    private val uiLifetime = LifetimeSource()

    private var loginState: MutableProperty<CircletLoginState> = mutableProperty(initialState())

    private fun initialState(): CircletLoginState {
        val workspace = circletWorkspace.workspace.value ?: return CircletLoginState.Disconnected("")
        return CircletLoginState.Connected(workspace.client.server, workspace)
    }

    private val wrapper: Wrapper = Wrapper()
    private lateinit var cloneView: CloneView

    init {
        Disposer.register(this, Disposable { uiLifetime.terminate() })

        circletWorkspace.workspace.forEach(uiLifetime) { workspace ->
            if (workspace == null) {
                val settings = CircletSettings.getInstance()
                loginState.value = CircletLoginState.Disconnected(settings.serverSettings.server)
            }
            else {
                loginState.value = CircletLoginState.Connected(workspace.client.server, workspace)
            }
        }

        loginState.view(uiLifetime) { lt, st ->
            val view = createView(lt, st)
            view.border = JBUI.Borders.empty(8, 12)
            wrapper.setContent(view)
            wrapper.repaint()
        }
    }


    private fun createView(lifetime: Lifetime, st: CircletLoginState): JComponent {
        dialogStateListener.onOkActionEnabled(false)
        return when (st) {
            is CircletLoginState.Connected -> {
                dialogStateListener.onListItemChanged()
                cloneView = CloneView(lifetime, project, dialogStateListener, st)
                cloneView.getView()
            }

            is CircletLoginState.Connecting -> {
                buildConnectingPanel(st) {
                    st.lt.terminate()
                    loginState.value = CircletLoginState.Disconnected(st.server)
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
                    loginState.value = CircletLoginState.Connecting(serverName, connectLt)
                    when (val response = circletWorkspace.signIn(connectLt, serverName)) {
                        is OAuthTokenResponse.Error -> {
                            loginState.value = CircletLoginState.Disconnected(serverName, response.description)
                        }
                    }
                } catch (th: CancellationException) {
                    throw th
                } catch (th: Throwable) {
                    log.warn(th)
                    loginState.value = CircletLoginState.Disconnected(serverName, th.message ?: "error of type ${th.javaClass.simpleName}")
                }
                val frame = SwingUtilities.getAncestorOfClass(JFrame::class.java, getView())
                AppIcon.getInstance().requestFocus(frame as IdeFrame?)
            }
        }
    }

    override fun doClone(checkoutListener: CheckoutProvider.Listener) {
        cloneView.doClone(checkoutListener)
    }

    override fun onComponentSelected() {
        val isConnected = loginState.value is CircletLoginState.Connected
        dialogStateListener.onOkActionNameChanged("Clone")
        dialogStateListener.onOkActionEnabled(isConnected && cloneView.getUrl() != null)
    }

    override fun doValidateAll(): List<ValidationInfo> {
        return cloneView.doValidteAll()
    }

    override fun getView(): Wrapper = wrapper
}

private class CloneView(
    private val lifetime: Lifetime,
    private val project: Project,
    dialogStateListener: VcsCloneDialogComponentStateListener,
    private val st: CircletLoginState.Connected
) {
    val settings = CircletSettings.getInstance()

    val listModel: CollectionListModel<CircletCloneListItem> = object : CollectionListModel<CircletCloneListItem>() {
        init {
            addListDataListener(object : ListDataListener {
                override fun contentsChanged(e: ListDataEvent?) = Unit
                override fun intervalRemoved(e: ListDataEvent?) = Unit

                override fun intervalAdded(e: ListDataEvent?) {
                    e?.let { event ->
                        (event.index0..event.index1).forEach { item ->
                            getElementAt(item).repoDetails.forEach(lifetime) { details ->
                                if (details != null) {
                                    val selection = circletProjectListWithSearch.list.selectedIndex
                                    fireContentsChanged(this, item, item)
                                    circletProjectListWithSearch.list.selectedIndex = selection
                                }
                            }
                        }
                    }
                }
            })
        }
    }

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

    val client: KCircletClient = st.workspace.client

    private val circletProjectListWithSearch = ListWithSearchComponent<CircletCloneListItem>(listModel, CircletCloneListItemRenderer())

    private val searchTextField: SearchTextField = circletProjectListWithSearch.searchField

    private val list: JBList<CircletCloneListItem> = circletProjectListWithSearch.list.apply {
        addListSelectionListener {
            if (it.valueIsAdjusting)
                return@addListSelectionListener
            // selection change is triggered when repo details update, so we can use value here.
            updateSelectedUrl()
        }
        setExpandableItemsEnabled(false)
    }

    val accountLabel = JLabel()

    private val passwordStatus: SimpleColoredComponent = SimpleColoredComponent().apply {
        isVisible = false
    }

    val cloneViewModel = CircletCloneComponentViewModel(lifetime, st.workspace)

    private val linkLabel: LinkLabel<*> = LinkLabel.create("Set password...", null).apply {
        horizontalTextPosition = SwingConstants.LEFT
        iconTextGap = 0
    }

    var createDirectoryError: ValidationInfo? = null

    init {
        cloneViewModel.readyToClone.forEach(lifetime, dialogStateListener::onOkActionEnabled)

        cloneViewModel.selectedUrl.forEach(lifetime) { su ->
            su ?: return@forEach
            val path = StringUtil.trimEnd(ClonePathProvider.relativeDirectoryPathForVcsUrl(project, su), GitUtil.DOT_GIT)
            directoryField.trySetChildPath(path)
        }

        cloneViewModel.me.forEach(lifetime) { profile ->
            accountLabel.toolTipText = profile.englishFullName()
        }

        cloneViewModel.repos.elements.forEach(lifetime) { allProjectsWithReposAndDetails ->
            launch(lifetime, Ui) {
                val allRepos = allProjectsWithReposAndDetails.filterNotNull()
                val toAdd = allRepos.drop(listModel.items.count())
                val selection = circletProjectListWithSearch.list.selectedIndex
                listModel.addAll(listModel.items.count(), toAdd)
                circletProjectListWithSearch.list.selectedIndex = selection
            }
        }

        CircletUserAvatarProvider.getInstance().avatars.forEach(lifetime) { avatars ->
            accountLabel.icon = resizeIcon(avatars.circle, VcsCloneDialogUiSpec.Components.avatarSize)
        }

        cloneViewModel.isLoading.forEach(lifetime, list::setPaintBusy)

        cloneViewModel.circletHttpPasswordState.forEach(lifetime) {
            if (cloneViewModel.cloneType.value == CloneType.HTTP) {
                passwordStatus.clear()
                passwordStatus.append("Git HTTP password not set", SimpleTextAttributes.ERROR_ATTRIBUTES)
                linkLabel.setListener({ _, _ -> setGitHttpPassword() }, null)
                linkLabel.text = "Set Git HTTP password..."
                linkLabel.icon = null

                passwordStatus.isVisible = it is CircletHttpPasswordState.NotSet
                linkLabel.isVisible = it is CircletHttpPasswordState.NotSet
            }
        }

        cloneViewModel.circletKeysState.forEach(lifetime) {
            if (cloneViewModel.cloneType.value == CloneType.SSH) {
                passwordStatus.clear()
                passwordStatus.append("SSH keys are not configured", SimpleTextAttributes.ERROR_ATTRIBUTES)
                linkLabel.setListener({ _, _ -> openSshKeysPage() }, null)
                linkLabel.text = "Configure..."
                linkLabel.icon = AllIcons.Ide.External_link_arrow

                passwordStatus.isVisible = it is CircletKeysState.NotSet
                linkLabel.isVisible = it is CircletKeysState.NotSet
            }
        }

        accountLabel.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent?) {
                showPopupMenu()
            }

            private fun showPopupMenu() {
                val host = st.server
                val serverUrl = cleanupUrl(st.server)
                val menuItems: MutableList<AccountMenuItem> = mutableListOf()
                menuItems += AccountMenuItem.Account(st.workspace.me.value.englishFullName(),
                                                     serverUrl,
                                                     resizeIcon(CircletUserAvatarProvider.getInstance().avatars.value.circle,
                                                                VcsCloneDialogUiSpec.Components.popupMenuAvatarSize),
                                                     listOf(browseAction("Open $serverUrl", host)))
                menuItems += browseAction("Projects", Navigator.p.absoluteHref(host), true)
                menuItems += AccountMenuItem.Action("Settings...",
                                                    {
                                                        CircletSettingsPanel.openSettings(project)
                                                        updateSelectedUrl()
                                                    },
                                                    showSeparatorAbove = true)
                menuItems += AccountMenuItem.Action("Log Out...", { circletWorkspace.signOut() })

                AccountsMenuListPopup(null, AccountMenuPopupStep(menuItems))
                    .showUnderneathOf(accountLabel)
            }
        })
    }

    private fun setGitHttpPassword() {
        val dialog = CircletSetGitHttpPasswordDialog(st.workspace.me.value, client)
        if (dialog.showAndGet()) {
            cloneViewModel.circletHttpPasswordState.value = dialog.result
        }
    }

    private fun openSshKeysPage() {
        val profile = st.workspace.me.value
        val gitConfigPage = Navigator.m.member(profile.username).git.absoluteHref(st.server)
        BrowserUtil.browse(gitConfigPage)
    }

    fun getView(): DialogPanel {
        return panel {
            row {
                cell(isFullWidth = true) {
                    searchTextField.textEditor(pushX, growX)
                    JSeparator(JSeparator.VERTICAL)(growY)
                    accountLabel()
                }
            }
            row {
                val scrollableList = ScrollPaneFactory.createScrollPane(
                    list,
                    ScrollPaneFactory.VERTICAL_SCROLLBAR_AS_NEEDED,
                    ScrollPaneFactory.HORIZONTAL_SCROLLBAR_NEVER)

                bindScroll(scrollableList)

                scrollableList(push, grow)
            }
            row("Directory:") {
                directoryField(growX, pushX)
            }
            row {
                cell(isFullWidth = true) {
                    passwordStatus()
                    linkLabel()
                }
            }
        }
    }

    private fun bindScroll(scrollableList: JScrollPane) {
        val slVisibility = SequentialLifetimes(lifetime)

        lateinit var scrollUpdater: (force: Boolean) -> Unit

        scrollUpdater = { force ->
            if (!lifetime.isTerminated) {
                // run element visibility updater, tracks elements in a view port and set visible to true.
                launch(slVisibility.next(), Ui) {
                    delay(300)
                    while (cloneViewModel.isLoading.value) {
                        delay(300)
                    }
                    val first = list.firstVisibleIndex
                    val last = list.lastVisibleIndex
                    if (first >= 0 && last >= 0) {
                        (first..last).forEach {
                            val el = list.model.getElementAt(it)
                            el.visible.value = true
                        }
                    }
                }
                val last = list.lastVisibleIndex
                if (force || !cloneViewModel.isLoading.value) {
                    if ((last == -1 || list.model.size < last + 10) && cloneViewModel.repos.hasMore.value) {
                        cloneViewModel.isLoading.value = true
                        launch(lifetime, Ui) {
                            cloneViewModel.repos.more()
                            scrollUpdater(true)
                        }
                    }
                    else {
                        cloneViewModel.isLoading.value = false
                    }
                }
            }
        }
        val listener: (e: AdjustmentEvent) -> Unit = {
            scrollUpdater(false)
        }

        scrollableList.verticalScrollBar.addAdjustmentListener(listener)
        lifetime.add {
            scrollableList.verticalScrollBar.removeAdjustmentListener(listener)
        }
    }

    fun updateSelectedUrl() {
        cloneViewModel.cloneType.value = settings.cloneType

        val repositoryUrls = list.selectedValue?.repoDetails?.value?.urls
        cloneViewModel.selectedUrl.value = when (settings.cloneType) {
            CloneType.SSH -> repositoryUrls?.sshUrl
            CloneType.HTTP -> repositoryUrls?.httpUrl
        }
    }

    fun getUrl(): String? = cloneViewModel.selectedUrl.value

    fun getDirectory(): String = directoryField.text

    fun doValidteAll(): List<ValidationInfo> {
        val list = ArrayList<ValidationInfo>()
        ContainerUtil.addIfNotNull(list, CloneDvcsValidationUtils.checkDirectory(directoryField.text, directoryField.textField))
        return list
    }

    fun doClone(checkoutListener: CheckoutProvider.Listener) {
        val url = getUrl()
        url ?: return
        val directory = getDirectory()

        createDirectoryError = CloneDvcsValidationUtils.createDestination(directory)
        if (createDirectoryError != null) {
            return
        }

        val parent = Paths.get(directory).toAbsolutePath().parent
        val lfs = LocalFileSystem.getInstance()
        var destinationParent = lfs.findFileByIoFile(parent.toFile())
        if (destinationParent == null) {
            destinationParent = lfs.refreshAndFindFileByIoFile(parent.toFile())
        }
        destinationParent ?: return
        val directoryName = Paths.get(directory).fileName.toString()
        val parentDirectory = parent.toAbsolutePath().toString()
        GitCheckoutProvider.clone(project,
                                  Git.getInstance(),
                                  checkoutListener,
                                  destinationParent,
                                  url,
                                  directoryName,
                                  parentDirectory)
    }
}
