// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.frontend.editor.livepreview

import com.intellij.ide.ui.LafManagerListener
import com.intellij.ide.ui.UISettingsListener
import com.intellij.ide.ui.UISettingsUtils
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.SoftWrapChangeListener
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.EditorSettingsState
import com.intellij.openapi.editor.impl.SettingsImpl
import com.intellij.openapi.editor.impl.SoftWrapModelImpl
import com.intellij.openapi.editor.impl.softwrap.CompositeSoftWrapPainter
import com.intellij.openapi.editor.impl.softwrap.SoftWrapDrawingType
import com.intellij.openapi.editor.impl.softwrap.SoftWrapPainter
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.limits.FileSizeLimit.Companion.getIntellisenseLimit
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.text.CharArrayUtil
import com.intellij.util.ui.StartupUiUtil
import org.intellij.plugins.markdown.lang.MarkdownFileType
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.settings.MarkdownSettings
import org.intellij.plugins.markdown.ui.preview.PreviewLAFThemeStyles
import java.awt.Graphics
import java.util.function.IntUnaryOperator
import javax.swing.border.Border
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import kotlin.math.roundToInt
import kotlin.reflect.KMutableProperty1

/**
 * Shows one editor in the UI font at the `Preview font size`, with soft wraps and with text margins while live preview is on.
 * Hides line numbers, indent guides and the right margin while live preview is on.
 * Keeps the caret out of the virtual space after the end of a line while live preview is on.
 * Restores the font of the global scheme, the saved settings and the saved border from before live preview when live preview turns off.
 * A setting without an override before live preview follows the global settings again after live preview.
 * While live preview is on, keeps soft wraps on. Only a document above the IntelliSense size limit keeps its soft-wrap flag.
 * A wrapped line starts at the first non-blank character of its first line, with no soft-wrap sign before it.
 * A wrapped list item line starts under the item text.
 */
