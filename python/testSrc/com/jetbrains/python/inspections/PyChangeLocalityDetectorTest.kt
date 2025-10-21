package com.jetbrains.python.inspections

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.psi.PyFile
import junit.framework.TestCase

class PyChangeLocalityDetectorTest : PyTestCase() {

  fun `test top level function body change`() = checkChangeScope("""
        def f():
            <selection>x = 1</selection>
    """, null)

  fun `test top level function whitespace at the start change`() = checkChangeScope("""
        <selection>
        </selection>def f():
            x = 1
        
        def bar():
            pass
    """.trimIndent(), """
        
        
        def f():
            x = 1
        
        def bar():
            pass
    """.trimIndent())

  fun `test top level function whitespace at the end change`() = checkChangeScope("""
        def f():
            x = 1<selection>
        
        </selection>def bar():
            pass
    """.trimIndent(), """
        def f():
            x = 1
        
        def bar():
            pass
    """.trimIndent())

  fun `test top level class whitespace at the end change`() = checkChangeScope("""
        class A:
            x = 1<selection>
        
        </selection>def bar():
            pass
    """.trimIndent(), """
        class A:
            x = 1
        
        def bar():
            pass
    """.trimIndent())

  private fun checkChangeScope(text: String, expectedScope: String?) {
    myFixture.configureByText("main.py", text)

    val selectionModel = myFixture.editor.selectionModel
    assertTrue("changed item has to be specified in <selection></selection>", selectionModel.hasSelection())

    var element = PsiTreeUtil.findElementOfClassAtRange(
      myFixture.file,
      selectionModel.selectionStart,
      selectionModel.selectionEnd,
      PsiElement::class.java
    ) ?: error("No PsiElement at selection range")

    val changeLocalityDetector = PyChangeLocalityDetector()

    /**
     * Emulate [com.intellij.codeInsight.daemon.impl.PsiChangeHandler.updateByChange] logic
     * by walking up the tree and calling [com.intellij.codeInsight.daemon.ChangeLocalityDetector.getChangeHighlightingDirtyScopeFor]
     * until non-null is returned
     */
    while (element !is PyFile) {
      val dirtyScope = changeLocalityDetector.getChangeHighlightingDirtyScopeFor(element)
      if (dirtyScope != null) {
        if (expectedScope == null) {
          throw IllegalStateException("scope was calculated when no scope was expected")
        }

        TestCase.assertEquals(expectedScope.trimIndent(), dirtyScope.text)
        return
      }
      element = element.parent
    }

    if (expectedScope != null) {
      throw IllegalStateException("scope wasn't calculated")
    }
  }
}
