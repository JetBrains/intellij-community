// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables.ui

import com.intellij.codeInsight.hints.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.suggested.startOffset
import com.intellij.util.DocumentUtil
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.editor.tables.TableModificationUtils
import org.intellij.plugins.markdown.editor.tables.TableModificationUtils.hasCorrectBorders
import org.intellij.plugins.markdown.editor.tables.TableUtils
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
      if (element is MarkdownTableRow || element is MarkdownTableSeparatorRow) {
        if (DocumentUtil.isAtLineStart(element.startOffset, editor.document) && TableUtils.findTable(element)?.hasCorrectBorders() == true) {
          val presentation = VerticalBarPresentation.create(factory, editor, element)
          sink.addInlineElement(element.startOffset, false, presentation, false)
        }
      } else if (element is MarkdownTable && element.hasCorrectBorders()) {
        val presentation = HorizontalBarPresentation.create(factory, editor, element)
        sink.addBlockElement(element.startOffset, false, true, -1, presentation)
      }
      return true
    }
  }

  override fun createSettings() = NoSettings()

  override val name: String
    get() = MarkdownBundle.message("markdown.table.inlay.kind.name")

  override val key: SettingsKey<NoSettings>
    get() = settingsKey

  override val previewText: String
    get() = TableModificationUtils.buildEmptyTable(3, 3)

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