internal class MarkdownLivePreviewEditorAppearance private constructor(
  private val editor: EditorEx,
  private val settings: SettingsImpl,
) : Disposable {
  private val settingsState: EditorSettingsState = settings.getState()

  /** The editor settings before live preview. Null while live preview is off. */
  private var preLivePreviewState: State? = null

  private class State(
    val settings: List<SavedSetting>,
    val contentBorder: Border?,
  )

  /** A setting before live preview. A null [override] means that the setting had no override. */
  private class SavedSetting(
    val property: KMutableProperty1<EditorSettingsState, Boolean>,
    val override: Boolean?,
  )

  init {
    val connection = ApplicationManager.getApplication().messageBus.connect(this)
    connection.subscribe(MarkdownSettings.ChangeListener.TOPIC, object : MarkdownSettings.ChangeListener {
      override fun settingsChanged(settings: MarkdownSettings) = onFontSettingsChanged()
    })
    connection.subscribe(UISettingsListener.TOPIC, UISettingsListener { onFontSettingsChanged() })
    connection.subscribe(LafManagerListener.TOPIC, LafManagerListener { onFontSettingsChanged() })
    editor.softWrapModel.addSoftWrapChangeListener(object : SoftWrapChangeListener {
      override fun softWrapsChanged() = onSoftWrapsChanged()
      override fun recalculationEnds() = Unit
    })
    // The editor zoom changes the font size without the font settings.
    editor.addPropertyChangeListener({ event ->
      if (event.propertyName == EditorEx.PROP_FONT_SIZE_2D && preLivePreviewState != null) applyContentMargin()
    }, this)
  }

  @RequiresEdt
  fun change(isLivePreviewEnabled: Boolean) = if (isLivePreviewEnabled) apply() else restore()

  // The deferred refresh refreshes the editor once for all changed settings.
  private fun apply() = settings.runWithDeferredEditorRefresh {
    if (preLivePreviewState == null) {
      val savedSettings = SAVED_SETTINGS.map { property ->
        SavedSetting(property, if (settingsState.isOverridden(property)) property.get(settingsState) else null)
      }
      preLivePreviewState = State(savedSettings, editor.contentComponent.border)
    }
    // The content margin reads the saved border, so the state comes first.
    applyFont()
    settings.isLineNumbersShown = false
    settings.isIndentGuidesShown = false
    // The UI font puts the margin column in the middle of the text.
    settings.isRightMarginShown = false
    settings.isVirtualSpace = false
    forceSoftWraps()
    settings.isUseCustomSoftWrapIndent = true
    if (settings.isUseSoftWraps) setSoftWrapPainter(MarkdownLivePreviewSoftWrapPainter(editor))
  }

  private fun restore() {
    // A null font name restores the font of the global scheme.
    editor.colorsScheme.editorFontName = null
    // The zoomed global size clears the font size of this editor.
    editor.setFontSize(UISettingsUtils.getInstance().scaledEditorFontSize)
    val state = preLivePreviewState ?: return
    preLivePreviewState = null
    settings.runWithDeferredEditorRefresh {
      if (settings.isUseSoftWraps) setSoftWrapPainter(CompositeSoftWrapPainter(editor))
      for (setting in state.settings) {
        val override = setting.override
        if (override == null) settingsState.clearOverriding(setting.property) else setting.property.set(settingsState, override)
      }
    }
    setContentBorder(state.contentBorder)
  }

  override fun dispose() = Unit

  private fun onFontSettingsChanged() {
    val update = Runnable {
      // Null saved settings mean that live preview is off, so the editor keeps the font of the global scheme.
      if (!editor.isDisposed && preLivePreviewState != null) applyFont()
    }
    val application = ApplicationManager.getApplication()
    if (application.isDispatchThread) update.run() else application.invokeLater(update, ModalityState.any())
  }

  private fun onSoftWrapsChanged() {
    // Null saved settings mean that live preview is off, so the editor keeps the soft wraps that the user chose.
    if (preLivePreviewState == null || editor.settings.isUseSoftWraps) return
    ApplicationManager.getApplication().invokeLater({
      if (!editor.isDisposed && preLivePreviewState != null) forceSoftWraps()
    }, ModalityState.any())
  }

  /** Turns soft wraps on, unless the document is above the IntelliSense size limit. */
  private fun forceSoftWraps() {
    if (!settings.isUseSoftWraps && editor.document.textLength <= getIntellisenseLimit(MarkdownFileType.INSTANCE.defaultExtension)) {
      settings.isUseSoftWraps = true
    }
  }

  private fun applyFont() {
    val scheme = editor.colorsScheme
    val fontName = StartupUiUtil.labelFont.fontName
    val fontNameChanged = scheme.editorFontName != fontName
    if (fontNameChanged) {
      scheme.editorFontName = fontName
    }
    val fontSize = UISettingsUtils.getInstance().scaleFontSize(PreviewLAFThemeStyles.defaultFontSize.toFloat())
    if (scheme.editorFontSize2D != fontSize) {
      editor.setFontSize(fontSize)
    }
    applyContentMargin()
  }

  /**
   * Adds a text margin of [CONTENT_MARGIN_EMS] font sizes at both sides of the text, as the preview does.
   * The text margin at the left side keeps the text away from the gutter border.
   * The text margin at the right side keeps the soft-wrapped text away from the vertical scroll bar.
   */
  private fun applyContentMargin() {
    if (editor.editorKind == EditorKind.DIFF) return
    val margin = (CONTENT_MARGIN_EMS * editor.colorsScheme.editorFontSize2D).roundToInt()
    // The soft wraps take insets that are at least as wide as the scroll bar as insets that include the scroll bar.
    val scrollBar = editor.scrollPane.verticalScrollBar
    val scrollBarWidth = scrollBar.width.takeIf { it > 0 } ?: scrollBar.preferredSize.width
    // The editor font size already gives scaled pixels.
    @Suppress("UseDPIAwareBorders")
    val marginBorder = EmptyBorder(0, margin, 0, margin + scrollBarWidth)
    val border = preLivePreviewState?.contentBorder?.let { CompoundBorder(it, marginBorder) } ?: marginBorder
    if (editor.contentComponent.border?.getBorderInsets(editor.contentComponent) != border.getBorderInsets(editor.contentComponent)) {
      setContentBorder(border)
    }
  }

  private fun setSoftWrapPainter(painter: SoftWrapPainter) {
    (editor.softWrapModel as? SoftWrapModelImpl)?.setSoftWrapPainter(painter)
  }

  private fun setContentBorder(border: Border?) {
    editor.contentComponent.border = border
    // The soft wraps read the text width only when the visible area changes, and a new border keeps the visible area.
    (editor.softWrapModel as? SoftWrapModelImpl)?.applianceManager?.updateAvailableArea()
  }

  companion object {
    private const val CONTENT_MARGIN_EMS = 2

    private val KEY = Key.create<MarkdownLivePreviewEditorAppearance>("markdown.live.preview.appearance")

    /** The settings that live preview changes and restores. */
    private val SAVED_SETTINGS: List<KMutableProperty1<EditorSettingsState, Boolean>> = listOf(
      EditorSettingsState::myUseSoftWraps,
      EditorSettingsState::myUseCustomSoftWrapIndent,
      EditorSettingsState::myAreLineNumbersShown,
      EditorSettingsState::myIndentGuidesShown,
      EditorSettingsState::myIsRightMarginShown,
      EditorSettingsState::myIsVirtualSpace,
    )

    /** Returns null for a disposed editor, and for an editor whose settings are not [SettingsImpl]. */
    @RequiresEdt
    fun getOrCreate(editor: EditorEx): MarkdownLivePreviewEditorAppearance? {
      if (editor.isDisposed) return null
      editor.getUserData(KEY)?.let { return it }
      val settings = editor.settings as? SettingsImpl ?: return null
      return MarkdownLivePreviewEditorAppearance(editor, settings).also {
        editor.putUserData(KEY, it)
        EditorUtil.disposeWithEditor(editor, it)
      }
    }
  }
}

