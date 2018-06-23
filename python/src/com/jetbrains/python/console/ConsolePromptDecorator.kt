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

import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorLinePainter
import com.intellij.openapi.editor.LineExtensionInfo
import com.intellij.openapi.editor.TextAnnotationGutterProvider
import com.intellij.openapi.editor.colors.ColorKey
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.UIUtil
import com.jetbrains.python.console.parsing.PythonConsoleData

import java.awt.*

/**
 * Created by Yuli Fiterman on 9/16/2016.
 */
class ConsolePromptDecorator(private val myEditorEx: EditorEx, private val myConsoleData: PythonConsoleData) : EditorLinePainter(), TextAnnotationGutterProvider {

  var mainPrompt: String = ""
    set(mainPrompt) {
      if (this.mainPrompt != mainPrompt) {
        field = mainPrompt
        UIUtil.invokeLaterIfNeeded { myEditorEx.gutterComponentEx.revalidateMarkup() }
      }
    }

  var promptAttributes: ConsoleViewContentType? = ConsoleViewContentType.USER_INPUT
    set(promptAttributes) {
      field = promptAttributes
      myEditorEx.colorsScheme.setColor(promptColor, promptAttributes?.attributes?.foregroundColor)

      UIUtil.invokeLaterIfNeeded { myEditorEx.gutterComponentEx.revalidateMarkup() }
    }

  val indentPrompt: String
    get() =
    extend(if (myConsoleData.isIPythonEnabled) {
      PyConsoleUtil.IPYTHON_INDENT_PROMPT
    }
    else {
      PyConsoleUtil.INDENT_PROMPT

    }, mainPrompt.length)

  init {
    myEditorEx.colorsScheme.setColor(promptColor, this.promptAttributes?.attributes?.foregroundColor)
  }


  override fun getLineExtensions(project: Project, file: VirtualFile, lineNumber: Int): Collection<LineExtensionInfo>? {

    return null


  }

  override fun getLineText(line: Int, editor: Editor): String? {
    if (line == 0) {
      return mainPrompt
    }
    else if (line > 0) {
      return indentPrompt
    }
    else {
      return null
    }
  }


  override fun getToolTip(line: Int, editor: Editor): String? {
    return null
  }

  override fun getStyle(line: Int, editor: Editor): EditorFontType {
    return EditorFontType.CONSOLE_PLAIN
  }

  override fun getColor(line: Int, editor: Editor): ColorKey? {
    return promptColor
  }

  override fun getBgColor(line: Int, editor: Editor): Color? {
    var backgroundColor: Color? = this.promptAttributes?.attributes?.backgroundColor
    if (backgroundColor == null) {
      backgroundColor = myEditorEx.backgroundColor
    }
    return backgroundColor
  }

  override fun getPopupActions(line: Int, editor: Editor): List<AnAction>? {
    return null
  }

  override fun gutterClosed() {

  }

  companion object {
    private val promptColor = ColorKey.createColorKey("CONSOLE_PROMPT_COLOR")

    private fun extend(s: String, len: Int): String {
      var res = s
      while (res.length < len) {
        res = " " + res
      }

      return res

    }
  }
}

