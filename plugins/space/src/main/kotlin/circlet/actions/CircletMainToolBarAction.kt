package circlet.actions

import circlet.client.api.*
import circlet.components.*
import circlet.platform.api.oauth.*
import circlet.platform.client.*
import circlet.settings.*
import circlet.ui.*
import circlet.ui.clone.*
import circlet.vcs.*
import circlet.workspaces.*
import com.intellij.icons.*
import com.intellij.ide.*
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.*
import com.intellij.openapi.project.*
import com.intellij.openapi.ui.popup.*
import com.intellij.openapi.ui.popup.util.*
import com.intellij.openapi.wm.*
import com.intellij.ui.*
import com.intellij.ui.components.*
import com.intellij.ui.components.panels.*
import com.intellij.ui.popup.*
import com.intellij.ui.popup.list.*
import com.intellij.util.ui.*
import com.intellij.util.ui.cloneDialog.*
import icons.*
import libraries.coroutines.extra.*
import runtime.*
import runtime.reactive.*
import java.awt.*
import java.awt.event.*
import java.util.concurrent.*
import javax.swing.*

class CircletMainToolBarAction : DumbAwareAction(), CustomComponentAction{

    override fun update(e: AnActionEvent) {
        val isOnNavBar = e.place == ActionPlaces.NAVIGATION_BAR_TOOLBAR
        e.presentation.isEnabledAndVisible = isOnNavBar
        if (!isOnNavBar) return

        val component = e.presentation.getClientProperty(CustomComponentAction.COMPONENT_KEY)
        if (component is JBLabel) {
            val avatar = CircletUserAvatarProvider.getInstance().avatar.value
            val statusIcon = if (circletWorkspace.workspace.value?.client?.connectionStatus?.value is ConnectionStatus.Connected)
            CircletIcons.statusOnline else CircletIcons.statusOffline
            val layeredIcon = LayeredIcon(2).apply {
                setIcon(resizeIcon(avatar, 16), 0)
                setIcon(statusIcon, 1, SwingConstants.SOUTH_EAST)
            }
            component.icon = layeredIcon
        }
    }

    override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
        return JBLabel(CircletUserAvatarProvider.getInstance().avatar.value).apply {
            text = " "
            addMouseListener(object :MouseAdapter(){
                override fun mouseClicked(e: MouseEvent?) {
                    val toolbar = ComponentUtil.getParentOfType(ActionToolbar::class.java, this@apply)
                    val dataContext = toolbar?.toolbarDataContext ?: DataManager.getInstance().getDataContext(this@apply)
                    actionPerformed(AnActionEvent.createFromInputEvent(e, place, presentation, dataContext, true, true))
                }
            })
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val component = e.inputEvent.component
        val workspace = circletWorkspace.workspace.value
        if (workspace != null) {
            buildMenu(workspace, CircletUserAvatarProvider.getInstance().avatar.value, e.project!!)
                .showUnderneathOf(component)
        }
        else {
            val disconnected = CircletLoginState.Disconnected(CircletServerSettingsComponent.getInstance().settings.value.server)

            val loginState: MutableProperty<CircletLoginState> = mutableProperty(disconnected)

            val wrapper = Wrapper()
            val popup = JBPopupFactory.getInstance().createComponentPopupBuilder(wrapper, wrapper)
                .setRequestFocus(true)
                .setFocusable(true)
                .createPopup()
            loginState.view(circletWorkspace.lifetime) { _: Lifetime, st: CircletLoginState ->
                val view = createView(st, loginState, component)
                if (view == null) {
                    popup.cancel()
                    return@view
                }
                view.border = JBUI.Borders.empty(8, 12)
                wrapper.setContent(view)
                wrapper.repaint()
                if (st is CircletLoginState.Disconnected) {
                    popup.pack(true, true)
                }
            }
            popup.showUnderneathOf(component)
        }
    }

    private fun createView(st: CircletLoginState, loginState: MutableProperty<CircletLoginState>, component: Component): JComponent? {
        return when (st) {
            is CircletLoginState.Connected -> {
                null
            }

            is CircletLoginState.Connecting -> {
                buildConnectingPanel(st) {
                    st.lt.terminate()
                    loginState.value = CircletLoginState.Disconnected(st.server)
                }
            }

            is CircletLoginState.Disconnected -> {
                buildLoginPanel(st) { serverName ->
                    login(serverName, loginState, component)
                }
            }
        }
    }

