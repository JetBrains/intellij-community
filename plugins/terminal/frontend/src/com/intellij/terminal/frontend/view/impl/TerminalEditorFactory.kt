package com.intellij.terminal.frontend.view.impl

import com.intellij.codeInsight.highlighting.BackgroundHighlightingUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
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
import kotlinx.coroutines.isActive
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.TerminalFontSettingsListener
import org.jetbrains.plugins.terminal.TerminalFontSettingsService
import org.jetbrains.plugins.terminal.TerminalFontSizeProviderImpl
import org.jetbrains.plugins.terminal.block.ui.*
import org.jetbrains.plugins.terminal.block.util.TerminalDataContextUtils
import java.awt.event.HierarchyEvent
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
    configureSoftWraps(editor)

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

    // Soft wraps are not needed in the alternate buffer editor because it's content is fully refreshed on resize.
    // So, soft wraps will only create additional noise.
    editor.settings.isUseSoftWraps = false
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
    editor.settings.isBlockCursor = false // we paint our own cursor, but this setting affects mouse selection subtly (IJPL-190533)
    editor.contentComponent.focusTraversalKeysEnabled = false
    editor.contextMenuGroupId = "Terminal.ReworkedTerminalContextMenu"
    CopyOnSelectionHandler.install(editor, settings)

    coroutineScope.awaitCancellationAndInvoke(Dispatchers.EDT) {
      // Check two things:
      // 1. If it is already disposed by the platform logic (for example, in case of project closing).
      // 2. Do not dispose if it is still showing because then it will be painted green.
      if (!editor.isDisposed && !editor.component.isShowing) {
        EditorFactory.getInstance().releaseEditor(editor)
      }
    }

    // Since we do not dispose the editor on scope cancellation if it is still showing,
    // we need to listen for its hiding and dispose it in case the coroutine scope is canceled.
    editor.component.addHierarchyListener { e ->
      if (e.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong() != 0L) {
        if (!editor.component.isShowing && !editor.isDisposed && !coroutineScope.coroutineContext.isActive) {
          EditorFactory.getInstance().releaseEditor(editor)
        }
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