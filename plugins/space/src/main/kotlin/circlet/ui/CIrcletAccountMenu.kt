package circlet.ui

import com.intellij.icons.*
import com.intellij.ide.*
import com.intellij.openapi.project.*
import com.intellij.openapi.ui.popup.*
import com.intellij.openapi.ui.popup.util.*
import com.intellij.ui.*
import com.intellij.ui.awt.*
import com.intellij.ui.components.*
import com.intellij.ui.popup.*
import com.intellij.ui.popup.list.*
import com.intellij.util.ui.*
import java.awt.*
import javax.swing.*

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

    override fun showUnderneathOf(aComponent: Component) {
        show(RelativePoint(aComponent, Point(-this.component.preferredSize.width+aComponent.width, aComponent.height)))
    }
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

fun browseAction(text: String, link: String, showSeparatorAbove: Boolean = false): AccountMenuItem.Action {
    return AccountMenuItem.Action(text, { BrowserUtil.browse(link) }, AllIcons.Ide.External_link_arrow, showSeparatorAbove)
}
