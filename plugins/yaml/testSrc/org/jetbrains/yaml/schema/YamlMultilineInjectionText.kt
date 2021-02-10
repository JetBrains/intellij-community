// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.schema

import com.intellij.psi.util.parents
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.InjectionTestFixture
import com.intellij.util.castSafelyTo
import com.intellij.util.containers.Predicate
import com.jetbrains.jsonSchema.JsonSchemaHighlightingTestBase.registerJsonSchema
import org.jetbrains.yaml.psi.YAMLScalar

class YamlMultilineInjectionText : BasePlatformTestCase() {

  private val myInjectionFixture: InjectionTestFixture
    get() = InjectionTestFixture(myFixture)

  override fun setUp() {
    super.setUp()
    registerJsonSchema(myFixture, """
      {
        "properties": {
          "X": {
            "x-intellij-language-injection": "XML"
          }
        }
      }
    """.trimIndent(), ".json", Predicate { true })

  }

  fun testSeparateLinesQuotesXmlInjection() {
    myFixture.configureByText("test.yaml", """
        X: "<html><caret>
             <body>boo</body>
           </html>"
    """.trimIndent())

    myInjectionFixture.assertInjectedLangAtCaret("XML")
    assertInjectedAndLiteralValue("<html> <body>boo</body> </html>")
  }


  fun testFoldedXmlInjection() {
    myFixture.configureByText("test.yaml", """
        X: >
          <html><caret>
               <body>boo</body>
          </html>
    """.trimIndent())

    myInjectionFixture.assertInjectedLangAtCaret("XML")
    assertInjectedAndLiteralValue("""
      |<html>
      |     <body>boo</body>
      |</html>
      |""".trimMargin())
  }

  fun testBlockInjection() {
    myFixture.configureByText("test.yaml", """
        X: |
          <html><caret>
               <body>boo</body>
          </html>
    """.trimIndent())

    myInjectionFixture.assertInjectedLangAtCaret("XML")
    assertInjectedAndLiteralValue("""
      |<html>
      |     <body>boo</body>
      |</html>
      |""".trimMargin())

  }

  private fun assertInjectedAndLiteralValue(expectedText: String) {
    assertEquals("fragment editor should be", expectedText, myInjectionFixture.openInFragmentEditor().file.text)
    assertEquals("literal text should be", expectedText, literalTextAtTheCaret)
  }

  val literalTextAtTheCaret: String
    get() = myInjectionFixture.topLevelFile.findElementAt(myInjectionFixture.topLevelCaretPosition)
      ?.parents(true)?.mapNotNull { it.castSafelyTo<YAMLScalar>() }?.firstOrNull()?.textValue!!

}