package org.intellij.plugins.markdown.ui.actions.styling

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.actionSystem.impl.MenuItemPresentationFactory
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.popup.PopupFactoryImpl
import com.intellij.ui.popup.PopupFactoryImpl.ActionItem
import com.intellij.ui.popup.list.PopupListElementRenderer
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.accessibility.AccessibleContext
import javax.swing.*

internal class SetHeaderLevelAction: AnAction(), CustomComponentAction {
  private val group = SetHeaderLevelGroup()

  override fun actionPerformed(event: AnActionEvent) = Unit

  override fun update(event: AnActionEvent) {
    updateWithChildren(event)
    val editor = event.getData(CommonDataKeys.EDITOR)
    if (editor?.let(this::isMultilineSelection) == true) {
      event.presentation.isEnabled = false
      return
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  private fun updateWithChildren(event: AnActionEvent) {
    event.presentation.isPopupGroup = true
    val children = group.getChildren(event).asSequence().filterIsInstance<SetHeaderLevelImpl>()
    val child = children.find { it.isSelected(event) }
    if (child == null) {
      val default = children.firstOrNull() ?: return
      event.presentation.text = default.templateText
      event.presentation.isEnabled = false
      return
    }
    event.presentation.text = child.templatePresentation.text
    child.update(event)
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return MyActionButton(group, presentation, place)
  }

  private fun isMultilineSelection(editor: Editor): Boolean {
    val document = editor.document
    val selectionModel = editor.selectionModel
    val start = document.getLineNumber(selectionModel.selectionStart)
    val end = document.getLineNumber(selectionModel.selectionEnd)
    return start != end
  }

  private class SetHeaderLevelGroup: DefaultActionGroup(
    SetHeaderLevelImpl.Normal(),
    Separator(),
    SetHeaderLevelImpl.Title(),
    SetHeaderLevelImpl.Subtitle(),
    SetHeaderLevelImpl.Heading(level = 3),
    SetHeaderLevelImpl.Heading(level = 4),
    Separator(),
    SetHeaderLevelImpl.Heading(level = 5),
    SetHeaderLevelImpl.Heading(level = 6)
  ) {
    init {
      templatePresentation.isPopupGroup = true
    }

    override fun displayTextInToolbar() = true

    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.BGT
    }
  }

  private class MyActionButton(group: ActionGroup, presentation: Presentation, place: String): ActionButtonWithText(group, presentation, place, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE) {
    private var wasPopupJustClosedByButtonClick = false

    override fun actionPerformed(event: AnActionEvent) {
      if (!wasPopupJustClosedByButtonClick) {
        showActionGroupPopup(action as ActionGroup, event)
      }
      wasPopupJustClosedByButtonClick = false
    }

    override fun createAndShowActionGroupPopup(actionGroup: ActionGroup, event: AnActionEvent): JBPopup {
      val popup = object: PopupFactoryImpl.ActionGroupPopup(
        null,
        actionGroup,
        event.dataContext,
        false,
        false,
        true,
        false,
        null,
        -1,
        null,
        ActionPlaces.getActionGroupPopupPlace(event.place),
        MenuItemPresentationFactory(),
        false
      ) {
        override fun getListElementRenderer(): ListCellRenderer<*> {
          return object: PopupListElementRenderer<Any>(this) {
            private lateinit var secondaryLabel: SimpleColoredComponent

            override fun createLabel() {
              super.createLabel()
              secondaryLabel = SimpleColoredComponent()
            }

            override fun createItemComponent(): JComponent? {
              createLabel()
              val panel = object: JPanel(BorderLayout()) {
                private val myAccessibleContext = myTextLabel.accessibleContext
                override fun getAccessibleContext(): AccessibleContext {
                  return myAccessibleContext ?: super.getAccessibleContext()
                }
              }
              panel.add(myTextLabel, BorderLayout.WEST)
              panel.add(secondaryLabel, BorderLayout.EAST)
              myIconBar = createIconBar()
              return layoutComponent(panel)
            }

            override fun createIconBar(): JComponent? {
              val res = Box.createHorizontalBox()
              res.border = JBUI.Borders.emptyRight(JBUI.CurrentTheme.ActionsList.elementIconGap())
              res.add(myIconLabel)
              return res
            }

            override fun customizeComponent(list: JList<out Any>?, value: Any?, isSelected: Boolean) {
              super.customizeComponent(list, value, isSelected)
              val action = (value as? ActionItem)?.action as? SetHeaderLevelImpl ?: return
              val font = obtainFontForLevel((action as? SetHeaderLevelImpl)?.level ?: 0)
              myTextLabel.font = font
              secondaryLabel.font = JBUI.Fonts.toolbarSmallComboBoxFont()
              secondaryLabel.clear()
              val secondaryText = action.secondaryText?.get() ?: return
              secondaryLabel.append(secondaryText, SimpleTextAttributes.GRAYED_ATTRIBUTES, true)
            }
          }
        }
      }
      popup.setShowSubmenuOnHover(true)
      popup.showUnderneathOf(event.inputEvent!!.component)
      return popup
    }
  }

  companion object {
    private fun obtainFontForLevel(level: Int): JBFont {
      val base = JBUI.Fonts.toolbarSmallComboBoxFont()
      return when (level) {
        1 -> base.biggerOn(5f).asBold()
        2 -> base.biggerOn(3f).asBold()
        3 -> base.biggerOn(2f).asBold()
        4 -> base.asBold()
        else -> base
      }
    }
  }
}
