package org.intellij.plugins.markdown.editor

import org.intellij.plugins.markdown.settings.MarkdownCodeInsightSettings
import org.junit.rules.ExternalResource

class MarkdownCodeInsightSettingsRule(
  private val transform: ((MarkdownCodeInsightSettings.State) -> Unit)? = null
): ExternalResource() {
  override fun before() {
    super.before()
    transform?.invoke(MarkdownCodeInsightSettings.getInstance().state)
  }

  override fun after() {
    super.after()
    MarkdownCodeInsightSettings.getInstance().reset()
  }
}
