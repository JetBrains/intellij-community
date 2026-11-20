// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.frontend.view.ketraterm

import com.intellij.ide.BrowserUtil
import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import io.github.ketraterm.ui.swing.api.SwingHostServices
import io.github.ketraterm.ui.swing.api.SwingHyperlinkDetector
import io.github.ketraterm.ui.swing.api.SwingTerminalContextMenuHandler
import io.github.ketraterm.ui.swing.api.SwingTerminalContextMenuRequest
import io.github.ketraterm.ui.swing.api.SwingTerminalHostKeyHandler
import io.github.ketraterm.ui.swing.api.TerminalUiDispatcher
import io.github.ketraterm.ui.swing.settings.TerminalClipboardHandler
import io.github.ketraterm.ui.swing.settings.TerminalHyperlinkHandler
import org.jetbrains.annotations.Nls
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

internal fun createKetraTermHostServices(
  settings: JBTerminalSystemSettingsProviderBase,
  hostKeyHandler: SwingTerminalHostKeyHandler,
  pasteClipboard: () -> Unit,
  hyperlinkDetector: SwingHyperlinkDetector,
): SwingHostServices = SwingHostServices.create {
  it.uiDispatcher = TerminalUiDispatcher { runnable ->
    ApplicationManager.getApplication().invokeLater(runnable, ModalityState.any())
  }
  it.clipboardHandler = KetraTermClipboardHandler(settings)
  it.hyperlinkHandler = TerminalHyperlinkHandler { uri ->
    BrowserUtil.browse(uri)
    true
  }
  it.hyperlinkDetector = hyperlinkDetector
  it.hostKeyHandler = hostKeyHandler
  it.contextMenuHandler = SwingTerminalContextMenuHandler { request ->
    showContextMenu(request, pasteClipboard)
    true
  }
}

internal class KetraTermClipboardHandler(private val settings: JBTerminalSystemSettingsProviderBase) : TerminalClipboardHandler {
  override fun copyText(text: String) {
    CopyPasteManager.getInstance().setContents(StringSelection(text))
  }

  override fun readText(): String? = CopyPasteManager.getInstance().getContents<String>(DataFlavor.stringFlavor)

  override fun readPrimarySelectionText(): String? {
    if (!settings.pasteOnMiddleMouseClick()) return null
    val contents = CopyPasteManager.getInstance().systemSelectionContents ?: return null
    if (!contents.isDataFlavorSupported(DataFlavor.stringFlavor)) return null
    return contents.getTransferData(DataFlavor.stringFlavor) as? String
  }
}

private fun showContextMenu(request: SwingTerminalContextMenuRequest, pasteClipboard: () -> Unit) {
  val actionManager = ActionManager.getInstance()
  val group = DefaultActionGroup()
  fun addAction(actionId: String, enabled: Boolean = true, perform: () -> Unit) {
    val source = actionManager.getAction(actionId) ?: return
    group.add(object : DumbAwareAction() {
      init {
        templatePresentation.copyFrom(source.templatePresentation)
      }

      override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

      override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = enabled
      }

      override fun actionPerformed(e: AnActionEvent) = perform()
    })
  }

  addAction(IdeActions.ACTION_COPY, request.hasSelection()) { request.copySelection() }
  addAction(IdeActions.ACTION_PASTE, perform = pasteClipboard)
  addAction(IdeActions.ACTION_SELECT_ALL) { request.selectAll() }
  request.hyperlink?.let { link ->
    group.addSeparator()
    (link.providerAction as? KetraTermHyperlinkAction)?.popupGroup(request.triggerEvent)?.let(group::add)
    group.add(menuAction(IdeBundle.message("terminal.action.OpenAsUrl.text")) { link.open() })
    if (link.uri != null) {
      group.add(menuAction(IdeBundle.message("action.text.copy.link.address")) { link.copyUri() })
    }
  }
  actionManager.createActionPopupMenu(ActionPlaces.POPUP, group).component.show(request.terminal, request.x, request.y)
}

private fun menuAction(@Nls text: String, perform: () -> Unit): DumbAwareAction = object : DumbAwareAction(text) {
  override fun actionPerformed(e: AnActionEvent) = perform()
}
