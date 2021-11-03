// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.smart

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter
import com.intellij.injected.editor.InjectionMeta
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.codeStyle.CodeStyleManager
import org.jetbrains.yaml.YAMLLanguage
import java.util.*


private val INJECTION_RANGE_BEFORE_ENTER = Key.create<RangeMarker>("NEXT_ELEMENT")
private val INDENT_BEFORE_PROCESSING = Key.create<String>("INDENT_BEFORE_PROCESSING")

fun preserveIndentStateBeforeProcessing(file: PsiFile, dataContext: DataContext) {
  if (file.virtualFile !is VirtualFileWindow) return
  val hostEditor = CommonDataKeys.HOST_EDITOR.getData(dataContext) as? EditorEx ?: return
  val hostFile = PsiManager.getInstance(hostEditor.project ?: return).findFile(hostEditor.virtualFile ?: return) ?: return
  if (!hostFile.viewProvider.hasLanguage(YAMLLanguage.INSTANCE)) return

  val injectionHost = InjectedLanguageManager.getInstance(file.project).getInjectionHost(file) ?: return
  val lineIndent = InjectionMeta.INJECTION_INDENT[injectionHost]
  INDENT_BEFORE_PROCESSING[file] = lineIndent
  INJECTION_RANGE_BEFORE_ENTER[file] = hostEditor.document.createRangeMarker(injectionHost.textRange)
}

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

    for (line in linesToAdjustIndent) {
      val lineStartOffset = document.getLineStartOffset(line)
      val forward = StringUtil.skipWhitespaceForward(document.charsSequence, lineStartOffset)
      CodeStyleManager.getInstance(file.project).adjustLineIndent(document, forward)
    }

    return EnterHandlerDelegate.Result.Continue
  }
}

private fun Document.linesBetween(startOffset: Int, endOffSet: Int): TreeSet<Int> {
  if (startOffset > textLength) return TreeSet<Int>()
  val endLine = if (endOffSet <= textLength) getLineNumber(endOffSet) else (lineCount - 1)
  return (getLineNumber(startOffset)..endLine).filterTo(TreeSet<Int>()) { line -> getLineStartOffset(line) >= startOffset }
}