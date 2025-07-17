// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.prompt

import com.intellij.codeInsight.AutoPopupController
import com.intellij.ide.navigationToolbar.NavBarModelExtension
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionToolbarListener
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.CaretVisualAttributes
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.ui.EditorTextField
import com.intellij.ui.border.CustomLineBorder
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.TerminalCommandExecutor
import org.jetbrains.plugins.terminal.block.history.CommandHistoryPresenter
import org.jetbrains.plugins.terminal.block.history.CommandSearchPresenter
import org.jetbrains.plugins.terminal.block.prompt.TerminalPromptController.PromptStateListener
import org.jetbrains.plugins.terminal.block.prompt.error.TerminalPromptErrorDescription
import org.jetbrains.plugins.terminal.block.prompt.error.TerminalPromptErrorStateListener
import org.jetbrains.plugins.terminal.block.prompt.error.TerminalPromptErrorUtil
import org.jetbrains.plugins.terminal.block.prompt.lang.TerminalPromptFileType
import org.jetbrains.plugins.terminal.block.session.BlockTerminalSession
import org.jetbrains.plugins.terminal.block.ui.TerminalUi
import org.jetbrains.plugins.terminal.block.ui.TerminalUi.useTerminalDefaultBackground
import org.jetbrains.plugins.terminal.block.ui.getCharSize
import org.jetbrains.plugins.terminal.block.ui.invokeLater
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.Dimension2D
import java.util.concurrent.CompletableFuture
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import kotlin.math.max
import kotlin.math.min

