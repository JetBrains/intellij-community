// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.schema

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.spellchecker.inspections.SpellCheckingInspection
import com.jetbrains.jsonSchema.JsonSchemaHighlightingTestBase
import org.jetbrains.yaml.YAMLLanguage
import java.util.function.Predicate

class YamlWithSchemaSpellcheckingTest : JsonSchemaHighlightingTestBase() {
  override fun getTestDataPath(): String {
    return PathManagerEx.getCommunityHomePath() + "/plugins/yaml/testSrc/org/jetbrains/yaml/schema/data/highlighting"
  }

  override fun getTestFileName(): String {
    return "config.yml"
  }

  override fun getInspectionProfile(): InspectionProfileEntry {
    return YamlJsonSchemaHighlightingInspection()
  }

  override fun getAvailabilityPredicate(): Predicate<VirtualFile> {
    return Predicate { file: VirtualFile ->
      file.fileType is LanguageFileType && (file.fileType as LanguageFileType).language.isKindOf(
        YAMLLanguage.INSTANCE)
    }
  }

  fun testYamlSpellcheckingWithSchema() {
    myFixture.enableInspections(SpellCheckingInspection::class.java)
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
    configureInitially(schema, """
      bugaga: <warning>foo</warning>
      ulala: <warning>123</warning>
      mememe:
        <warning>- hello</warning>
    """.trimIndent(), "json")
    myFixture.checkHighlighting(true, false, true)
    configureInitially(schema, """
      bugaga: ququ
      ulala: hello
      mememe:
         <TYPO descr="Typo: In word 'uhuhu'">uhuhu</TYPO>: <TYPO descr="Typo: In word 'bububu'">bububu</TYPO>
         <TYPO descr="Typo: In word 'ulala'">ulala</TYPO>: hello 2 # the key does not match schema and treat as type
    """.trimIndent(), "json")
    myFixture.checkHighlighting(true, false, true)
  }
}