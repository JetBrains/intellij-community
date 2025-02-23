// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.schema

import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.JsonSchemaHeavyAbstractTest
import com.jetbrains.jsonSchema.UserDefinedJsonSchemaConfiguration
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion

class YamlSchemaReferenceTest : JsonSchemaHeavyAbstractTest() {
  override fun getBasePath(): String = "" // unused

  fun testReferencingSchemaInYaml() {
    lateinit var schema: VirtualFile
    skeleton(object : Callback {
      override fun doCheck() {
        assertStringItems("city")
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
        addSchema(UserDefinedJsonSchemaConfiguration("my-schema", JsonSchemaVersion.SCHEMA_7, schema.url, false, emptyList()))
      }
    })
  }
}