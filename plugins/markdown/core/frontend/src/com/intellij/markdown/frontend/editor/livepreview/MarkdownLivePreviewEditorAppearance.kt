// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.frontend.editor.livepreview

import com.intellij.ide.ui.LafManagerListener
import com.intellij.ide.ui.UISettingsListener
import com.intellij.ide.ui.UISettingsUtils
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.SoftWrapChangeListener
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.SoftWrapModelImpl
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.limits.FileSizeLimit.Companion.getIntellisenseLimit
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.StartupUiUtil
import org.intellij.plugins.markdown.lang.MarkdownFileType
import org.intellij.plugins.markdown.settings.MarkdownSettings
import org.intellij.plugins.markdown.ui.preview.PreviewLAFThemeStyles
import javax.swing.border.Border
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import kotlin.math.roundToInt

/**
 * Shows one editor in the UI font at the `Preview font size`, with soft wraps and with text margins while live preview is on.
 * Hides line numbers, indent guides and the right margin while live preview is on.
 * Restores the font of the global scheme, the saved settings and the saved border from before live preview when live preview turns off.
 * While live preview is on, keeps soft wraps on. Only a document above the IntelliSense size limit keeps its soft-wrap flag.
 * A wrapped line keeps the indent of its first line, so a nested list item stays indented.
 */
internal class MarkdownLivePreviewEditorAppearance(private val editor: EditorEx) : Disposable {
  /** The editor settings before live preview. Null while live preview is off. */
  private var preLivePreviewState: State? = null

  private data class State(
    val softWraps: Boolean,
    val customSoftWrapIndent: Boolean,
    val lineNumbers: Boolean,
    val indentGuides: Boolean,
    val rightMargin: Boolean,
    val contentBorder: Border?,
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

  private fun apply() {
    val settings = editor.settings
    if (preLivePreviewState == null) {
      preLivePreviewState = State(
        settings.isUseSoftWraps,
        settings.isUseCustomSoftWrapIndent,
        settings.isLineNumbersShown,
        settings.isIndentGuidesShown,
        settings.isRightMarginShown,
        editor.contentComponent.border,
      )
    }
    // The content margin reads the saved border, so the state comes first.
    applyFont()
    settings.isLineNumbersShown = false
    settings.isIndentGuidesShown = false
    // The UI font puts the margin column in the middle of the text.
    settings.isRightMarginShown = false
    forceSoftWraps()
    settings.isUseCustomSoftWrapIndent = true
  }

  private fun restore() {
    // A null font name restores the font of the global scheme.
    editor.colorsScheme.editorFontName = null
    // The zoomed global size clears the font size of this editor.
    editor.setFontSize(UISettingsUtils.getInstance().scaledEditorFontSize)
    (val softWraps, val customSoftWrapIndent, val lineNumbers, val indentGuides, val rightMargin, val contentBorder) =
      preLivePreviewState ?: return
    preLivePreviewState = null
    editor.settings.isUseSoftWraps = softWraps
    editor.settings.isUseCustomSoftWrapIndent = customSoftWrapIndent
    editor.settings.isLineNumbersShown = lineNumbers
    editor.settings.isIndentGuidesShown = indentGuides
    editor.settings.isRightMarginShown = rightMargin
    setContentBorder(contentBorder)
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
    val settings = editor.settings
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

  private fun setContentBorder(border: Border?) {
    editor.contentComponent.border = border
    // The soft wraps read the text width only when the visible area changes, and a new border keeps the visible area.
    (editor.softWrapModel as? SoftWrapModelImpl)?.applianceManager?.updateAvailableArea()
  }

  companion object {
    private const val CONTENT_MARGIN_EMS = 2

    private val KEY = Key.create<MarkdownLivePreviewEditorAppearance>("markdown.live.preview.appearance")

    @RequiresEdt
    fun getOrCreate(editor: EditorEx): MarkdownLivePreviewEditorAppearance? {
      if (editor.isDisposed) return null
      editor.getUserData(KEY)?.let { return it }
      return MarkdownLivePreviewEditorAppearance(editor).also {
        editor.putUserData(KEY, it)
        EditorUtil.disposeWithEditor(editor, it)
      }
    }
  }
}
