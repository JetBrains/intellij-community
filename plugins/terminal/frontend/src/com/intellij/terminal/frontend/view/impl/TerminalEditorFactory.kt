package com.intellij.terminal.frontend.view.impl

import com.intellij.codeInsight.highlighting.BackgroundHighlightingUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.actions.ChangeEditorFontSizeStrategy
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.softwrap.EmptySoftWrapPainter
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.project.Project
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.terminal.TerminalFontSizeProvider
import com.intellij.util.AwaitCancellationAndInvoke
import com.intellij.util.awaitCancellationAndInvoke
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.TerminalFontSettingsListener
import org.jetbrains.plugins.terminal.TerminalFontSettingsService
import org.jetbrains.plugins.terminal.TerminalFontSizeProviderImpl
import org.jetbrains.plugins.terminal.block.ui.*
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils
import javax.swing.JScrollPane

@ApiStatus.Internal
object TerminalEditorFactory {
  private val LOG = logger<TerminalEditorFactory>()

  fun createOutputEditor(
    project: Project,
    settings: JBTerminalSystemSettingsProviderBase,
    coroutineScope: CoroutineScope,
  ): EditorImpl {
    val document = createDocument(withLanguage = true)
    val editor = createEditor(document, project, settings, coroutineScope)
    editor.putUserData(TerminalDataContextUtils.IS_OUTPUT_MODEL_EDITOR_KEY, true)
    addTopAndBottomInsets(editor)

    BackgroundHighlightingUtil.disableBackgroundHighlightingForeverIn(editor)
    TextEditorProvider.putTextEditor(editor, TerminalOutputTextEditor(editor))
    return editor
  }

  fun createAlternateBufferEditor(
    project: Project,
    settings: JBTerminalSystemSettingsProviderBase,
    coroutineScope: CoroutineScope,
  ): EditorImpl {
    val document = createDocument(withLanguage = false)
    val editor = createEditor(document, project, settings, coroutineScope)
    editor.putUserData(TerminalDataContextUtils.IS_ALTERNATE_BUFFER_MODEL_EDITOR_KEY, true)
    editor.scrollPane.verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_NEVER
    editor.scrollPane.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
    return editor
  }

  /**
   * Configures listening for font changes in the terminal settings [TerminalFontSettingsService].
   * Also configures temporary font size changing actions like [com.intellij.openapi.editor.actions.ChangeEditorFontSizeAction]
   * to apply changes to [TerminalFontSizeProviderImpl] and listens for its changes.
   *
   * Applies font change to the editor and calls [onFontChanged] after that.
   */
  fun listenEditorFontChanges(
    editor: EditorImpl,
    settings: JBTerminalSystemSettingsProviderBase,
    parentDisposable: Disposable,
    onFontChanged: () -> Unit,
  ) {
    editor.putUserData(ChangeEditorFontSizeStrategy.KEY, ChangeTerminalFontSizeStrategy)
    editor.putUserData(TerminalFontSizeProvider.KEY, TerminalFontSizeProviderImpl.getInstance())

    val fontSettingsListener = object : TerminalFontSettingsListener {
      override fun fontSettingsChanged() {
        editor.applyFontSettings(settings)
        editor.reinitSettings()
        onFontChanged()
      }
    }
    TerminalFontSettingsService.getInstance().addListener(fontSettingsListener, parentDisposable)

    TerminalFontSizeProviderImpl.getInstance().addListener(parentDisposable, object : TerminalFontSizeProvider.Listener {
      override fun fontChanged(showZoomIndicator: Boolean) {
        editor.setTerminalFontSize(
          fontSize = TerminalFontSizeProviderImpl.getInstance().getFontSize(),
          showZoomIndicator = showZoomIndicator,
        )
        onFontChanged()
      }
    })
  }

  @OptIn(AwaitCancellationAndInvoke::class)
  private fun createEditor(
    document: Document,
    project: Project,
    settings: JBTerminalSystemSettingsProviderBase,
    coroutineScope: CoroutineScope,
  ): EditorImpl {
    val editor = TerminalUiUtils.createOutputEditor(document, project, settings, installContextMenu = false)
    editor.contentComponent.focusTraversalKeysEnabled = false
    editor.contextMenuGroupId = "Terminal.ReworkedTerminalContextMenu"
    configureSoftWraps(editor)
    CopyOnSelectionHandler.install(editor, settings)

    coroutineScope.awaitCancellationAndInvoke(Dispatchers.UiWithModelAccess) {
      // It might be already released by the platform logic in case of the project closing.
      // So use an additional check to avoid double release exceptions.
      if (!editor.isDisposed) {
        EditorFactory.getInstance().releaseEditor(editor)
      }
    }
    return editor
  }

  private fun createDocument(withLanguage: Boolean): Document {
    return if (withLanguage) {
      runReadAction {
        FileDocumentManager.getInstance().getDocument(TerminalOutputVirtualFile())!!
      }
    }
    else DocumentImpl("", true)
  }

  private fun configureSoftWraps(editor: EditorImpl) {
    editor.settings.isUseSoftWraps = true
    editor.settings.isUseCustomSoftWrapIndent = false
    val softWrapModel = editor.softWrapModel
    softWrapModel.applianceManager.setLineWrapPositionStrategy(TerminalLineWrapPositionStrategy())
    softWrapModel.applianceManager.setSoftWrapsUnderScrollBar(true)
    softWrapModel.setSoftWrapPainter(EmptySoftWrapPainter)
  }

  private fun addTopAndBottomInsets(editor: Editor) {
    val inlayModel = editor.inlayModel

    val topRenderer = VerticalSpaceInlayRenderer(TerminalUi.blockTopInset)
    inlayModel.addBlockElement(0, false, true, TerminalUi.terminalTopInlayPriority, topRenderer)!!

    val bottomRenderer = VerticalSpaceInlayRenderer(TerminalUi.blockBottomInset)
    inlayModel.addBlockElement(editor.document.textLength, true, false, TerminalUi.terminalBottomInlayPriority, bottomRenderer)
  }
}