private class MarkdownLivePreviewSoftWrapPainter(
  private val editor: EditorEx,
  private val delegate: SoftWrapPainter = CompositeSoftWrapPainter(editor),
) : SoftWrapPainter by delegate {
  override fun paint(g: Graphics, drawingType: SoftWrapDrawingType, x: Int, y: Int, lineHeight: Int): Int =
    if (drawingType == SoftWrapDrawingType.AFTER_SOFT_WRAP) 0 else delegate.paint(g, drawingType, x, y, lineHeight)

  override fun getDrawingHorizontalOffset(g: Graphics, drawingType: SoftWrapDrawingType, x: Int, y: Int, lineHeight: Int): Int =
    if (drawingType == SoftWrapDrawingType.AFTER_SOFT_WRAP) 0 else delegate.getDrawingHorizontalOffset(g, drawingType, x, y, lineHeight)

  override fun getMinDrawingWidth(drawingType: SoftWrapDrawingType): Int =
    if (drawingType == SoftWrapDrawingType.AFTER_SOFT_WRAP) 0 else delegate.getMinDrawingWidth(drawingType)

  /** Returns the x of the item text from the line start on a list item marker line */
  override fun getIndentOffset(lineStartOffset: Int, offsetToX: IntUnaryOperator): Int {
    val document = editor.document
    val lineEnd = document.getLineEndOffset(document.getLineNumber(lineStartOffset))
    val iterator = editor.highlighter.createIterator(lineStartOffset)
    var markerEnd = -1
    while (!iterator.atEnd() && iterator.start < lineEnd) {
      when (iterator.tokenType) {
        MarkdownTokenTypes.LIST_BULLET, MarkdownTokenTypes.LIST_NUMBER, MarkdownTokenTypes.CHECK_BOX -> markerEnd = iterator.end
        MarkdownTokenTypes.WHITE_SPACE, MarkdownTokenTypes.BLOCK_QUOTE -> Unit
        else -> break
      }
      iterator.advance()
    }
    if (markerEnd < 0) return -1
    val textStart = CharArrayUtil.shiftForward(document.immutableCharSequence, markerEnd, " \t")
    return if (textStart < lineEnd) offsetToX.applyAsInt(textStart) else -1
  }
}
