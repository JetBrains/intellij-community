// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.TextRange
import com.jetbrains.python.codeInsight.typing.PyTypeShed
import com.jetbrains.python.codeInsight.userSkeletons.PyUserSkeletonsUtil.getUserSkeletonsDirectory
import com.jetbrains.python.debugger.PyLineBreakpointType
import com.jetbrains.python.fixtures.PyTestCase

class PyLineBreakpointTypeTest : PyTestCase() {

  // PY-16932
  fun testPutAtUserSkeleton() {
    val skeletonsDir = getUserSkeletonsDirectory()
    val pythonFile = skeletonsDir!!.findFileByRelativePath("lettuce/terrain.py")
    val line = 20

    val document = FileDocumentManager.getInstance().getDocument(pythonFile!!)
    val range = TextRange.create(document!!.getLineStartOffset(line), document.getLineEndOffset(line))
    assertEquals("        pass", document.getText(range))

    assertFalse(PyLineBreakpointType().canPutAt(pythonFile, line, myFixture.project))
  }

  // PY-16932
  fun testPutAtSkeleton() {
    runWithAdditionalFileInSkeletonDir("my_mod.py", "class A:\n    def method(self):\n        print(\"ok\")") { pythonFile ->
      val line = 2

      val document = FileDocumentManager.getInstance().getDocument(pythonFile)
      val range = TextRange.create(document!!.getLineStartOffset(line), document.getLineEndOffset(line))
      assertEquals("        print(\"ok\")", document.getText(range))

      assertFalse(PyLineBreakpointType().canPutAt(pythonFile, line, myFixture.project))
    }
  }

  // PY-16932
  fun testPutAtPythonStub() {
    val pythonFile = PyTypeShed.directory!!.findFileByRelativePath("stdlib/2/__builtin__.pyi")
    val line = 29

    val document = FileDocumentManager.getInstance().getDocument(pythonFile!!)
    val range = TextRange.create(document!!.getLineStartOffset(line), document.getLineEndOffset(line))
    assertEquals("_T = TypeVar('_T')", document.getText(range))

    assertFalse(PyLineBreakpointType().canPutAt(pythonFile, line, myFixture.project))
  }

  fun testCorrect() {
    val pythonFile = myFixture.configureByText(PythonFileType.INSTANCE, "def foo():\n" +
                                                                        "    print(1)").virtualFile
    val line = 1

    val document = FileDocumentManager.getInstance().getDocument(pythonFile!!)
    val range = TextRange.create(document!!.getLineStartOffset(line), document.getLineEndOffset(line))
    assertEquals("    print(1)", document.getText(range))

    assertTrue(PyLineBreakpointType().canPutAt(pythonFile, line, myFixture.project))
  }
}
