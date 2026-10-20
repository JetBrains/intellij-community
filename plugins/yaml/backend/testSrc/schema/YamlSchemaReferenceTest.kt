// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.schema

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.jetbrains.jsonSchema.JsonSchemaHeavyTestFixture
import com.jetbrains.jsonSchema.UserDefinedJsonSchemaConfiguration
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion
import com.jetbrains.jsonSchema.jsonSchemaHeavyFixture
import org.junit.jupiter.api.Test

@TestApplication
class YamlSchemaReferenceTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val codeInsightFixture = codeInsightModuleFixture(projectFixture)
  private val myFixture by codeInsightFixture
  private val schemaFixture by jsonSchemaHeavyFixture(codeInsightFixture)

  @Test
  fun testReferencingSchemaInYaml() {
    lateinit var schema: VirtualFile
    schemaFixture.skeleton(object : JsonSchemaHeavyTestFixture.Callback {
      override fun doCheck() {
        schemaFixture.assertStringItems("city")
      }

      override fun configureFiles() {
        schema = myFixture.configureByText("schema.json", """
          {
            "${'$'}id": "https://example.com/schemas/address",
            "${'$'}schema": "https://json-schema.org/draft/2020-12/schema",
            "type": "object",
            "properties": {
              "city": { "type": "string" }
            },
            "required": ["city"]
          }
        """.trimIndent()).virtualFile
        myFixture.configureByText("document.yaml", """
          {
            "${'$'}schema": https://example.com/schemas/address,
            <caret>
          }
        """.trimIndent()).virtualFile
      }

      override fun registerSchemes() {
        schemaFixture.addSchema(UserDefinedJsonSchemaConfiguration("my-schema", JsonSchemaVersion.SCHEMA_7, schema.url, false, emptyList()))
      }
    })
  }
}
