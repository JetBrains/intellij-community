// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.highlighting.PyLocalGlobalHighlightVisitor.Holder.LG_MARKER
import java.awt.Color

class PyLocalGlobalHighlightingTest : PyTestCase() {
  fun testLocalVariable() {
    doTest("""
             def f():
                 <h local>p</h> = "1"
                 print(<h local>p</h>)
                 """.trimIndent())
  }

  fun testSameNameGlobalAndLocalVariable() {
    doTest("""
             <h global>p</h> = "1"
             def f():
                 <h local>p</h> = "2"
                 print(<h local>p</h>)
             print(<h global>p</h>)
                 """.trimIndent())
  }

  fun testSameNameGlobalVariable() {
    doTest("""
             <h global>p</h> = "1"
             def f():
                 print(<h global>p</h>)
             print(<h global>p</h>)
                 """.trimIndent())
  }

  fun testSameNameGlobalVariableWithReassignment() {
    doTest("""
             <h global>p</h> = "1"
             def f():
                 global <h global>p</h>
                 <h global>p</h> = "2"
                 print(<h global>p</h>)
             print(<h global>p</h>)
                 """.trimIndent())
  }

  fun testSameNameGlobalAndNonlocalVariable() {
    doTest("""
             <h global>p</h> = "1"
             def outer():
                 <h local>p</h> = "2"
                 def nested():
                     nonlocal <h local>p</h>
                     <h local>p</h> = "3"
                 print(<h local>p</h>)
             print(<h global>p</h>)
                 """.trimIndent())
  }

  fun testSameNameGlobalAndAmbiguityVariable() {
    doTest("""
             <h global>p</h> = "1"
             def f():
                 print(p) # ambiguity - "UnboundLocalError: local variable 'p' referenced before assignment"
                 <h local>p</h> = "2"
                 print(<h local>p</h>)
             print(<h global>p</h>)
                 """.trimIndent())
  }

  fun testSameNameOuterAndNonLocalVariable() {
    doTest("""
            def outer():
                <h local>p</h> = "1"
                def nested():
                    nonlocal <h local>p</h>
                    print(<h local>p</h>)
                    <h local>p</h> = "2"
                    print(<h local>p</h>
                print(<h local>p</h>)
                 """.trimIndent())
  }

  fun testSameNameGlobalVariableAndParameter() {
    doTest("""
             <h global>p</h> = "1"
             def f(p):
                 print(p)
             print(<h global>p</h>)
                 """.trimIndent())
  }

  fun testSameNameLocalVariableAndParameter() {
    doTest("""
             <h global>p</h> = "1"
             def f(p):
                 <h local>p</h> = "1"
                 print(<h local>p</h>)
             print(<h global>p</h>)
                 """.trimIndent())
  }

  fun testSameNameAttributeAndLocalVariableAndParameter() {
    doTest("""
             class A:
                 def __init__(self):
                     self.a = None

                 def f(self, a):
                     self.a = 2
                     <h local>a</h> = a
                     print(<h local>a</h>)
                 """.trimIndent())
  }

  fun testVariableAfterAugAssignment() {
    doTest("""
            def f():
                <h local>p</h> = 1
                <h local>p</h> += 1
                print(<h local>p</h>)
             """.trimIndent())
  }

  private fun doTest(text: String) {
    val scheme = EditorColorsManager.getInstance().globalScheme
    val lAttributes = scheme.getAttributes(DefaultLanguageHighlighterColors.LOCAL_VARIABLE)
    val gAttributes = scheme.getAttributes(DefaultLanguageHighlighterColors.GLOBAL_VARIABLE)
    scheme.setAttributes(DefaultLanguageHighlighterColors.LOCAL_VARIABLE, TextAttributes().apply { foregroundColor = Color.RED })
    scheme.setAttributes(DefaultLanguageHighlighterColors.GLOBAL_VARIABLE, TextAttributes().apply { foregroundColor = Color.GREEN })

    try {
      val file = myFixture.configureByText("a.py", text.replace("<h [a-z]+>(.*)</h>".toRegex(), "$1"))
      val highlighting = myFixture.doHighlighting().filter { it.type == LG_MARKER }
      assertEquals(
        text,
        CodeInsightTestFixtureImpl.getTagsFromSegments(myFixture.getDocument(file).text, highlighting, "h") {
          when (it.getTextAttributes(null, null)?.foregroundColor) {
            Color.RED -> "local"
            Color.GREEN -> "global"
            else -> "?"
          }
        })
    }
    finally {
      scheme.setAttributes(DefaultLanguageHighlighterColors.LOCAL_VARIABLE, lAttributes)
      scheme.setAttributes(DefaultLanguageHighlighterColors.GLOBAL_VARIABLE, gAttributes)
    }
  }
}