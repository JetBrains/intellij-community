package circlet.ui.clone

import circlet.client.*
import circlet.client.api.*
import circlet.components.*
import circlet.platform.api.oauth.*
import circlet.platform.client.*
import circlet.runtime.*
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
import kotlin.properties.*

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
    private val uiLifetime: Lifetime,
    private val project: Project,
    private val dialogStateListener: VcsCloneDialogComponentStateListener,
    private val st: CircletLoginState.Connected) {
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

    private var selectedUrl: String? by Delegates.observable<String?>(null) { _, _, _ ->
        onSelectedUrlChanged()
    }

    private val listModel: CollectionListModel<CircletCloneListItem> = object : CollectionListModel<CircletCloneListItem>() {
        override fun add(element: CircletCloneListItem) {
            val index = size
            super.add(element)
            element.repoDetails.forEach(uiLifetime) { details ->
                val selectedIndex = list.selectedIndex
                details ?: return@forEach
                fireContentsChanged(this, index, index)
                list.selectedIndex = selectedIndex
            }
        }

        override fun addAll(index: Int, elements: MutableList<out CircletCloneListItem>) {
            super.addAll(index, elements)
            elements.forEachIndexed() { i, element ->
                element.repoDetails.forEach(uiLifetime) { details ->
                    fireContentsChanged(this, index + i, index + i)
                }
            }
        }
    }
    val client: KCircletClient = st.workspace.client
    val circletImageLoader = CircletImageLoader(uiLifetime, client)

    private val circletProjectListWithSearch: ListWithSearchComponent<CircletCloneListItem> =
        ListWithSearchComponent<CircletCloneListItem>(listModel, CircletCloneListItemRenderer())

    private val searchTextField: SearchTextField = circletProjectListWithSearch.searchField
    private val list: JBList<CircletCloneListItem> = circletProjectListWithSearch.list.apply {
        addListSelectionListener {
            if (it.valueIsAdjusting) return@addListSelectionListener
            val selected = selectedValue
            if (selected == null) {
                selectedUrl = null
                return@addListSelectionListener
            }
            selectedUrl = selected.repoDetails.value?.urls?.sshUrl
        }
    }

    val accountLabel = JLabel()

    val cloneViewModel = CircletCloneComponentViewModel(
        uiLifetime,
        st.workspace,
        client.pr,
        client.repoService,
        client.star,
        circletImageLoader
    )

    init {

        searchTextField.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                onSelectedUrlChanged()
            }
        })



        cloneViewModel.me.forEach(uiLifetime) { profile ->
            accountLabel.toolTipText = profile.englishFullName()
        }

        cloneViewModel.repos.elements.forEach(uiLifetime) { allProjectsWithReposAndDetails ->
            launch(uiLifetime, ApplicationUiDispatch.coroutineContext) {
                listModel.addAll(listModel.items.count(), allProjectsWithReposAndDetails.drop(listModel.items.count()))
            }
        }

        CircletUserAvatarProvider.getInstance().avatar.forEach(uiLifetime) { avatarIcon: Icon ->
            accountLabel.icon = resizeIcon(avatarIcon, VcsCloneDialogUiSpec.Components.avatarSize)
        }

        cloneViewModel.isLoading.forEach(uiLifetime, list::setPaintBusy)

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

    private fun onSelectedUrlChanged() {
        val urlSelected = selectedUrl != null
        dialogStateListener.onOkActionEnabled(urlSelected)
        if (urlSelected) {
            val path = StringUtil.trimEnd(ClonePathProvider.relativeDirectoryPathForVcsUrl(project, selectedUrl!!), GitUtil.DOT_GIT)
            directoryField.trySetChildPath(path)
        }
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


                var pro = false

                val sl1 = SequentialLifetimes(uiLifetime)
                val sl2 = SequentialLifetimes(uiLifetime)

                lateinit var scrollUpdater: () -> Unit
                scrollUpdater = {
                    launch(sl1.next(), Ui) {
                        delay(300)
                        while (pro) {
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
                    launch(sl2.next(), Ui) {
                        val last = list.lastVisibleIndex
                        if (!pro) {
                            if (list.model.size < last + 10 && cloneViewModel.repos.hasMore.value) {
                                pro = true
                                launch(uiLifetime, Ui) {
                                    cloneViewModel.repos.more()
                                    pro = false
                                    scrollUpdater()
                                }
                            }
                        }
                    }
                }
                scrollableList.verticalScrollBar.addAdjustmentListener {
                    scrollUpdater()
                }
                scrollableList(push, grow)
            }
            row("Directory:") {
                directoryField(growX, pushX)
            }
        }
    }

    fun getUrl(): String? = selectedUrl

    fun getDirectory(): String = directoryField.text

    fun doValidteAll(): List<ValidationInfo> {
        val list = ArrayList<ValidationInfo>()
        ContainerUtil.addIfNotNull(list, CloneDvcsValidationUtils.checkDirectory(directoryField.text, directoryField.textField))
        ContainerUtil.addIfNotNull(list, CloneDvcsValidationUtils.createDestination(directoryField.text))
        return list
    }
}
