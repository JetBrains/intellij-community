package org.intellij.plugins.markdown.editor.toc

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.siblings
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeader
import org.intellij.plugins.markdown.lang.psi.util.hasType
import org.intellij.plugins.markdown.ui.actions.MarkdownActionPlaces

internal class GenerateTableOfContentsAction: AnAction() {
  init {
    addTextOverride(MarkdownActionPlaces.INSERT_POPUP) {
      MarkdownBundle.message("action.Markdown.GenerateTableOfContents.insert.popup.text")
    }
  }

  override fun actionPerformed(event: AnActionEvent) {
    val file = event.getRequiredData(CommonDataKeys.PSI_FILE) as MarkdownFile
    val editor = event.getRequiredData(CommonDataKeys.EDITOR)
    val content = obtainToc(file)
    val caretOffset = editor.caretModel.primaryCaret.offset
    val project = event.getData(CommonDataKeys.PROJECT)
    val existingRanges = findExistingTocs(file).toList()
    runWriteAction {
      executeCommand(project) {
        val document = editor.document
        when {
          existingRanges.none() -> document.insertString(caretOffset, content)
          else -> existingRanges.asReversed().forEach { document.replaceString(it, content) }
        }
      }
    }
  }

  override fun update(event: AnActionEvent) {
    val file = event.getData(CommonDataKeys.PSI_FILE) as? MarkdownFile
    val editor = event.getData(CommonDataKeys.EDITOR)
    if (file == null || editor == null) {
      event.presentation.isEnabled = false
      return
    }
    if (findExistingTocs(file).any()) {
      event.presentation.text = MarkdownBundle.message("action.Markdown.GenerateTableOfContents.update.text")
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  companion object {
    @NlsSafe
    private const val sectionDelimiter = "<!-- TOC -->"

    private fun Document.replaceString(range: TextRange, replacement: String) {
      replaceString(range.startOffset, range.endOffset, replacement)
    }

    fun findExistingTocs(file: MarkdownFile): Sequence<TextRange> {
      val elements = collectTopLevelElements(file)
      val blockElements = elements.filter(this::isTocBlock).windowed(size = 2, step = 2)
      return blockElements.map { (begin, end) -> TextRange(begin.startOffset, end.endOffset) }
    }

    private fun isTocBlock(element: PsiElement): Boolean {
      if (element.hasType(MarkdownElementTypes.HTML_BLOCK)) {
        val child = element.firstChild?.takeIf { it.hasType(MarkdownTokenTypes.HTML_BLOCK_CONTENT) } ?: return false
        return child.textMatches(sectionDelimiter)
      }
      return false
    }

    private fun buildToc(file: MarkdownFile): String {
      val headers = collectHeaders(file)
      return buildString {
        appendLine(sectionDelimiter)
        for (header in headers) {
          appendHeader(header)
          appendLine()
        }
        append(sectionDelimiter)
      }
    }

    fun obtainToc(file: MarkdownFile): String {
      return CachedValuesManager.getCachedValue(file) {
        CachedValueProvider.Result.create(buildToc(file), PsiModificationTracker.MODIFICATION_COUNT)
      }
    }

    private fun collectTopLevelElements(file: MarkdownFile): Sequence<PsiElement> {
      val elements = file.firstChild?.firstChild?.siblings(forward = true, withSelf = true)
      return elements.orEmpty()
    }

    private fun collectHeaders(file: MarkdownFile): Sequence<MarkdownHeader> {
      val topLevelElements = collectTopLevelElements(file)
      return topLevelElements.filterIsInstance<MarkdownHeader>()
    }

    private fun StringBuilder.appendHeader(header: MarkdownHeader) {
      val text = header.buildVisibleText(hideImages = false) ?: return
      val reference = header.anchorText ?: return
      repeat(header.level - 1) {
        append("  ")
      }
      append("* [")
      append(text)
      append("](#")
      append(reference)
      append(")")
    }
  }
}
