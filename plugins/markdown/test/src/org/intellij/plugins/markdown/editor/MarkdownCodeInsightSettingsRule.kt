package org.intellij.plugins.markdown.editor

import org.intellij.plugins.markdown.settings.MarkdownCodeInsightSettings
import org.junit.rules.ExternalResource

class MarkdownCodeInsightSettingsRule(
  private val transform: ((MarkdownCodeInsightSettings.State) -> MarkdownCodeInsightSettings.State)? = null
): ExternalResource() {
  override fun before() {
    super.before()
    if (transform != null) {
      MarkdownCodeInsightSettings.getInstance().update(transform)
    }
  }

  override fun after() {
    super.after()
    MarkdownCodeInsightSettings.getInstance().reset()
  }
}
