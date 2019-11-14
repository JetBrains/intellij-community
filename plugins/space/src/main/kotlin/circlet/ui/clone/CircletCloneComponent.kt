package circlet.ui.clone

import circlet.client.*
import circlet.client.api.*
import circlet.components.*
import circlet.platform.api.oauth.*
import circlet.platform.client.*
import circlet.settings.*
import circlet.settings.log
import circlet.ui.*
import com.intellij.dvcs.*
import com.intellij.dvcs.repo.*
import com.intellij.dvcs.ui.*
import com.intellij.icons.*
import com.intellij.ide.*
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
import com.intellij.util.containers.*
import com.intellij.util.ui.*
import com.intellij.util.ui.cloneDialog.*
import git4idea.*
import git4idea.checkout.*
import git4idea.commands.*
import libraries.coroutines.extra.*
import runtime.*
import runtime.reactive.*
import java.awt.event.*
import java.nio.file.*
import javax.swing.*
import javax.swing.event.*

internal class CircletCloneComponent(val project: Project,
                                     private val git: Git) : VcsCloneDialogExtensionComponent() {
    // state
    private val uiLifetime = LifetimeSource()

    private var loginState: MutableProperty<CircletLoginState> = mutableProperty(initialState())

    private fun initialState(): CircletLoginState {
        val workspace = circletWorkspace.workspace.value ?: return CircletLoginState.Disconnected("", null)
        return CircletLoginState.Connected(workspace.client.server, workspace)
    }

    private val wrapper: Wrapper = Wrapper()
    private lateinit var cloneView: CloneView

    init {
        this.attachChild(Disposable { uiLifetime.terminate() })

        circletWorkspace.workspace.forEach(uiLifetime) { workspace ->
            if (workspace == null) {
                val settings = CircletServerSettingsComponent.getInstance().settings
                loginState.value = CircletLoginState.Disconnected(settings.value.server, "")
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
                    loginState.value = CircletLoginState.Disconnected(st.server, null)
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
                } catch (th: Throwable) {
                    log.error(th)
                    loginState.value = CircletLoginState.Disconnected(serverName, th.message ?: "error of type ${th.javaClass.simpleName}")
                }
            }
        }
    }

    override fun doClone(checkoutListener: CheckoutProvider.Listener) {
        val url = cloneView.getUrl()
        url ?: return
        val directory = cloneView.getDirectory()
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
                                  git,
                                  checkoutListener,
                                  destinationParent,
                                  url,
                                  directoryName,
                                  parentDirectory)
    }

    override fun onComponentSelected() {
        val isConnected = loginState.value is CircletLoginState.Connected
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
    private val dialogStateListener: VcsCloneDialogComponentStateListener,
    private val st: CircletLoginState.Connected
) {

    val selectedUrl = mutableProperty<String?>(null)

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
    val circletImageLoader = CircletImageLoader(lifetime, client)

    private val circletProjectListWithSearch = ListWithSearchComponent<CircletCloneListItem>(listModel, CircletCloneListItemRenderer())

    private val searchTextField: SearchTextField = circletProjectListWithSearch.searchField

    private val list: JBList<CircletCloneListItem> = circletProjectListWithSearch.list.apply {
        addListSelectionListener {
            if (it.valueIsAdjusting)
                return@addListSelectionListener
            selectedUrl.value = selectedValue?.repoDetails?.value?.urls?.sshUrl
        }
    }

    val accountLabel = JLabel()

    val cloneViewModel = CircletCloneComponentViewModel(
        lifetime,
        st.workspace,
        client.pr,
        client.repoService,
        client.star,
        circletImageLoader
    )

    init {

        selectedUrl.forEach(lifetime) { su ->
            dialogStateListener.onOkActionEnabled(su != null)
            if (su != null) {
                val path = StringUtil.trimEnd(ClonePathProvider.relativeDirectoryPathForVcsUrl(project, su), GitUtil.DOT_GIT)
                directoryField.trySetChildPath(path)
            }
        }

        searchTextField.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                selectedUrl.value = e.document.getText(0, e.document.length)
            }
        })

        cloneViewModel.me.forEach(lifetime) { profile ->
            accountLabel.toolTipText = profile.englishFullName()
        }

        cloneViewModel.repos.elements.forEach(lifetime) { allProjectsWithReposAndDetails ->
            launch(lifetime, Ui) {
                val toAdd = allProjectsWithReposAndDetails.drop(listModel.items.count())
                val selection = circletProjectListWithSearch.list.selectedIndex
                listModel.addAll(listModel.items.count(), toAdd)
                circletProjectListWithSearch.list.selectedIndex = selection
            }
        }

        CircletUserAvatarProvider.getInstance().avatar.forEach(lifetime) { avatarIcon: Icon ->
            accountLabel.icon = resizeIcon(avatarIcon, VcsCloneDialogUiSpec.Components.avatarSize)
        }

        cloneViewModel.isLoading.forEach(lifetime, list::setPaintBusy)

        accountLabel.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent?) {
                showPopupMenu()
            }

            private fun showPopupMenu() {
                val serverUrl = cleanupUrl(st.server)
                val menuItems: MutableList<AccountMenuItem> = mutableListOf()
                menuItems += AccountMenuItem.Account(
                    st.workspace.me.value.englishFullName(),
                    serverUrl,
                    resizeIcon(accountLabel.icon, VcsCloneDialogUiSpec.Components.popupMenuAvatarSize))
                menuItems += AccountMenuItem.Action("Open $serverUrl",
                                                    { BrowserUtil.browse(st.server) },
                                                    AllIcons.Ide.External_link_arrow,
                                                    showSeparatorAbove = true)
                menuItems += AccountMenuItem.Action("Projects",
                                                    { BrowserUtil.browse("${st.server.removeSuffix("/")}/p") },
                                                    AllIcons.Ide.External_link_arrow)
                menuItems += AccountMenuItem.Action("Log Out...", { circletWorkspace.signOut() }, showSeparatorAbove = true)

                AccountsMenuListPopup(null, AccountMenuPopupStep(menuItems))
                    .showUnderneathOf(accountLabel)
            }
        })
    }

    fun getView(): DialogPanel {
        return panel {
            val gapLeft = JBUI.scale(VcsCloneDialogUiSpec.Components.innerHorizontalGap)
            row {
                cell(isFullWidth = true) {
                    searchTextField.textEditor(pushX, growX)
                    JSeparator(JSeparator.VERTICAL)(growY, gapLeft = gapLeft)
                    accountLabel(gapLeft = gapLeft)
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
        }
    }

    private fun bindScroll(scrollableList: JScrollPane) {
        val slVisibility = SequentialLifetimes(lifetime)

        lateinit var scrollUpdater: (force : Boolean) -> Unit

        scrollUpdater = { force ->

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
        scrollableList.verticalScrollBar.addAdjustmentListener {
            scrollUpdater(false)
        }
    }

    fun getUrl(): String? = selectedUrl.value

    fun getDirectory(): String = directoryField.text

    fun doValidteAll(): List<ValidationInfo> {
        val list = ArrayList<ValidationInfo>()
        ContainerUtil.addIfNotNull(list, CloneDvcsValidationUtils.checkDirectory(directoryField.text, directoryField.textField))
        ContainerUtil.addIfNotNull(list, CloneDvcsValidationUtils.createDestination(directoryField.text))
        return list
    }
}
