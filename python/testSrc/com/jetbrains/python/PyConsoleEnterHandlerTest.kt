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

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.EditorTestUtil
import com.jetbrains.python.console.PyConsoleEnterHandler
import com.jetbrains.python.console.PyConsoleUtil
import com.jetbrains.python.console.PythonConsoleView
import com.jetbrains.python.fixtures.PyTestCase

/**
 * Created by Yuli Fiterman on 9/20/2016.
 */
class PyConsoleEnterHandlerTest : PyTestCase() {


  lateinit private var myEditor: Editor
  lateinit private var myEnterHandler: PyConsoleEnterHandler


  override fun setUp() {
    super.setUp()
    resetEditor()
    myEnterHandler = PyConsoleEnterHandler()
  }

  private fun resetEditor() {
    val consoleView = PythonConsoleView(myFixture.project, "Console", projectDescriptor?.sdk)
    myEditor = disposeOnTearDown(consoleView).consoleEditor
    val virtFile = consoleView.virtualFile
    PyConsoleUtil.getOrCreateIPythonData(virtFile).isIPythonEnabled = true
  }

  fun push(text: String): Boolean {
    text.forEach { EditorTestUtil.performTypingAction(myEditor, it) }
    return myEnterHandler.handleEnterPressed(myEditor as EditorEx)
  }


  fun testTripleQuotes() {
    assertFalse(push("'''abs"))


  }

  fun testSimpleSingleLine() {
    assertTrue(push("a = 1"))
    resetEditor()
    assertFalse(push("for a in range(5):"))
    resetEditor()
    assertFalse(push("a = [1,\n2,"))


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
    assertTrue(push(""))

  }

  //PY-20616
  fun testEmptyBlock1() {
    assertFalse(push("def foo():"))
    assertFalse(push(""))
    assertFalse(push(""))
    assertFalse(push("print()"))
    assertTrue(push(""))
  }

  //PY-20616
  fun testEmptyBlock2() {
    assertFalse(push("if True:"))
    assertFalse(push(""))
    assertFalse(push(""))
    assertFalse(push("print()"))
    assertTrue(push(""))
  }


  override fun tearDown() {
    Disposer.dispose(testRootDisposable)
    super.tearDown()
  }


}