    private fun login(serverName: String, loginState: MutableProperty<CircletLoginState>, component: Component) {
        launch(circletWorkspace.lifetime, Ui) {
            circletWorkspace.lifetime.usingSource { connectLt ->
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
                val frame = SwingUtilities.getAncestorOfClass(JFrame::class.java, component)
                AppIcon.getInstance().requestFocus(frame as IdeFrame?)
            }
        }
    }

    private fun buildMenu(workspace: Workspace, icon: Icon, project: Project): AccountsMenuListPopup {
        val url = CircletServerSettingsComponent.getInstance().settings.value.server
        val serverUrl = cleanupUrl(url)
        val menuItems: MutableList<AccountMenuItem> = mutableListOf()
        menuItems += AccountMenuItem.Account(
            workspace.me.value.englishFullName(),
            serverUrl,
            resizeIcon(icon, VcsCloneDialogUiSpec.Components.popupMenuAvatarSize),
            listOf(AccountMenuItem.Action("Open $serverUrl",
                                          { BrowserUtil.browse(url) },
                                          AllIcons.Ide.External_link_arrow,
                                          showSeparatorAbove = true)))
        menuItems += AccountMenuItem.Action("Clone Repository...",
                                            { CircletCloneAction.runClone(project) },
                                            showSeparatorAbove = true)
        val projectContext = CircletProjectContext.getInstance(project)
        val projectInfos = projectContext.projectDescriptions

        if (projectInfos != null) {
            val keys = projectInfos.second
            if (keys.size > 1) {
                val reviewActions = keys.map {
                    val key = it.projectKey.key
                    val projectName = it.project.name
                    AccountMenuItem.Action("Open for $projectName project",
                                           { BrowserUtil.browse("${url}/p/${key}/review") },
                                           AllIcons.Ide.External_link_arrow)
                }.toList()
                menuItems += AccountMenuItem.Group("Code Reviews", reviewActions)

                val planningActions = keys.map {
                    val key = it.projectKey.key
                    val projectName = it.project.name
                    AccountMenuItem.Action("Open for $projectName project",
                                           { BrowserUtil.browse("${url}/p/$key/checklists") },
                                           AllIcons.Ide.External_link_arrow)
                }.toList()
                menuItems += AccountMenuItem.Group("Checklists", planningActions)

                val issuesActions = keys.map {
                    val key = it.projectKey.key
                    val projectName = it.project.name
                    AccountMenuItem.Action("Open for $projectName project",
                                           { BrowserUtil.browse("${url}/p/$key/issues") },
                                           AllIcons.Ide.External_link_arrow)
                }.toList()
                menuItems += AccountMenuItem.Group("Issues", issuesActions)
            }
            else {
                val key = keys.first().projectKey.key

                menuItems += AccountMenuItem.Action("Code Reviews",
                                                    { BrowserUtil.browse("${url}/p/$key/review") },
                                                    AllIcons.Ide.External_link_arrow)
                menuItems += AccountMenuItem.Action("Checklists",
                                                    { BrowserUtil.browse("${url}/p/$key/checklists") },
                                                    AllIcons.Ide.External_link_arrow)
                menuItems += AccountMenuItem.Action("Issues",
                                                    { BrowserUtil.browse("${url}/p/$key/issues") },
                                                    AllIcons.Ide.External_link_arrow)
            }
        }
        menuItems += AccountMenuItem.Action("Log Out...",
                                            { circletWorkspace.signOut() },
                                            showSeparatorAbove = true)

        return AccountsMenuListPopup(project, AccountMenuPopupStep(menuItems))
    }
}

sealed class AccountMenuItem(val showSeparatorAbove: Boolean) {
    class Account(val title: String,
                  val info: String,
                  val icon: Icon,
                  val actions: List<AccountMenuItem> = emptyList(),
                  showSeparatorAbove: Boolean = false
    ) : AccountMenuItem(showSeparatorAbove)

