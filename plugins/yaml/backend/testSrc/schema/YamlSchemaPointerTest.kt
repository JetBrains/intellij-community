// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.schema

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.jsonSchema.JsonSchemaMappingsProjectConfiguration
import com.jetbrains.jsonSchema.UserDefinedJsonSchemaConfiguration
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion
import junit.framework.TestCase
import org.jetbrains.yaml.JsonSchemaIdReference

class YamlSchemaPointerTest : BasePlatformTestCase() {

  override fun setUp() {
    super.setUp()
    val schema = myFixture.configureByText("schema.json", """
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

    JsonSchemaMappingsProjectConfiguration.getInstance(project).setState(mapOf(
      UserDefinedJsonSchemaConfiguration("my-schema", JsonSchemaVersion.SCHEMA_7, schema.url, false, emptyList()).let { it.name to it }
    ))
    JsonSchemaService.Impl.get(project).reset()
    psiManager.dropPsiCaches()
  }

  fun `test schema configured by ref`() {
    myFixture.configureByText("document.yaml", """
          {
            "${'$'}schema": https://example.com/schemas/address,
            <caret>
          }
        """.trimIndent()).virtualFile

    myFixture.testCompletionVariants("document.yaml", "city")
  }

  fun `test schema configured by language server comment`() {
    myFixture.configureByText("document.yaml", """
          # yaml-language-server: ${'$'}schema=https://example.com/schemas/address
          <caret>
        """.trimIndent())

    myFixture.testCompletionVariants("document.yaml", "city")
  }

  fun `test schema ref available`() {
    myFixture.configureByText("document.yaml", """
          # yaml-language-server: ${'$'}schema=https://<caret>example.com/schemas/address
          
        """.trimIndent())
    val refs = TargetElementUtil.findReference(myFixture.editor)?.let(PsiReferenceUtil::unwrapMultiReference).orEmpty()
    TestCase.assertTrue(refs.filterIsInstance<JsonSchemaIdReference>().isNotEmpty())
  }

  fun `test schema ls completion`() {
    myFixture.configureByText("document.yaml", """
          # yaml-language-server: ${'$'}schema=<caret>
          
        """.trimIndent())
    UsefulTestCase.assertContainsElements(myFixture.getCompletionVariants("document.yaml").orEmpty(),
                                          "http://json-schema.org/draft-06/schema")
  }

  fun `test schema ls completion in complex comment`() {
    myFixture.configureByText("document.yaml", """
          # Copyright 2019 The Kubernetes Authors.
          # SPDX-License-Identifier: Apache-2.0
          # yaml-language-server: ${'$'}schema=<caret>
          
          run:
            deadline: 5m
            go: '1.20'
          
          
          linters:
            enable-all: true
            disable:
              - cyclop
              - exhaustivestruct
              - forbidigo
          
        """.trimIndent())
    UsefulTestCase.assertContainsElements(myFixture.getCompletionVariants("document.yaml").orEmpty(),
                                          "http://json-schema.org/draft-06/schema")
  }

  fun `test schema ref completion`() {
    myFixture.configureByText("document.yaml", """
          # <caret>
          
        """.trimIndent())
    UsefulTestCase.assertContainsElements(myFixture.getCompletionVariants("document.yaml").orEmpty(),
                                          "\$schema: ")
    myFixture.type("\$schema: ")
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    FileDocumentManager.getInstance().saveDocument(myFixture.editor.document)
    UsefulTestCase.assertContainsElements(myFixture.getCompletionVariants("document.yaml").orEmpty(),
                                          "http://json-schema.org/draft-04/schema")
  }

  fun `test schema detected`() {
    myFixture.configureByText("document.yaml", """
          # ${"\$"}schema: https://myexternal-schema-service.com/schemas/def
          
          <caret>
          
        """.trimIndent())

    val schemaFilesForFile = JsonSchemaService.Impl.get(project).getSchemaFilesForFile(myFixture.file.virtualFile)
    UsefulTestCase.assertSameElements(schemaFilesForFile.map { it.url }, "https://myexternal-schema-service.com/schemas/def")
  }

}