// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables.ui

import com.intellij.codeInsight.hints.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.suggested.startOffset
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.editor.tables.TableModificationUtils.hasCorrectBorders
import org.intellij.plugins.markdown.editor.tables.TableUtils.calculateActualTextRange
import org.intellij.plugins.markdown.editor.tables.TableUtils.separatorRow
import org.intellij.plugins.markdown.editor.tables.ui.presentation.HorizontalBarPresentation
import org.intellij.plugins.markdown.editor.tables.ui.presentation.VerticalBarPresentation
import org.intellij.plugins.markdown.lang.MarkdownFileType
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTable
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTableRow
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownTableSeparatorRow
import org.intellij.plugins.markdown.settings.MarkdownSettings
import javax.swing.JPanel

internal class MarkdownTableInlayProvider: InlayHintsProvider<NoSettings> {
  override fun getCollectorFor(file: PsiFile, editor: Editor, settings: NoSettings, sink: InlayHintsSink): InlayHintsCollector? {
    if (!Registry.`is`("markdown.tables.editing.support.enable") || !MarkdownSettings.getInstance(file.project).isEnhancedEditingEnabled) {
      return null
    }
    if (file.fileType != MarkdownFileType.INSTANCE) {
      return null
    }
    return Collector(editor)
  }

  private class Collector(editor: Editor): FactoryInlayHintsCollector(editor) {
    override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
      if (editor.getUserData(DISABLE_TABLE_INLAYS) == true) {
        return true
      }
      if (element is MarkdownTable && element.hasCorrectBorders()) {
        processTableRows(element, editor, sink)
        val presentation = HorizontalBarPresentation.create(factory, editor, element)
        sink.addBlockElement(element.startOffset, false, true, -1, presentation)
      }
      return true
    }

    private fun processTableRows(table: MarkdownTable, editor: Editor, sink: InlayHintsSink) {
      val regularRows = table.getRows(true)
      val separatorRow = table.separatorRow ?: return
      val rows = regularRows.asSequence() + sequenceOf(separatorRow)
      for (row in rows) {
        val presentation = VerticalBarPresentation.create(factory, editor, row)
        val startOffset = row.calculateStartOffsetForInlay()
        sink.addInlineElement(startOffset, false, presentation, false)
      }
    }

    private fun PsiElement.calculateStartOffsetForInlay(): Int {
      return when (this) {
        is MarkdownTableSeparatorRow -> calculateActualTextRange().startOffset
        is MarkdownTableRow -> startOffset
        else -> error("This method should not be called on anything other than MarkdownTableRow or MarkdownTableSeparatorRow")
      }
    }
  }

  override fun createSettings() = NoSettings()

  override val name: String
    get() = MarkdownBundle.message("markdown.table.inlay.kind.name")

  override val description: String
    get() = MarkdownBundle.message("markdown.table.inlay.kind.description")

  override val key: SettingsKey<NoSettings>
    get() = settingsKey

  override val previewText: String? = null

  override fun createConfigurable(settings: NoSettings): ImmediateConfigurable {
    return object: ImmediateConfigurable {
      override fun createComponent(listener: ChangeListener) = JPanel()
    }
  }

  companion object {
    private val settingsKey = SettingsKey<NoSettings>("MarkdownTableInlayProviderSettingsKey")

    val DISABLE_TABLE_INLAYS = Key<Boolean>("MarkdownDisableTableInlaysKey")
  }
}
