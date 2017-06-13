/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.console

import com.intellij.execution.impl.ConsoleViewUtil
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.richcopy.settings.RichCopySettings
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.TextRange
import java.awt.datatransfer.StringSelection

/**
 * Created by Yuli Fiterman on 9/17/2016.
 */
class PyConsoleCopyHandler(val originalHandler: EditorActionHandler) : EditorActionHandler() {

  override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
    if (!RichCopySettings.getInstance().isEnabled) {
      return originalHandler.execute(editor, null, dataContext)
    }
    if (true != editor.getUserData(ConsoleViewUtil.EDITOR_IS_CONSOLE_HISTORY_VIEW)
        || editor.caretModel.allCarets.size != 1) {
      return originalHandler.execute(editor, null, dataContext)
    }
    doCopyWithoutPrompt(editor as EditorEx)
  }

  private fun doCopyWithoutPrompt(editor: EditorEx) {
    val start = editor.selectionModel.selectionStart
    val end = editor.selectionModel.selectionEnd
    val document = editor.document
    val beginLine = document.getLineNumber(start)
    val endLine = document.getLineNumber(end)
    val sb = StringBuilder()
    for (i in beginLine..endLine) {
      var lineStart = document.getLineStartOffset(i)
      val r = Ref.create<Int>()
      editor.markupModel.processRangeHighlightersOverlappingWith(lineStart, lineStart) {
        val length = it.getUserData(PROMPT_LENGTH_MARKER) ?: return@processRangeHighlightersOverlappingWith true
        r.set(length)
        false
      }
      if (!r.isNull) {
        lineStart += r.get()
      }
      val rangeStart = Math.max(lineStart, start)
      val rangeEnd = Math.min(document.getLineEndOffset(i), end)
      if (rangeStart < rangeEnd) {
        sb.append(document.getText(TextRange(rangeStart, rangeEnd)))
        if (rangeEnd < end) {
          sb.append("\n")
        }
      }
    }
    if (!sb.isEmpty()) {
      CopyPasteManager.getInstance().setContents(StringSelection(sb.toString()))
    }
  }

  companion object {
    @JvmField
    val PROMPT_LENGTH_MARKER: Key<Int?> = Key.create<Int>("PROMPT_LENGTH_MARKER");

  }
}
