// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.schema

import com.intellij.grazie.spellcheck.GrazieSpellCheckingInspection
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.jetbrains.jsonSchema.jsonSchemaHighlightingFixture
import org.jetbrains.yaml.YAMLLanguage
import org.junit.jupiter.api.Test

@TestApplication
class YamlWithSchemaSpellcheckingTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val codeInsightFixture = codeInsightModuleFixture(projectFixture)
  private val myFixture by codeInsightFixture
  private val mySchemaFixture by jsonSchemaHighlightingFixture(codeInsightFixture, "config.yml", ::YamlJsonSchemaHighlightingInspection,
                                                               YAMLLanguage.INSTANCE)

  @Test
  fun testYamlSpellcheckingWithSchema() {
    myFixture.enableInspections(GrazieSpellCheckingInspection::class.java)
    val schema = """
    {
      "properties": {
        "bugaga": {
          "enum": [
            "ququ",
            "pupu"
          ]
        },
        "ulala": {
          "type": "string"
        },
        "mememe": {
          "type": "object"
        }
      }
    }
    """.trimIndent()
    mySchemaFixture.configureInitially(schema, """
      bugaga: <warning>foo</warning>
      ulala: <warning>123</warning>
      mememe:
        <warning>- hello</warning>
    """.trimIndent(), "json")
    myFixture.checkHighlighting(true, false, true)
    mySchemaFixture.configureInitially(schema, """
      bugaga: ququ
      ulala: hello
      mememe:
         <TYPO descr="Typo: In word 'uhuhu'">uhuhu</TYPO>: <TYPO descr="Typo: In word 'bububu'">bububu</TYPO>
         <TYPO descr="Typo: In word 'ulala'">ulala</TYPO>: hello 2 # the key does not match schema and treat as type
    """.trimIndent(), "json")
    myFixture.checkHighlighting(true, false, true)
  }
}