    class Action(val text: String,
                 val runnable: () -> Unit,
                 val rightIcon: Icon = EmptyIcon.create(AllIcons.Ide.External_link_arrow),
                 showSeparatorAbove: Boolean = false
    ) : AccountMenuItem(showSeparatorAbove)

    class Group(val text: String,
                val actions: List<AccountMenuItem> = emptyList(),
                showSeparatorAbove: Boolean = false
    ) : AccountMenuItem(showSeparatorAbove)
}

class AccountMenuPopupStep(items: List<AccountMenuItem>) : BaseListPopupStep<AccountMenuItem>(null, items) {
    override fun hasSubstep(selectedValue: AccountMenuItem?): Boolean {
        val hasSubActions = selectedValue is AccountMenuItem.Account && selectedValue.actions.size > 1
        return hasSubActions
    }

    override fun onChosen(selectedValue: AccountMenuItem, finalChoice: Boolean): PopupStep<*>? = when (selectedValue) {
        is AccountMenuItem.Action -> doFinalStep(selectedValue.runnable)
        is AccountMenuItem.Account -> when {
            selectedValue.actions.isEmpty() -> null
            selectedValue.actions.size == 1 -> doFinalStep((selectedValue.actions.first() as AccountMenuItem.Action).runnable)
            else -> AccountMenuPopupStep(selectedValue.actions)
        }
        is AccountMenuItem.Group -> AccountMenuPopupStep(selectedValue.actions)
    }

    override fun getBackgroundFor(value: AccountMenuItem?) = UIUtil.getPanelBackground()
}

class AccountsMenuListPopup(
    project: Project? = null,
    accountMenuPopupStep: AccountMenuPopupStep,
    parent: WizardPopup? = null,
    parentObject: Any? = null
) : ListPopupImpl(project,
                  parent,
                  accountMenuPopupStep,
                  parentObject
) {
    override fun getListElementRenderer() = AccountMenuItemRenderer()

    override fun createPopup(parent: WizardPopup?, step: PopupStep<*>?, parentValue: Any?) = AccountsMenuListPopup(
        parent?.project,
        step as AccountMenuPopupStep,
        parent,
        parentValue)
}

class AccountMenuItemRenderer : ListCellRenderer<AccountMenuItem> {
    private val leftInset = 12
    private val innerInset = 8
    private val emptyMenuRightArrowIcon = EmptyIcon.create(AllIcons.General.ArrowRight)
    private val separatorBorder = JBUI.Borders.customLine(JBUI.CurrentTheme.Popup.separatorColor(), 1, 0, 0, 0)

    private val listSelectionBackground = UIUtil.getListSelectionBackground(true)

    private val accountRenderer = AccountItemRenderer()
    private val actionRenderer = ActionItemRenderer()
    private val groupRenderer = GroupItemRenderer()

    override fun getListCellRendererComponent(list: JList<out AccountMenuItem>?,
                                              value: AccountMenuItem,
                                              index: Int,
                                              selected: Boolean,
                                              focused: Boolean): Component {
        val renderer = when (value) {
            is AccountMenuItem.Account -> accountRenderer.getListCellRendererComponent(null, value, index, selected, focused)
            is AccountMenuItem.Action -> actionRenderer.getListCellRendererComponent(null, value, index, selected, focused)
            is AccountMenuItem.Group -> groupRenderer.getListCellRendererComponent(null, value, index, selected, focused)
        }
        UIUtil.setBackgroundRecursively(renderer, if (selected) listSelectionBackground else UIUtil.getPanelBackground())
        renderer.border = if (value.showSeparatorAbove) separatorBorder else null
        return renderer
    }

