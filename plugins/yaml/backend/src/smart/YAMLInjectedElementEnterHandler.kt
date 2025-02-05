// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.smart

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter
import com.intellij.formatting.FormattingMode
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ReadConstraint
import com.intellij.openapi.application.constrainedReadAndWriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.*

class YAMLInjectedElementEnterHandler : EnterHandlerDelegateAdapter() {

  override fun postProcessEnter(file: PsiFile, editor: Editor, dataContext: DataContext): EnterHandlerDelegate.Result {
    val hostEditor = CommonDataKeys.HOST_EDITOR.getData(dataContext) as? EditorEx ?: return EnterHandlerDelegate.Result.Continue
    val caretOffset = hostEditor.caretModel.offset
    val injectionIndent = INDENT_BEFORE_PROCESSING[file] ?: ""
    val pointer = INJECTION_RANGE_BEFORE_ENTER[file] ?: return EnterHandlerDelegate.Result.Continue
    val document = hostEditor.document
    val currentInjectionHost = InjectedLanguageManager.getInstance(file.project).getInjectionHost(file)
                               ?: return EnterHandlerDelegate.Result.Continue
    val currentHostRange = currentInjectionHost.textRange

    val lines = document.linesBetween(currentHostRange.endOffset, pointer.endOffset)
    pointer.dispose(); INJECTION_RANGE_BEFORE_ENTER[file] = null

    val caretLine = document.getLineNumber(caretOffset)
    lines.add(caretLine)
    val linesToAdjustIndent = TreeSet<Int>()
    for (line in lines) {
      val lineStartOffset = document.getLineStartOffset(line)
      val lineChars = document.charsSequence.subSequence(lineStartOffset, document.getLineEndOffset(line))
      val commonPrefixLength = StringUtil.commonPrefixLength(lineChars, injectionIndent)
      val fix = !lineChars.startsWith(injectionIndent)
      if (fix) {
        document.replaceString(lineStartOffset, lineStartOffset + commonPrefixLength, injectionIndent)
        if (line == caretLine) {
          hostEditor.caretModel.moveToOffset(lineStartOffset + injectionIndent.length)
        }
      }
      if (fix || (lineChars.isBlank() && lines.last() != line))
        linesToAdjustIndent.add(line)
    }

    if (Registry.`is`("yaml.injection.async.indent")) {
      usePreviousLineIndents(document, linesToAdjustIndent)
      YamlCoroutineScopeService.getCoroutineScope(file.project).launch {
        reformatAsync(file.project, document, linesToAdjustIndent)
      }
    }
    else {
      for (line in linesToAdjustIndent) {
        CodeStyleManager.getInstance(file.project).adjustLineIndent(document, document.getLineIndentRange(line).endOffset)
      }
    }

    return EnterHandlerDelegate.Result.Continue
  }

  private fun usePreviousLineIndents(document: Document, linesToAdjustIndent: TreeSet<Int>) {
    for (line in linesToAdjustIndent) {
      if (line == 0) continue
      val prevLineIndentRange = document.getLineIndentRange(line - 1)
      val indentRange = document.getLineIndentRange(line)
      if (prevLineIndentRange.length == indentRange.length) continue
      document.replaceString(indentRange.startOffset, indentRange.endOffset, prevLineIndentRange.subSequence(document.charsSequence))
    }
  }

  private fun Document.getLineIndentRange(line: Int): TextRange {
    val lineStart = getLineStartOffset(line)
    val indentEnd = StringUtil.skipWhitespaceForward(charsSequence, lineStart)
    return TextRange.create(lineStart, indentEnd)
  }

  private suspend fun reformatAsync(project: Project, document: Document, linesToAdjustIndent: Iterable<Int>) {
    val codeStyleManager = CodeStyleManager.getInstance(project)
    constrainedReadAndWriteAction(ReadConstraint.withDocumentsCommitted(project)) {
      val toAdjust = linesToAdjustIndent.mapNotNull { line ->
        val lineIndentRange = document.getLineIndentRange(line)
        val docFile = PsiDocumentManager.getInstance(project).getPsiFile(document) ?: return@mapNotNull null
        val indent = codeStyleManager.getLineIndent(docFile, lineIndentRange.endOffset, FormattingMode.ADJUST_INDENT)
                     ?: return@mapNotNull null
        if (StringUtil.equals(lineIndentRange.subSequence(document.charsSequence), indent)) return@mapNotNull null
        line to indent
      }
      if (toAdjust.isEmpty()) return@constrainedReadAndWriteAction value(Unit)
      writeAction {
        CommandProcessor.getInstance().runUndoTransparentAction {
          for ((line, lineIndent) in toAdjust) {
            val indentRange = document.getLineIndentRange(line)
            document.replaceString(indentRange.startOffset, indentRange.endOffset, lineIndent)
          }
        }
      }
    }
  }

}

@Service(Service.Level.PROJECT)
class YamlCoroutineScopeService(private val coroutineScope: CoroutineScope) {
  companion object {
    fun getCoroutineScope(project: Project): CoroutineScope = project.service<YamlCoroutineScopeService>().coroutineScope
  }
}


private fun Document.linesBetween(startOffset: Int, endOffSet: Int): TreeSet<Int> {
  if (startOffset > textLength) return TreeSet<Int>()
  val endLine = if (endOffSet <= textLength) getLineNumber(endOffSet) else (lineCount - 1)
  return (getLineNumber(startOffset)..endLine).filterTo(TreeSet<Int>()) { line -> getLineStartOffset(line) >= startOffset }
}