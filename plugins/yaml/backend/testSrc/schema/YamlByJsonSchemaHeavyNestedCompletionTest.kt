// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.schema

import com.intellij.openapi.Disposable
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.jetbrains.jsonSchema.impl.TestSchemas.open1ThenOpen2Then3Schema
import com.jetbrains.jsonSchema.impl.TestSchemas.settingWithEnabledShorthand
import com.jetbrains.jsonSchema.impl.TestSchemas.settingWithEnabledShorthandAndCustomization
import com.jetbrains.jsonSchema.impl.addShorthandValueHandlerForEnabledField
import com.jetbrains.jsonSchema.impl.assertThatSchema
import com.jetbrains.jsonSchema.impl.nestedCompletions.buildNestedCompletionsTree
import com.jetbrains.jsonSchema.impl.testNestedCompletionsWithPredefinedCompletionsRoot
import com.jetbrains.jsonSchema.impl.withConfiguration
import com.jetbrains.jsonSchema.jsonSchemaHeavyFixture
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

@TestApplication
class YamlByJsonSchemaHeavyNestedCompletionTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val codeInsightFixture = codeInsightModuleFixture(projectFixture)
  private val myFixture by codeInsightFixture
  private val schemaFixture by jsonSchemaHeavyFixture(codeInsightFixture, "yml")

  @TestDisposable
  lateinit var testRootDisposable: Disposable

  @TempDir
  lateinit var workingFolder: File

  @BeforeEach
  fun setUp() {
    myFixture.testDataPath = workingFolder.path
  }

  @Test
  fun `testnested completion into property that does not exist yet`() {
    open1ThenOpen2Then3Schema
      .appliedToYamlFile("""
        one:
          thr<caret>
      """.trimIndent())
      .completesTo("""
        one:
          two:
            three: <selection>false<caret></selection>
      """.trimIndent())
  }

  @Test
  fun `testnested completion from empty file`() {
    open1ThenOpen2Then3Schema
      .appliedToYamlFile("""
        thr<caret>
      """.trimIndent())
      .completesTo("""
        one:
          two:
            three: <selection>false<caret></selection>
      """.trimIndent())
  }

  @Test
  fun `testnested completion between properties`() {
    open1ThenOpen2Then3Schema
      .appliedToYamlFile("""
        one:
          four: 4
        thr<caret>
        five:
          six: 6
      """.trimIndent())
      .completesTo("""
        one:
          four: 4
          two:
            three: <selection>false<caret></selection>
        five:
          six: 6
      """.trimIndent())
  }

  @Test
  fun `testnested completion into existing property`() {
    open1ThenOpen2Then3Schema
      .appliedToYamlFile("""
        one:
          thr<caret>
          two:
            foo: bar
      """.trimIndent())
      .completesTo("""
        one:
          two:
            three: <selection>false<caret></selection>
            foo: bar
      """.trimIndent())
  }

  @Test
  fun `testnested completion into property already exists with sub-property that does not exist yet`() {
    open1ThenOpen2Then3Schema
      .appliedToYamlFile("""
        thr<caret>
        one:
          foo: bar
      """.trimIndent())
      .completesTo("""
        one:
          two:
            three: <selection>false<caret></selection>
          foo: bar
      """.trimIndent())
  }

  @Test
  fun `testarray item completion into existing property`() {
    assertThatSchema("""
      {
        "properties": {
          "foo": {
            "properties": {
              "arr": {
                "type": "array"
              }
            }
          }
        }
      }
    """.trimIndent())
      .withConfiguration(
        buildNestedCompletionsTree {
          open("foo")
        }
      )
      .appliedToYamlFile("""
        arr<caret>
        foo:
          bar: baz
      """.trimIndent())
      .completesTo("""
        foo:
          arr:
            - <caret>
          bar: baz
      """.trimIndent())
  }

  @Test
  fun `testnested array item completion into existing property`() {
    assertThatSchema("""
      {
        "properties": {
          "foo": {
            "properties": {
              "bar": {
                "properties": {
                  "arr": {
                    "type": "array"
                  }
                }
              }
            }
          }
        }
      }
    """.trimIndent())
      .withConfiguration(
        buildNestedCompletionsTree {
          open("foo") {
            open("bar")
          }
        }
      )
      .appliedToYamlFile("""
        arr<caret>
        foo:
          baz: 42
      """.trimIndent())
      .completesTo("""
        foo:
          bar:
            arr:
              - <caret>
          baz: 42
      """.trimIndent())
  }

  @Test
  fun `testarray item completion into existing property while inside a property`() {
    assertThatSchema("""
      {
        "properties": {
          "nested": {
            "properties": {
              "foo": {
                "properties": {
                  "arr": {
                    "type": "array"
                  }
                }
              }
            }
          }
        }
      }
    """.trimIndent())
      .withConfiguration(
        buildNestedCompletionsTree {
          open("nested") {
            open("foo")
          }
        }
      )
      .appliedToYamlFile("""
        nested:
          arr<caret>
          foo:
            bar: baz
      """.trimIndent())
      .completesTo("""
        nested:
          foo:
            arr:
              - <caret>
            bar: baz
      """.trimIndent())
  }

  @Test
  fun `testthat completing while being below the destination, inserts the completion at the bottom`() {
    open1ThenOpen2Then3Schema
      .appliedToYamlFile("""
        one:
          two:
            foo: false
        thr<caret>
      """.trimIndent())
      .completesTo("""
        one:
          two:
            foo: false
            three: <selection>false<caret></selection>
      """.trimIndent())

    open1ThenOpen2Then3Schema
      .appliedToYamlFile("""
        one:
          two:
            foo: false
          twosBrother: 4
        onesBrother: 2
        thr<caret>
      """.trimIndent())
      .completesTo("""
        one:
          two:
            foo: false
            three: <selection>false<caret></selection>
          twosBrother: 4
        onesBrother: 2
      """.trimIndent())

    open1ThenOpen2Then3Schema
      .appliedToYamlFile("""
        one:
          two:
            foo: false
          twosBrother: 4
          thr<caret>
        onesBrother: 2
      """.trimIndent())
      .completesTo("""
        one:
          two:
            foo: false
            three: <selection>false<caret></selection>
          twosBrother: 4
        onesBrother: 2
      """.trimIndent())

    open1ThenOpen2Then3Schema
      .appliedToYamlFile("""
        one:
          twosBrother: 4
        onesBrother: 2
        thr<caret>
      """.trimIndent())
      .completesTo("""
        one:
          twosBrother: 4
          two:
            three: <selection>false<caret></selection>
        onesBrother: 2
      """.trimIndent())
  }

  @Test
  fun `testnested completion leads to expanding - single level nestedness`() {
    addShorthandValueHandlerForEnabledField(testRootDisposable)

    settingWithEnabledShorthand
      .appliedToYamlFile("""
        setting: enabled
        val<caret>
      """.trimIndent())
      .completesTo("""
        setting:
          enabled: true
          value: <caret>
      """.trimIndent())
  }

  @Test
  fun `testnested completion leads to expanding - multiple level nestedness`() {
    addShorthandValueHandlerForEnabledField(testRootDisposable)

    settingWithEnabledShorthandAndCustomization
      .appliedToYamlFile("""
        setting: enabled
        val<caret>
      """.trimIndent())
      .completesTo("""
        setting:
          enabled: true
          customization:
            value: <caret>
      """.trimIndent())
  }

  private fun JsonSchemaYamlSetup.completesTo(@Language("YAML") expectedResult: String) {
    workingFolder.resolve("Schema.json").createTemporarilyWithContent(schemaSetup.schemaJson) {
      workingFolder.resolve("test.yml").createTemporarilyWithContent(yaml) {
        workingFolder.resolve("test_after.yml").createTemporarilyWithContent(expectedResult) {
          testNestedCompletionsWithPredefinedCompletionsRoot(schemaSetup.predefinedNestedCompletionsRoot) {
            schemaFixture.baseInsertTest(".", "test")
          }
        }
      }
    }
  }
}


private inline fun File.createTemporarilyWithContent(content: String, block: () -> Unit) {
  try {
    writeText(content)
    block()
  }
  finally {
    delete()
  }
}