    private inner class AccountItemRenderer : JPanel(GridBagLayout()), ListCellRenderer<AccountMenuItem.Account> {
        private val listSelectionForeground = UIUtil.getListSelectionForeground(true)

        val avatarLabel = JLabel()
        val titleComponent = JLabel().apply {
            font = JBUI.Fonts.label().asBold()
        }
        val link = JBLabel().apply {
            font = JBUI.Fonts.smallFont()
        }
        val nextStepIconLabel = JLabel()

        init {
            val insets = JBUI.insets(innerInset, leftInset, innerInset, innerInset)
            var gbc = GridBag().setDefaultAnchor(GridBag.WEST)

            gbc = gbc.nextLine().next() // 1, |
                .weightx(0.0).fillCellVertically().insets(insets).coverColumn()
            add(avatarLabel, gbc)

            gbc = gbc.next() // 2, 1
                .weightx(1.0).weighty(0.5).fillCellHorizontally().anchor(GridBag.LAST_LINE_START)
            add(titleComponent, gbc)

            gbc = gbc.next() // 3, |
                .weightx(0.0).insets(insets).coverColumn()
            add(nextStepIconLabel, gbc)

            gbc = gbc.nextLine().next().next() // 2, 2
                .weightx(1.0).weighty(0.5).fillCellHorizontally().anchor(GridBag.FIRST_LINE_START)
            add(link, gbc)
        }

        override fun getListCellRendererComponent(list: JList<out AccountMenuItem.Account>?,
                                                  value: AccountMenuItem.Account,
                                                  index: Int,
                                                  selected: Boolean,
                                                  focused: Boolean): JComponent {
            avatarLabel.icon = value.icon

            titleComponent.apply {
                text = value.title
                foreground = if (selected) listSelectionForeground else SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES.fgColor
            }

            link.apply {
                text = value.info
                foreground = if (selected) listSelectionForeground else SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES.fgColor
            }

            nextStepIconLabel.apply {
                icon = when {
                    value.actions.size < 2 -> emptyMenuRightArrowIcon
                    selected && ColorUtil.isDark(listSelectionBackground) -> AllIcons.Icons.Ide.NextStepInverted
                    else -> AllIcons.Icons.Ide.NextStep
                }
            }
            return this
        }
    }

    private inner class ActionItemRenderer : JPanel(GridBagLayout()), ListCellRenderer<AccountMenuItem.Action> {
        val actionTextLabel = JLabel()
        val rightIconLabel = JLabel()

        init {
            val topBottom = 3
            val insets = JBUI.insets(topBottom, leftInset, topBottom, 0)

            var gbc = GridBag().setDefaultAnchor(GridBag.WEST)
            gbc = gbc.nextLine().next().insets(insets)
            add(actionTextLabel, gbc)
            gbc = gbc.next()
            add(rightIconLabel, gbc)
            gbc = gbc.next().fillCellHorizontally().weightx(1.0).anchor(GridBag.EAST)
                .insets(JBUI.insets(topBottom, leftInset, topBottom, innerInset))
            add(JLabel(emptyMenuRightArrowIcon), gbc)
        }

        override fun getListCellRendererComponent(list: JList<out AccountMenuItem.Action>?,
                                                  value: AccountMenuItem.Action,
                                                  index: Int,
                                                  selected: Boolean,
                                                  focused: Boolean): JComponent {
            actionTextLabel.text = value.text
            rightIconLabel.icon = value.rightIcon
            actionTextLabel.foreground = UIUtil.getListForeground(selected, true)
            return this
        }
    }

    private inner class GroupItemRenderer : JPanel(GridBagLayout()), ListCellRenderer<AccountMenuItem.Group> {
        val actionTextLabel = JLabel()
        val rightIconLabel = JLabel()

        init {
            val topBottom = 3
            val insets = JBUI.insets(topBottom, leftInset, topBottom, 0)

            var gbc = GridBag().setDefaultAnchor(GridBag.WEST)
            gbc = gbc.nextLine().next().insets(insets)
            add(actionTextLabel, gbc)
            gbc = gbc.next().weightx(1.0)
            add(rightIconLabel, gbc)
            gbc = gbc.next().fillCellHorizontally().weightx(0.0).anchor(GridBag.EAST)
                .insets(JBUI.insets(topBottom, leftInset, topBottom, innerInset))
            add(JLabel(AllIcons.General.ArrowRight), gbc)
        }

        override fun getListCellRendererComponent(list: JList<out AccountMenuItem.Group>?,
                                                  value: AccountMenuItem.Group,
                                                  index: Int,
                                                  selected: Boolean,
                                                  focused: Boolean): JComponent {
            actionTextLabel.text = value.text
            actionTextLabel.foreground = UIUtil.getListForeground(selected, true)
            return this
        }
    }
}

