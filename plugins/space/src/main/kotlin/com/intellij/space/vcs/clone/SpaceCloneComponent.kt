// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.clone

import circlet.client.api.englishFullName
import circlet.platform.client.BatchResult
import circlet.platform.client.KCircletClient
import com.intellij.dvcs.DvcsRememberedInputs
import com.intellij.dvcs.repo.ClonePathProvider
import com.intellij.dvcs.ui.CloneDvcsValidationUtils
import com.intellij.dvcs.ui.DvcsBundle
import com.intellij.dvcs.ui.SelectChildTextFieldWithBrowseButton
import com.intellij.execution.process.ProcessIOExecutorService
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.ide.ui.fullRow
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.CheckoutProvider
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogComponentStateListener
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogExtensionComponent
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.space.components.SpaceUserAvatarProvider
import com.intellij.space.components.SpaceWorkspaceComponent
import com.intellij.space.messages.SpaceBundle
import com.intellij.space.promo.bigPromoBanner
import com.intellij.space.promo.promoPanel
import com.intellij.space.settings.CloneType
import com.intellij.space.settings.SpaceLoginState
import com.intellij.space.settings.SpaceSettings
import com.intellij.space.settings.SpaceSettingsPanel
import com.intellij.space.stats.SpaceStatsCounterCollector
import com.intellij.space.ui.*
import com.intellij.space.ui.LoginComponents.buildConnectingPanel
import com.intellij.space.ui.LoginComponents.loginPanel
import com.intellij.space.utils.SpaceUrls
import com.intellij.space.vcs.SpaceHttpPasswordState
import com.intellij.space.vcs.SpaceKeysState
import com.intellij.space.vcs.SpaceSetGitHttpPasswordDialog
import com.intellij.ui.*
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBList
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.layout.*
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.cloneDialog.ListWithSearchComponent
import com.intellij.util.ui.cloneDialog.VcsCloneDialogUiSpec
import git4idea.GitUtil
import git4idea.checkout.GitCheckoutProvider
import git4idea.commands.Git
import kotlinx.coroutines.asCoroutineDispatcher
import libraries.coroutines.extra.*
import runtime.Ui
import runtime.reactive.SequentialLifetimes
import java.awt.event.AdjustmentEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.nio.file.Paths
import javax.swing.*
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

internal class SpaceCloneComponent(val project: Project) : VcsCloneDialogExtensionComponent() {
  // state
  private val uiLifetime = LifetimeSource()

  private val wrapper: Wrapper = Wrapper()
  private lateinit var cloneView: CloneView

  init {
    Disposer.register(this, Disposable { uiLifetime.terminate() })

    val workspace = SpaceWorkspaceComponent.getInstance()
    SpaceStatsCounterCollector.OPEN_SPACE_CLONE_TAB.log(SpaceStatsCounterCollector.LoginState.convert(workspace.loginState.value))

    workspace.loginState.forEach(uiLifetime) { st ->
      val view = createView(uiLifetime, st)
      wrapper.setContent(view)
      wrapper.repaint()
    }
  }


  private fun createView(lifetime: Lifetime, st: SpaceLoginState): JComponent {
    dialogStateListener.onOkActionEnabled(false)
    return when (st) {
      is SpaceLoginState.Connected -> {
        dialogStateListener.onListItemChanged()
        cloneView = CloneView(lifetime, project, dialogStateListener, st)
        cloneView.getView()
      }

      is SpaceLoginState.Connecting -> buildConnectingPanel(st, SpaceStatsCounterCollector.LoginPlace.CLONE) {
        st.cancel()
      }

      is SpaceLoginState.Disconnected -> buildCloneLoginPanel(st) { serverName ->
        SpaceWorkspaceComponent.getInstance().signInManually(serverName, lifetime, getView())
      }
    }.let { view ->
      UIUtil.addInsets(view, UIUtil.PANEL_REGULAR_INSETS)
      if (st is SpaceLoginState.Disconnected) ScrollPaneFactory.createScrollPane(view, true) else view
    }
  }

  private fun buildCloneLoginPanel(st: SpaceLoginState.Disconnected, loginAction: (String) -> Unit): JComponent {
    return panel {
      loginPanel(st, SpaceStatsCounterCollector.LoginPlace.CLONE, isLoginActionDefault = true) {
        loginAction(it)
      }

      promoPanel(SpaceStatsCounterCollector.ExplorePlace.CLONE)

      bigPromoBanner(SpaceStatsCounterCollector.OverviewPlace.CLONE)?.let {
        fullRow { it() }
      }
    }
  }

  override fun doClone(checkoutListener: CheckoutProvider.Listener) {
    SpaceStatsCounterCollector.CLONE_REPO.log()
    cloneView.doClone(checkoutListener)
  }

