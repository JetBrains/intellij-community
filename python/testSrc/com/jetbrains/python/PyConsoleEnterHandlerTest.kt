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
package com.jetbrains.python

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.EditorTestUtil
import com.jetbrains.python.console.PyConsoleEnterHandler
import com.jetbrains.python.fixtures.PyTestCase
import kotlin.math.max

/**
 * Created by Yuli Fiterman on 9/20/2016.
 */
class PyConsoleEnterHandlerTest : PyTestCase() {

  private lateinit var editor: Editor
  private lateinit var enterHandler: PyConsoleEnterHandler

  override fun setUp() {
    super.setUp()
    resetEditor()
    enterHandler = PyConsoleEnterHandler()
  }

  private fun resetEditor() {
    myFixture.configureByText(PythonFileType.INSTANCE, "")
    editor = myFixture.editor
  }

  private fun pushWithoutTyping(text: String): Boolean {
    // Avoid autocompletion while typing in some cases, to check incomplete statements
    WriteCommandAction.runWriteCommandAction(myFixture.project, {
      val lineCount = editor.document.lineCount
      val endOffset = editor.document.getLineEndOffset(max(0, lineCount - 1))
      editor.document.insertString(endOffset, text)
    })
    return enterHandler.handleEnterPressed(editor as EditorEx)
  }

  private fun push(text: String): Boolean {
    text.forEach { EditorTestUtil.performTypingAction(editor, it) }
    return enterHandler.handleEnterPressed(editor as EditorEx)
  }

  fun testTripleQuotes() {
    assertFalse(pushWithoutTyping("'''abs"))
  }

  fun testSingleQuote() {
    assertTrue(push("'a'"))
    assertTrue(push("a = 'abc'"))
    assertTrue(push("'abc"))
    assertTrue(push("a = 'st"))
  }

  fun testSimpleSingleLine() {
    assertTrue(push("a = 1"))
    resetEditor()
    assertFalse(push("for a in range(5):"))
    resetEditor()
    assertFalse(pushWithoutTyping("a = [1,\n2,"))
  }

  fun testInputComplete1() {
    assertFalse(push("if 1:"))
    assertFalse(push("\tx=1"))
    assertTrue(push(""))
  }

  fun testInputComplete2() {
    assertFalse(push("x = (2+\\"))
    assertTrue(push("3)"))
  }

  fun testInputComplete3() {
    push("if 1:")
    assertFalse(push("    x = (2+"))
    assertFalse(push("    y = 3"))
    assertTrue(push(""))
  }

  fun testInputComplete4() {
    push("try:")
    push("    a = 5")
    push("except:")
    assertFalse(push("    raise"))
  }

  fun testLineContinuation() {
    assertFalse(push("import os, \\"))
    assertTrue(push("sys"))
  }

  fun testLineContinuation2() {
    assertTrue(push("1 \\\n\n"))
  }

  fun testCellMagicHelp() {
    assertTrue(push("%%cellm?"))
  }

  fun testCellMagic() {
    assertFalse(push("%%cellm firstline"))
    assertFalse(push("  line2"))
    assertFalse(push("  line3"))
    assertTrue(push(""))

  }

  fun testMultiLineIf() {
    assertFalse(push("if True:"))
    assertFalse(push(""))
    assertFalse(push(""))
    assertFalse(push(""))
    assertFalse(push("\ta = 1"))
    assertTrue(push(""))
  }

  fun testTryExcept() {
    assertFalse(push("try:"))
    assertFalse(push(""))
    assertFalse(push(""))
    assertFalse(push("\ta = 1"))
    assertFalse(push(""))
    assertFalse(push(""))
    assertFalse(push("except:"))
    assertFalse(push(""))
    assertFalse(push(""))
    assertFalse(push("\tprint('hi!')"))
    assertTrue(push(""))
  }

  fun testBackSlash() {
    assertFalse(push("if True and \\"))
    assertFalse(push("\tTrue:"))
    assertFalse(push("\ta = 1"))
    assertTrue(push(""))
  }

  fun testMultipleBackSlash() {
    assertFalse(push("if\\"))
    assertFalse(push("\tTrue\\"))
    assertFalse(push("\t:\\"))
    assertFalse(push(""))
    assertFalse(push("\ta = \\"))
    assertFalse(push("\t1"))
    assertTrue(push(""))
  }

  fun testDocstringDouble() {
    assertFalse(pushWithoutTyping("a = \"\"\"test"))
    assertFalse(push("second"))
    assertTrue(push("third\"\"\""))
  }

  fun testDocstring() {
    assertFalse(pushWithoutTyping("a = '''test"))
    assertFalse(push("second"))
    assertTrue(push("third'''"))
  }

  fun testFunction() {
    assertFalse(push("def foo():"))
    assertFalse(push(""))
    assertFalse(push("\ta = 1"))
    assertFalse(push("\treturn 'hi!'"))
    assertTrue(push(""))
  }

  override fun tearDown() {
    Disposer.dispose(testRootDisposable)
    super.tearDown()
  }

}
