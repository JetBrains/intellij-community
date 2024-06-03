// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp.prompt

import com.intellij.codeInsight.AutoPopupController
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.editor.CaretVisualAttributes
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.ui.LanguageTextField
import com.intellij.ui.border.CustomLineBorder
import com.intellij.ui.components.JBLayeredPane
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import org.jetbrains.plugins.terminal.exp.BlockTerminalSession
import org.jetbrains.plugins.terminal.exp.TerminalCommandExecutor
import org.jetbrains.plugins.terminal.exp.TerminalInlineCompletion
import org.jetbrains.plugins.terminal.exp.TerminalUi
import org.jetbrains.plugins.terminal.exp.TerminalUi.useTerminalDefaultBackground
import org.jetbrains.plugins.terminal.exp.history.CommandHistoryPresenter
import org.jetbrains.plugins.terminal.exp.history.CommandSearchPresenter
import org.jetbrains.plugins.terminal.exp.prompt.TerminalPromptController.PromptStateListener
import org.jetbrains.plugins.terminal.exp.prompt.lang.TerminalPromptLanguage
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JLayeredPane
import javax.swing.JScrollPane
import kotlin.math.min

internal class TerminalPromptView(
  private val project: Project,
  private val settings: JBTerminalSystemSettingsProviderBase,
  session: BlockTerminalSession,
  commandExecutor: TerminalCommandExecutor
) : PromptStateListener, Disposable {
  val controller: TerminalPromptController
  val component: JComponent

  val preferredFocusableComponent: JComponent
    get() = editor.contentComponent

  private val editor: EditorImpl
  private val commandHistoryPresenter: CommandHistoryPresenter
  private val commandSearchPresenter: CommandSearchPresenter

  init {
    val editorTextField = createPromptTextField(session)
    editor = editorTextField.getEditor(true) as EditorImpl
    controller = TerminalPromptController(project, editor, session, commandExecutor)
    controller.addListener(this)

    commandHistoryPresenter = CommandHistoryPresenter(project, editor, controller)
    commandSearchPresenter = CommandSearchPresenter(project, editor, controller.model)

    val toolbarComponent = createToolbarComponent(targetComponent = editor.contentComponent)
    component = TerminalPromptPanel(editorTextField, toolbarComponent)

    val innerBorder = JBUI.Borders.empty(TerminalUi.promptTopInset,
                                         TerminalUi.blockLeftInset + TerminalUi.cornerToBlockInset,
                                         TerminalUi.promptBottomInset,
                                         0)
    val outerBorder = object : CustomLineBorder(TerminalUi.promptSeparatorColor(editor),
                                                JBInsets(1, 0, 0, 0)) {
      override fun paintBorder(c: Component, g: Graphics?, x: Int, y: Int, w: Int, h: Int) {
        // Paint the border only if the component is not on the top
        if (c.y != 0) {
          super.paintBorder(c, g, x, y, w, h)
        }
      }
    }
    component.border = JBUI.Borders.compound(outerBorder, innerBorder)

    // move focus to the prompt text field on mouse click in the area of the prompt
    component.addMouseListener(object : MouseAdapter() {
      override fun mousePressed(e: MouseEvent?) {
        IdeFocusManager.getInstance(project).requestFocus(editor.contentComponent, true)
      }
    })
  }

  override fun commandHistoryStateChanged(showing: Boolean) {
    if (showing) {
      val history = controller.commandHistory
      if (history.isNotEmpty()) {
        commandHistoryPresenter.showCommandHistory(history)
      }
    }
    else {
      commandHistoryPresenter.onCommandHistoryClosed()
    }
  }

  override fun commandSearchRequested() {
    val history = controller.commandHistory
    if (history.isNotEmpty()) {
      commandSearchPresenter.showCommandSearch(history)
    }
  }

  private fun createPromptTextField(session: BlockTerminalSession): LanguageTextField {
    val textField = object : LanguageTextField(TerminalPromptLanguage, project, "", false) {
      override fun setBackground(bg: Color?) {
        // do nothing to not set background to editor in super method
      }

      override fun updateUI() {
        super.updateUI()
        font = EditorUtil.getEditorFont()
      }
    }
    textField.setDisposedWith(this)
    textField.alignmentX = JComponent.LEFT_ALIGNMENT

    val editor = textField.getEditor(true) as EditorImpl
    editor.settings.isUseSoftWraps = true
    editor.settings.isShowingSpecialChars = false
    editor.scrollPane.border = JBUI.Borders.empty()
    editor.scrollPane.verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
    editor.scrollPane.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
    editor.setVerticalScrollbarVisible(true)
    editor.gutterComponentEx.isPaintBackground = false
    editor.useTerminalDefaultBackground(this)
    editor.colorsScheme.apply {
      editorFontName = settings.terminalFont.fontName
      editorFontSize = settings.terminalFont.size
      lineSpacing = 1.0f
      // to not paint the custom background under the prompt
      setColor(EditorColors.READONLY_FRAGMENT_BACKGROUND_COLOR, null)
    }
    editor.caretModel.primaryCaret.visualAttributes = CaretVisualAttributes(null, CaretVisualAttributes.Weight.HEAVY)
    editor.putUserData(AutoPopupController.SHOW_BOTTOM_PANEL_IN_LOOKUP_UI, false)

    FileDocumentManager.getInstance().getFile(editor.document)?.let {
      editor.setFile(it)
    }
    if (Registry.`is`("terminal.new.ui.inline.completion")) {
      TerminalInlineCompletion.getInstance(project).install(editor)
    }

    editor.contextMenuGroupId = "Terminal.PromptContextMenu"

    return textField
  }

  private fun createToolbarComponent(targetComponent: JComponent): JComponent {
    val actionManager = ActionManager.getInstance()
    val toolbarGroup = actionManager.getAction("Terminal.PromptToolbar") as ActionGroup
    val toolbar = actionManager.createActionToolbar("TerminalPrompt", toolbarGroup, true)
    toolbar.targetComponent = targetComponent
    toolbar.component.isOpaque = false
    toolbar.component.border = JBUI.Borders.emptyRight(10)
    return toolbar.component
  }

  override fun dispose() {}

  private class TerminalPromptPanel(
    private val mainComponent: JComponent,
    private val sideComponent: JComponent
  ) : JBLayeredPane() {
    init {
      isOpaque = false
      // cast to Any needed to call right method overload
      add(mainComponent, JLayeredPane.DEFAULT_LAYER as Any)
      add(sideComponent, JLayeredPane.POPUP_LAYER as Any)
    }

    override fun getPreferredSize(): Dimension {
      return mainComponent.preferredSize.also {
        JBInsets.addTo(it, insets)
      }
    }

    override fun doLayout() {
      val rect = Rectangle(0, 0, width, height)
      JBInsets.removeFrom(rect, insets)
      for (component in components) {
        when (component) {
          mainComponent -> layoutMainComponent(component, rect)
          sideComponent -> layoutSideComponent(component, rect)
        }
      }
    }

    private fun layoutMainComponent(component: Component, rect: Rectangle) {
      val prefHeight = component.preferredSize.height
      val compHeight = min(rect.height, prefHeight)
      component.setBounds(rect.x, rect.y, rect.width, compHeight)
    }

    private fun layoutSideComponent(component: Component, rect: Rectangle) {
      val prefSize = component.preferredSize
      val compWidth = min(rect.width, prefSize.width)
      component.setBounds(rect.x + rect.width - compWidth, rect.y, compWidth, prefSize.height)
    }
  }
}