  override fun onComponentSelected() {
    val isConnected = SpaceWorkspaceComponent.getInstance().loginState.value is SpaceLoginState.Connected
    dialogStateListener.onOkActionNameChanged(DvcsBundle.message("clone.button"))
    dialogStateListener.onOkActionEnabled(isConnected && cloneView.getSelectedListItem() != null)
  }

  override fun doValidateAll(): List<ValidationInfo> {
    return cloneView.doValidateAll()
  }

  override fun getView(): Wrapper = wrapper

  override fun getPreferredFocusedComponent(): JComponent = wrapper
}

private class CloneView(
  private val lifetime: Lifetime,
  private val project: Project,
  dialogStateListener: VcsCloneDialogComponentStateListener,
  private val st: SpaceLoginState.Connected
) {
  val settings = SpaceSettings.getInstance()

  val listModel: CollectionListModel<SpaceCloneListItem> = object : CollectionListModel<SpaceCloneListItem>() {
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
    addBrowseFolderListener(DvcsBundle.message("clone.destination.directory.browser.title"),
                            DvcsBundle.message("clone.destination.directory.browser.description"),
                            project,
                            fcd)
  }

  val client: KCircletClient = st.workspace.client

  private val circletProjectListWithSearch = ListWithSearchComponent<SpaceCloneListItem>(listModel, SpaceCloneListItemRenderer())

  private val searchTextField: SearchTextField = circletProjectListWithSearch.searchField

  private val list: JBList<SpaceCloneListItem> = circletProjectListWithSearch.list.apply {
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

  val cloneViewModel = SpaceCloneComponentViewModel(lifetime, st.workspace)

  private val linkLabel: LinkLabel<*> = LinkLabel.create(SpaceBundle.message("clone.dialog.link.set.password.text"), null).apply {
    horizontalTextPosition = SwingConstants.LEFT
    iconTextGap = 0
  }

  private val useSshLinkLabel = ActionLink(SpaceBundle.message("clone.dialog.link.label.use.ssh")) {
    SpaceSettings.getInstance().cloneType = CloneType.SSH
    cloneViewModel.cloneType.value = SpaceSettings.getInstance().cloneType
  }.apply {
    isVisible = false
  }

  private val useHttpLinkLabel = ActionLink(SpaceBundle.message("clone.dialog.link.label.use.http")) {
    SpaceSettings.getInstance().cloneType = CloneType.HTTPS
    cloneViewModel.cloneType.value = SpaceSettings.getInstance().cloneType
  }.apply {
    isVisible = false
  }

  var createDirectoryError: ValidationInfo? = null

  init {
    cloneViewModel.readyToClone.forEach(lifetime, dialogStateListener::onOkActionEnabled)

    cloneViewModel.selectedCloneListItem.forEach(lifetime) { cloneListItem ->
      cloneListItem ?: return@forEach
      val details = cloneListItem.repoDetails.value
      val httpUrl = details?.urls?.httpUrl ?: cloneListItem.repoInfo.name

      val path = StringUtil.trimEnd(ClonePathProvider.relativeDirectoryPathForVcsUrl(project, httpUrl), GitUtil.DOT_GIT)
      directoryField.trySetChildPath(path)
    }

    cloneViewModel.me.forEach(lifetime) { profile ->
      accountLabel.toolTipText = profile.englishFullName() // NON-NLS
    }

    cloneViewModel.repos.batches.forEach(lifetime) { batchResult ->
      when (batchResult) {
        is BatchResult.More -> {
          val selection = circletProjectListWithSearch.list.selectedIndex
          listModel.add(batchResult.items.flatten())
          circletProjectListWithSearch.list.selectedIndex = selection
        }
        is BatchResult.Reset -> listModel.removeAll()
      }
    }

    SpaceUserAvatarProvider.getInstance().avatars.forEach(lifetime) { avatars ->
      accountLabel.icon = resizeIcon(avatars.circle, VcsCloneDialogUiSpec.Components.avatarSize)
    }

    cloneViewModel.isLoading.forEach(lifetime, list::setPaintBusy)

    cloneViewModel.spaceHttpPasswordState.forEach(lifetime) {
      if (cloneViewModel.cloneType.value == CloneType.HTTPS) {
        passwordStatus.clear()
        passwordStatus.append(SpaceBundle.message("clone.dialog.error.http.password.not.set.text"), SimpleTextAttributes.ERROR_ATTRIBUTES)
        linkLabel.setListener({ _, _ -> setGitHttpPassword() }, null)
        linkLabel.text = SpaceBundle.message("clone.dialog.link.set.http.password.text")
        linkLabel.icon = null

        passwordStatus.isVisible = it is SpaceHttpPasswordState.NotSet
        linkLabel.isVisible = it is SpaceHttpPasswordState.NotSet
        useSshLinkLabel.isVisible = it is SpaceHttpPasswordState.NotSet
        useHttpLinkLabel.isVisible = false
      }
    }

    cloneViewModel.circletKeysState.forEach(lifetime) {
      if (cloneViewModel.cloneType.value == CloneType.SSH) {
        passwordStatus.clear()
        passwordStatus.append(SpaceBundle.message("clone.dialog.error.ssh.is.not.configured.text"), SimpleTextAttributes.ERROR_ATTRIBUTES)
        linkLabel.setListener({ _, _ -> openSshKeysPage() }, null)
        linkLabel.text = SpaceBundle.message("clone.dialog.link.configure.ssh.text")
        linkLabel.icon = AllIcons.Ide.External_link_arrow

        passwordStatus.isVisible = it is SpaceKeysState.NotSet
        linkLabel.isVisible = it is SpaceKeysState.NotSet
        useSshLinkLabel.isVisible = false
        useHttpLinkLabel.isVisible = it is SpaceKeysState.NotSet
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
        menuItems += AccountMenuItem.Account(st.workspace.me.value.englishFullName(), // NON-NLS
                                             serverUrl,
                                             resizeIcon(SpaceUserAvatarProvider.getInstance().avatars.value.circle,
                                                        VcsCloneDialogUiSpec.Components.popupMenuAvatarSize),
                                             listOf(
                                               browseAction(SpaceBundle.message("clone.dialog.browse.server.action", serverUrl), host)))
        menuItems += browseAction(SpaceBundle.message("clone.dialog.open.projects.action"), SpaceUrls.projects(), true)
        menuItems += AccountMenuItem.Action(SpaceBundle.message("clone.dialog.open.settings.action"),
                                            {
                                              SpaceSettingsPanel.openSettings(project)
                                              updateSelectedUrl()
                                            },
                                            showSeparatorAbove = true)
        menuItems += AccountMenuItem.Action(SpaceBundle.message("clone.dialog.logout.action"),
                                            { SpaceWorkspaceComponent.getInstance().signOut(SpaceStatsCounterCollector.LogoutPlace.CLONE) })

        AccountsMenuListPopup(null, AccountMenuPopupStep(menuItems))
          .showUnderneathOf(accountLabel)
      }
    })
  }

  private fun setGitHttpPassword() {
    val dialog = SpaceSetGitHttpPasswordDialog(st.workspace.me.value, client)
    if (dialog.showAndGet()) {
      cloneViewModel.spaceHttpPasswordState.value = dialog.result
    }
  }

  private fun openSshKeysPage() {
    val profile = st.workspace.me.value
    val gitConfigPage = SpaceUrls.git(profile.username)
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
      row(SpaceBundle.message("clone.dialog.directory.to.clone.label.text")) {
        directoryField(growX, pushX)
      }
      row {
        cell(isFullWidth = true) {
          passwordStatus()
          linkLabel()
          useHttpLinkLabel()
          useSshLinkLabel()
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
    cloneViewModel.selectedCloneListItem.value = list.selectedValue
  }

  fun getSelectedListItem(): SpaceCloneListItem? {
    return cloneViewModel.selectedCloneListItem.value
  }

  fun getDirectory(): String = directoryField.text

  fun doValidateAll(): List<ValidationInfo> {
    val list = ArrayList<ValidationInfo>()
    ContainerUtil.addIfNotNull(list, CloneDvcsValidationUtils.checkDirectory(directoryField.text, directoryField.textField))
    return list
  }

  fun doClone(checkoutListener: CheckoutProvider.Listener) {
    val cloneListItem = getSelectedListItem() ?: return

    object : Task.Backgroundable(project, SpaceBundle.message("clone.dialog.progress.title.loading.repository.details"), false) {
      var details = cloneListItem.repoDetails.value

      override fun run(indicator: ProgressIndicator) {
        if (details != null) return
        details = st.workspace.lifetime.usingSource { lt ->
          runBlocking(lt, ProcessIOExecutorService.INSTANCE.asCoroutineDispatcher()) {
            cloneViewModel.loadDetails(cloneListItem)
          }
        }
      }

      override fun onSuccess() {
        val url = when (settings.cloneType) {
                    CloneType.SSH -> details!!.urls.sshUrl
                    CloneType.HTTPS -> details!!.urls.httpUrl
                  } ?: return

        val directory = getDirectory()

        createDirectoryError = CloneDvcsValidationUtils.createDestination(directory)
        if (createDirectoryError != null) {
          return
        }
        val parent = Paths.get(directory).toAbsolutePath().parent
        val lfs = LocalFileSystem.getInstance()
        val destinationParent = lfs.findFileByIoFile(parent.toFile()) ?: lfs.refreshAndFindFileByIoFile(parent.toFile()) ?: return

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
    }.queue()
  }
}

