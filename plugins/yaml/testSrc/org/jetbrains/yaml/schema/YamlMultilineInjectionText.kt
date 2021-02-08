// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.schema

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.InjectionTestFixture
import com.intellij.util.containers.Predicate
import com.jetbrains.jsonSchema.JsonSchemaHighlightingTestBase
import com.jetbrains.jsonSchema.JsonSchemaHighlightingTestBase.registerJsonSchema
import junit.framework.TestCase

class YamlMultilineInjectionText : BasePlatformTestCase() {

  val myInjectionFixture: InjectionTestFixture
    get() = InjectionTestFixture(myFixture)

  fun testSeparateLinesQuotesXmlInjection() {
    registerJsonSchema(myFixture, """
      {
        "properties": {
          "X": {
            "x-intellij-language-injection": "XML"
          }
        }
      }
    """.trimIndent(), ".json", Predicate { true })

    myFixture.configureByText("test.yaml", """
        X: "<html><caret>
               <body>boo</body>
            </html>"
    """.trimIndent())

    myInjectionFixture.assertInjectedLangAtCaret("XML")
    TestCase.assertEquals("", myInjectionFixture.injectedElement?.containingFile?.text)
  }


}