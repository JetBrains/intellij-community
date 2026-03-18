// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.intentions

import com.intellij.modcommand.ModCommand
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.fixtures.PyTestCase

class PyCopyStringLiteralToClipboardIntentionTest : PyTestCase() {
  fun `test simple literal`() {
    doTest("""x = "hello, <caret>world" """, "hello, world")
  }

  fun `test escape sequences`() {
    doTest("""x = "line1\nline<caret>2" """, "line1\nline2")
  }

  fun `test implicit concatenation`() {
    doTest("""x = "foo<caret>" "bar" """, "foobar")
  }

  fun `test raw string`() {
    doTest("""x = r"<caret>\n" """, "\\n")
  }

  fun `test f-string no fragments`() {
    doTest("""x = f"hell<caret>o" """, "hello")
  }

  fun `test f-string with expression`() {
    doTest("""x = f"Hello, <caret>{name}!" """, "Hello, ?!")
  }

  fun `test f-string multiple expressions`() {
    doTest("""x = f"<caret>{a} + {b} = {a + b}" """, "? + ? = ?")
  }

  fun `test f-string mixed with plain`() {
    doTest("""x = f"Hello <caret>{name}!" " World" """, "Hello ?! World")
  }

  fun `test f-string escaped brace`() {
    doTest("""x = f"{{<caret>literal}}" """, "{literal}")
  }

  fun `test multiline string`() {
    doTest("""
        x = f'''li<caret>ne1{interp1}
        line2{interp2}
        '''
        """.trimIndent(), "line1?\nline2?\n")
  }

  fun `test format part is stripped`() {
    doTest("""f"Hello, {name!r:0>+#015,.2f}! Welcome."""", "Hello, ?! Welcome.")
  }

  private fun doTest(code: String, expected: String) {
    val hint = PyPsiBundle.message("INTN.NAME.copy.string.to.clipboard")
    myFixture.configureByText("test.py", code)
    val action = checkNotNull(myFixture.findSingleIntention(hint).asModCommandAction())
    assertEquals(ModCommand.copyToClipboard(expected), action.perform(myFixture.actionContext))
  }
}