@ApiStatus.Internal
class TerminalPromptView(
  private val project: Project,
  private val settings: JBTerminalSystemSettingsProviderBase,
  session: BlockTerminalSession,
  commandExecutor: TerminalCommandExecutor,
) : PromptStateListener, Disposable {
  val controller: TerminalPromptController

  val component: JComponent
    get() = promptPanel

  private val promptPanel: TerminalPromptPanel

  val preferredFocusableComponent: JComponent
    get() = editor.contentComponent

  val terminalWidth: Int
    get() {
      val availableWidth = promptPanel.getAvailableContentWidth()
      val scrollBarWidth = editor.scrollPane.verticalScrollBar.width
      return availableWidth - scrollBarWidth
    }

  val charSize: Dimension2D
    get() = editor.getCharSize()

  private val editor: EditorImpl
  private val commandHistoryPresenter: CommandHistoryPresenter
  private val commandSearchPresenter: CommandSearchPresenter

  private val toolbar: ActionToolbar
  private val toolbarSizeInitializedFuture: CompletableFuture<*>

  init {
    val editorTextField = createPromptTextField()
    editor = editorTextField.getEditor(true) as EditorImpl
    controller = TerminalPromptController(project, editor, session, commandExecutor)
    controller.addListener(this)

    commandHistoryPresenter = CommandHistoryPresenter(project, editor, controller)
    commandSearchPresenter = CommandSearchPresenter(project, editor, controller.model)

    toolbar = createToolbar(targetComponent = editor.contentComponent)
    toolbarSizeInitializedFuture = scheduleToolbarUpdate(toolbar)

    promptPanel = TerminalPromptPanel(editorTextField, toolbar.component)

    val innerBorder = JBUI.Borders.empty(TerminalUi.promptTopInset,
                                         TerminalUi.blockLeftInset + TerminalUi.cornerToBlockInset,
                                         TerminalUi.promptBottomInset,
                                         0)
    val outerBorder = object : CustomLineBorder(TerminalUi.promptSeparatorColor(editor),
                                                JBInsets(1, 0, 0, 0)) {
      override fun getBorderInsets(c: Component): Insets {
        if (c.y == 0) {
          return JBInsets.emptyInsets()
        }
        return super.getBorderInsets(c)
      }

      override fun paintBorder(c: Component, g: Graphics?, x: Int, y: Int, w: Int, h: Int) {
        // Paint the border only if the component is not on the top
        if (c.y != 0) {
          super.paintBorder(c, g, x, y, w, h)
        }
      }
    }
    promptPanel.border = JBUI.Borders.compound(outerBorder, innerBorder)

    // move focus to the prompt text field on mouse click in the area of the prompt
    promptPanel.addMouseListener(object : MouseAdapter() {
      override fun mousePressed(e: MouseEvent?) {
        IdeFocusManager.getInstance(project).requestFocus(editor.contentComponent, true)
      }
    })

    controller.model.addErrorStateListener(object : TerminalPromptErrorStateListener {
      override fun errorStateChanged(description: TerminalPromptErrorDescription?) {
        val errorComponent = if (description != null) {
          TerminalPromptErrorUtil.createErrorComponent(description, editor.colorsScheme)
        }
        else null
        promptPanel.setBottomComponent(errorComponent)
      }
    }, parentDisposable = this)
  }

  /** Terminal width depends on toolbar size, because space for it is reserved */
  fun getTerminalWidthInitializedFuture(): CompletableFuture<*> {
    return toolbarSizeInitializedFuture
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

  private fun createPromptTextField(): EditorTextField {
    val textField = object : EditorTextField(project, TerminalPromptFileType) {
      override fun setBackground(bg: Color?) {
        // do nothing to not set background to editor in super method
      }

      override fun updateUI() {
        super.updateUI()
        font = EditorUtil.getEditorFont()
      }
    }
    textField.setOneLineMode(false)
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
      it.putUserData(NavBarModelExtension.IGNORE_IN_NAVBAR, true)
    }

    editor.contextMenuGroupId = "Terminal.PromptContextMenu"

    return textField
  }

  private fun createToolbar(targetComponent: JComponent): ActionToolbar {
    val actionManager = ActionManager.getInstance()
    val toolbarGroup = actionManager.getAction("Terminal.PromptToolbar") as ActionGroup
    val toolbar = actionManager.createActionToolbar("TerminalPrompt", toolbarGroup, true)
    toolbar.targetComponent = targetComponent
    toolbar.component.isOpaque = false
    toolbar.component.border = JBUI.Borders.emptyRight(10)
    return toolbar
  }

  /** After completion of the returned future, toolbar preferred size is guaranteed to be valid. */
  @RequiresEdt
  private fun scheduleToolbarUpdate(toolbar: ActionToolbar): CompletableFuture<*> {
    val disposable = Disposer.newCheckedDisposable(this)
    val future = CompletableFuture<Unit>()
    Disposer.register(disposable) {
      if (!future.isDone) {
        future.completeExceptionally(IllegalStateException("parent disposed"))
      }
    }

    val isDisposed = { disposable.isDisposed }
    // Invoke later, because in the current implementation 'updateActionsAsync' is updating the actions in place.
    // But the toolbar might not be added to the UI hierarchy yet -> don't have a correct data content.
    invokeLater(expired = isDisposed, ModalityState.any()) {
      // Prompt component that contains the toolbar is not visible on terminal opening.
      // So we need to explicitly mark the toolbar as showing, so the update won't be aborted.
      UIUtil.markAsShowing(toolbar.component, true)

      toolbar.addListener(object : ActionToolbarListener {
        override fun actionsUpdated() {
          // Resume future completion later, because at this moment the update is not reflected on the toolbar state.
          invokeLater(expired = isDisposed, ModalityState.any()) {
            future.complete(Unit)
          }
        }
      }, disposable)

      future.whenComplete { _, _ ->
        Disposer.dispose(disposable)
        UIUtil.markAsShowing(toolbar.component, false)
      }

      toolbar.updateActionsAsync()
    }

    return future
  }

  override fun dispose() {}

  private class TerminalPromptPanel(
    private val mainComponent: JComponent,
    private val sideComponent: JComponent,
  ) : JPanel(null) {
    private var bottomComponent: JComponent? = null

    init {
      isOpaque = false
      add(mainComponent)
      add(sideComponent)
    }

    fun setBottomComponent(component: JComponent?) {
      bottomComponent?.let { remove(it) }
      if (component != null) {
        add(component)
      }
      bottomComponent = component
      revalidate()
      repaint()
    }

    /** The resulting width of the [mainComponent] if we invoke [doLayout] right now. */
    fun getAvailableContentWidth(): Int {
      return width - insets.left - insets.right - sideComponent.preferredSize.width
    }

    override fun getPreferredSize(): Dimension {
      val mainComponentSize = mainComponent.preferredSize
      val bottomComponentSize = bottomComponent?.preferredSize ?: Dimension(0, 0)
      val sideComponentSize = sideComponent.preferredSize
      val size = Dimension(mainComponentSize.width + sideComponentSize.width, mainComponentSize.height + bottomComponentSize.height)
      JBInsets.addTo(size, insets)
      return size
    }

    override fun doLayout() {
      val rect = Rectangle(0, 0, width, height)
      JBInsets.removeFrom(rect, insets)

      val sidePrefSize = sideComponent.preferredSize
      val mainPrefSize = mainComponent.preferredSize
      val bottomPrefSize = bottomComponent?.preferredSize ?: Dimension(0, 0)

      // Place it in the top right corner
      val sideComponentX = max(rect.x + rect.width - sidePrefSize.width, 0)
      sideComponent.setBounds(sideComponentX, rect.y, sidePrefSize.width, sidePrefSize.height)

      // Make it fill the all horizontal space until the side component and vertical space until the bottom component
      val mainHeight = min(rect.height, mainPrefSize.height)
      mainComponent.setBounds(rect.x, rect.y, rect.width - sidePrefSize.width, mainHeight)

      // Place it right below the main component, allowing it to fill the full width.
      // Side component is small, so they should not intersect.
      val bottomHeight = min(rect.height - mainHeight, bottomPrefSize.height)
      bottomComponent?.setBounds(rect.x, rect.y + mainHeight, rect.width, bottomHeight)
    }
  }
}
