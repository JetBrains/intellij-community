// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.intention

import com.intellij.codeInsight.intention.impl.ShowIntentionActionsHandler
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.jsonSchema.JsonSchemaHighlightingTestBase
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import com.jetbrains.jsonSchema.impl.fixes.AddOptionalPropertiesIntention
import org.intellij.lang.annotations.Language
import org.jetbrains.yaml.intentions.YAMLAddOptionalPropertiesIntention
import org.junit.Assert

class YAMLAddOptionalPropertiesIntentionTest : BasePlatformTestCase() {
  private fun addSchema() {
    JsonSchemaHighlightingTestBase.registerJsonSchema(
      myFixture,
      """
        {
          "${"$"}schema":"http://json-schema.org/draft-04/schema#", 
          "properties": {
            "aa": {
              "type": "object",
              "properties": {
                "bb" : {
                  "type": "object",
                  "properties": {
                    "cc": {
                      "type": "string"
                    },
                    "dd": {
                      "type": "integer"
                    }
                  }
                }
              }
            },
            "ee": {
              "type": "object",
              "example": {
                "ff": {
                  "gg": 12345
                }
              },
              "properties": {
                "ff": {
                  "type": "object",
                  "properties": {
                    "gg": {
                      "type": "integer"
                    }
                  }
                }
              }
            }
          }
        }
      """.trimIndent(),
      "json"
    ) { true }
  }

  private fun ensureSchemaIsCached(json: PsiFile) {
    Assert.assertNotNull(JsonSchemaService.Impl.get(myFixture.project).getSchemaObject(json))
  }

  private fun doTest(@Language("yaml") before: String, @Language("yaml") after: String) {
    addSchema()

    val yaml = myFixture.configureByText("test.yaml", before)
    Assert.assertNotNull(JsonSchemaService.Impl.get(myFixture.project).getSchemaObject(yaml))
    ensureSchemaIsCached(yaml)
    val intention = myFixture.findSingleIntention(YAMLAddOptionalPropertiesIntention().text)
    ShowIntentionActionsHandler.chooseActionAndInvoke(myFixture.file, myFixture.editor, intention, intention.text)

    ensureSchemaIsCached(yaml)
    val previewText = myFixture.getIntentionPreviewText(intention)
    Assert.assertEquals(after, previewText)
    myFixture.checkResult(after)
  }

  fun `test insert properties in non-empty object`() {
    doTest(
      """
        some<caret>thing: 
          else:
        
      """.trimIndent(),
      """
        something:
          else:
        
        aa:
        ee:
        
      """.trimIndent()
    )
  }

  fun `test insert properties in deep object`() {
    doTest(
      """
        aa:
          bb:
            some<caret>thing: 
      """.trimIndent(),
      """
        aa:
          bb:
            something:
            cc:
            dd: 0
      """.trimIndent()
    )
  }


  fun `test intention unavailable if all properties are present`() {
    JsonSchemaHighlightingTestBase.registerJsonSchema(myFixture, """
      {
        "properties": {
          "aa" : {}
        }
      }
    """.trimIndent(), "json") { true }
    myFixture.configureByText("test.yaml", """
      a<caret>a:
    """.trimIndent())
    Assert.assertNull(myFixture.getAvailableIntention(AddOptionalPropertiesIntention().text))
  }

  fun `test add example from schema as default value`() {
    doTest(
      """
        ee:
          some<caret>thing:
      """.trimIndent(),
      """
        ee:
          something:
          ff:
            gg: 12345
      """.trimIndent()
    )
  }
}
