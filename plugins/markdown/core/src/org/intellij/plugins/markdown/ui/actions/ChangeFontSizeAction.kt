package org.intellij.plugins.markdown.ui.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.Key
import com.intellij.ui.jcef.JBCefApp
import org.intellij.plugins.markdown.ui.preview.MarkdownPreviewFileEditor
import org.intellij.plugins.markdown.ui.preview.PreviewLAFThemeStyles
import org.intellij.plugins.markdown.ui.preview.jcef.MarkdownJCEFHtmlPanel
import org.intellij.plugins.markdown.ui.preview.jcef.impl.executeJavaScript

private val FontSize = Key.create<Int>("Markdown.Preview.FontSize")

internal sealed class ChangeFontSizeAction(private val transform: (Int) -> Int): DumbAwareAction() {
  class Increase: ChangeFontSizeAction(transform = { it + 1 })

  class Decrease: ChangeFontSizeAction(transform = { (it - 1).coerceAtLeast(1) })

  class Reset: ChangeFontSizeAction(transform = { PreviewLAFThemeStyles.defaultFontSize })

  override fun actionPerformed(event: AnActionEvent) {
    val editor = MarkdownActionUtil.findMarkdownPreviewEditor(event)
    checkNotNull(editor) { "Preview editor should be obtainable from the action event" }
    val preview = editor.getUserData(MarkdownPreviewFileEditor.PREVIEW_BROWSER)?.get() ?: return
    if (preview !is MarkdownJCEFHtmlPanel) {
      return
    }
    val currentSize = preview.getUserData(FontSize) ?: PreviewLAFThemeStyles.defaultFontSize
    val newSize = transform(currentSize)
    preview.putUserData(FontSize, newSize)
    preview.changeFontSize(newSize)
  }

  override fun update(event: AnActionEvent) {
    val editor = MarkdownActionUtil.findMarkdownPreviewEditor(event)
    event.presentation.isEnabledAndVisible = editor != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }
}

/**
 * @param size Unscaled font size.
 */
internal fun MarkdownJCEFHtmlPanel.changeFontSize(size: Int) {
  val scaled = JBCefApp.normalizeScaledSize(size)
  // language=JavaScript
  val code = """
  |(function() {
  |  const styles = document.querySelector(":root").style;
  |  styles.setProperty("${PreviewLAFThemeStyles.Variables.FontSize}", "${scaled}px");
  |})();
  """.trimMargin()
  executeJavaScript(code)
}
