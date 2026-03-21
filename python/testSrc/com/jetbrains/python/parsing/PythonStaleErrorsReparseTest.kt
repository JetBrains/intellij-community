// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.parsing

import com.intellij.openapi.application.runUndoTransparentWriteAction
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.fixtures.PyTestCase

/**
 * Tests that incremental reparse does not leave a structurally incorrect PSI tree
 * when a cascading error (e.g., a broken string literal) reshuffles statement list
 * boundaries and is then fixed.
 *
 * @see com.jetbrains.python.psi.impl.PyStatementListElementType.isReparseable
 */
class PythonStaleErrorsReparseTest : PyTestCase() {

  companion object {
    private const val REGISTRY_KEY = "python.statement.lists.incremental.reparse"
  }

  /**
   * Insert `"` before a condition containing a string literal.
   * The quote opens a string that eats code until the next `"`,
   * cascading errors across nesting levels.
   */
  fun testInsertQuoteBeforeConditionWithStringLiteral() = doInsertAndRemoveCharTest(
    source = """
      class C:
          def m(self):
              if x == "value" and y:
                  return 1
              return 0
          def other(self):
              return 2
    """,
    anchor = "        if ",
    charToInsert = "\""
  )

  /** Same pattern in deeper nesting: class -> method -> elif -> if. */
  fun testInsertQuoteInDeeplyNestedCondition() = doInsertAndRemoveCharTest(
    source = """
      class C:
          def m(self, t):
              if t is None:
                  pass
              elif isinstance(t, str):
                  if t == "Annotated" and len(t) > 0:
                      return t
                  else:
                      return None
              return t
          def other(self):
              return 0
    """,
    anchor = "            if t == ",
    charToInsert = "\""
  )

  /**
   * Delete the opening quote of a string — turns `"START"` into `START"`
   * (bare name + unterminated string), then restore it.
   */
  fun testDeleteOpeningQuoteOfStringLiteral() = withIncrementalReparse {
    myFixture.configureByText("test.py", """
      class C:
          def m(self):
              marker = "START"
              return marker
          def other(self):
              return 0
    """)
    val document = commitAndGetDocument()
    assertNoErrors("Initial")

    runUndoTransparentWriteAction { document.deleteString(document.text.indexOf("\"START\""), document.text.indexOf("\"START\"") + 1) }
    commit()
    assertHasErrors("After break")

    runUndoTransparentWriteAction { document.insertString(document.text.indexOf("START\""), "\"") }
    commit()
    assertNoErrors("After fix")
  }

  /** Insert a colon that turns a statement into a block header, reshuffling statement lists. */
  fun testInsertColonCreatesPhantomBlock() = doInsertAndRemoveCharTest(
    source = """
      class C:
          def m(self):
              x = compute()
              y = x + 1
              return y
          def other(self):
              return 0
    """,
    anchor = "        x = compute()",
    charToInsert = ":"
  )

  /** Break a function signature by inserting a quote, then fix it. */
  fun testBreakFunctionSignature() = doInsertAndRemoveCharTest(
    source = """
      class C:
          def process(self, data):
              result = data.strip()
              return result
          def validate(self):
              return True
    """,
    anchor = "    def process(self",
    charToInsert = "\""
  )

  private fun doInsertAndRemoveCharTest(source: String, anchor: String, charToInsert: String) =
    withIncrementalReparse {
      myFixture.configureByText("test.py", source)
      val document = commitAndGetDocument()
      assertNoErrors("Initial")

      runUndoTransparentWriteAction {
        val pos = document.text.indexOf(anchor)
        assertTrue("Could not find: $anchor", pos >= 0)
        document.insertString(pos + anchor.length, charToInsert)
      }
      commit()
      assertHasErrors("After break")

      runUndoTransparentWriteAction {
        val pos = document.text.indexOf(anchor + charToInsert)
        assertTrue("Could not find broken anchor", pos >= 0)
        document.deleteString(pos + anchor.length, pos + anchor.length + charToInsert.length)
      }
      commit()
      assertNoErrors("After fix")
    }

  private fun withIncrementalReparse(action: () -> Unit) {
    val original = Registry.`is`(REGISTRY_KEY)
    try {
      Registry.get(REGISTRY_KEY).setValue(true)
      action()
    }
    finally {
      Registry.get(REGISTRY_KEY).setValue(original)
    }
  }

  private fun commitAndGetDocument() =
    PsiDocumentManager.getInstance(myFixture.project).let { psiDocumentManager ->
      psiDocumentManager.getDocument(myFixture.file)!!
        .also { psiDocumentManager.commitDocument(it) }
    }

  private fun commit() = PsiDocumentManager.getInstance(myFixture.project).commitAllDocuments()

  private fun assertNoErrors(phase: String) {
    val error = PsiTreeUtil.findChildOfType(myFixture.file, PsiErrorElement::class.java)
    assertNull("$phase: PSI should have no errors" +
               (error?.let { "; found: ${it.errorDescription} at ${it.textOffset}" } ?: ""),
               error)
  }

  private fun assertHasErrors(phase: String) {
    assertNotNull("$phase: PSI should have errors",
                  PsiTreeUtil.findChildOfType(myFixture.file, PsiErrorElement::class.java))
  }
}
