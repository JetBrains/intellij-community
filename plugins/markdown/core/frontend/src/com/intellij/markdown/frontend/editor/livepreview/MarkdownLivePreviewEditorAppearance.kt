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
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.limits.FileSizeLimit.Companion.getIntellisenseLimit
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.StartupUiUtil
import org.intellij.plugins.markdown.lang.MarkdownFileType
import org.intellij.plugins.markdown.settings.MarkdownPreviewSettings
import org.intellij.plugins.markdown.ui.preview.PreviewLAFThemeStyles

/**
 * Shows one editor in the UI font at the `Preview font size` and with soft wraps while live preview is on.
 * Restores the font of the global scheme and the saved settings from before live preview when live preview turns off.
 * While live preview is on, keeps soft wraps on. Only a document above the IntelliSense size limit keeps its soft-wrap flag.
 */
internal class MarkdownLivePreviewEditorAppearance(private val editor: EditorEx) : Disposable {
  /** The editor soft wrap setting before live preview */
  private var preLivePreviewSoftWraps: Boolean? = null

  init {
    val connection = ApplicationManager.getApplication().messageBus.connect(this)
    connection.subscribe(MarkdownPreviewSettings.ChangeListener.TOPIC, MarkdownPreviewSettings.ChangeListener { onFontSettingsChanged() })
    connection.subscribe(UISettingsListener.TOPIC, UISettingsListener { onFontSettingsChanged() })
    connection.subscribe(LafManagerListener.TOPIC, LafManagerListener { onFontSettingsChanged() })
    editor.softWrapModel.addSoftWrapChangeListener(object : SoftWrapChangeListener {
      override fun softWrapsChanged() = onSoftWrapsChanged()
      override fun recalculationEnds() = Unit
    })
  }

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  fun change(isLivePreviewEnabled: Boolean) = if (isLivePreviewEnabled) apply() else restore()

  private fun apply() {
    applyFont()
    val settings = editor.settings
    if (preLivePreviewSoftWraps == null) {
      preLivePreviewSoftWraps = settings.isUseSoftWraps
    }
    forceSoftWraps()
  }

  private fun restore() {
    // A null font name restores the font of the global scheme.
    editor.colorsScheme.editorFontName = null
    // The zoomed global size clears the font size of this editor.
    editor.setFontSize(UISettingsUtils.getInstance().scaledEditorFontSize)
    val softWraps = preLivePreviewSoftWraps ?: return
    preLivePreviewSoftWraps = null
    editor.settings.isUseSoftWraps = softWraps
  }

  override fun dispose() = Unit

  private fun onFontSettingsChanged() {
    val update = Runnable {
      // Null saved settings mean that live preview is off, so the editor keeps the font of the global scheme.
      if (!editor.isDisposed && preLivePreviewSoftWraps != null) applyFont()
    }
    val application = ApplicationManager.getApplication()
    if (application.isDispatchThread) update.run() else application.invokeLater(update, ModalityState.any())
  }

  private fun onSoftWrapsChanged() {
    // Null saved settings mean that live preview is off, so the editor keeps the soft wraps that the user chose.
    if (preLivePreviewSoftWraps == null || editor.settings.isUseSoftWraps) return
    ApplicationManager.getApplication().invokeLater({
      if (!editor.isDisposed && preLivePreviewSoftWraps != null) forceSoftWraps()
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
  }

  companion object {
    private val KEY = Key.create<MarkdownLivePreviewEditorAppearance>("markdown.live.preview.appearance")

    @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
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
