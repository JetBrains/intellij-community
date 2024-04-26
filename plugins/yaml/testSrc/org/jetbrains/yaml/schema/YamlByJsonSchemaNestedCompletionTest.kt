// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.schema

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.jetbrains.jsonSchema.impl.*
import com.jetbrains.jsonSchema.impl.nestedCompletions.buildNestedCompletionsTree
import org.intellij.lang.annotations.Language

class YamlByJsonSchemaNestedCompletionTest : JsonBySchemaCompletionBaseTest() {
  fun `test simple nested completion`() {
    open1ThenOpen2Then3Schema
      .appliedToYamlFile("""
        one:
          <caret>
      """.trimIndent())
      .hasCompletionVariantsAtCaret(
        "two.three",
        "two",
      )
  }

  fun `test does not complete existing key`() {
    open1ThenOpen2Then3Schema
      .appliedToYamlFile("""
        <caret>
        one:
          two:
            bla: false
      """.trimIndent())
      .hasCompletionVariantsAtCaret(
        "one.two.three",
      )
  }

  fun `test that nodes that already exist aren't completed`() {
    open1ThenOpen2Then3Schema
      .appliedToYamlFile("""
        one:
          <caret>
          two:
            foo: bar
      """.trimIndent())
      .hasCompletionVariantsAtCaret(
        "two.three",
      )
  }

  fun `test that nested nodes are not completed if it's not configured`() {
    assertThatSchema("""
      {
        "properties": {
          "one": {
            "properties": {
              "two": {
                "type": "boolean"
              }
            }
          }
        }
      }
    """.trimIndent())
      .appliedToYamlFile("""
        <caret>
      """.trimIndent())
      .hasCompletionVariantsAtCaret(
        "one",
      )
  }

  fun `test that nested nodes are not completed if the node is isolated`() {
    assertThatSchema("""
      {
        "properties": {
          "one": {
            "properties": {
              "two": {
                "type": "boolean"
              }
            }
          }
        }
      }
    """.trimIndent())
      .withConfiguration(
        buildNestedCompletionsTree {
          isolated("one") {}
        }
      )
      .appliedToYamlFile("""
        <caret>
      """.trimIndent())
      .hasCompletionVariantsAtCaret(
        "one",
      )
  }

  fun `test completions can start in isolated regex nodes`() {
    val twoThreePropertyJsonText = """{
            "properties": {
              "two": {
                "properties": {
                  "three": {
                    "type": "boolean"
                  }
                }
              }
            }
          }"""
    assertThatSchema("""
      {
        "properties": {
          "one@foo": $twoThreePropertyJsonText,
          "one@bar": $twoThreePropertyJsonText,
          "one@baz": $twoThreePropertyJsonText
        }
      }
    """.trimIndent())
      .withConfiguration(
        buildNestedCompletionsTree {
          isolated("one@(foo|bar)".toRegex()) {
            open("two")
          }
        }
      )
      .appliedToYamlFile("""
        one@foo:
          <caret>
      """.trimIndent())
      .hasCompletionVariantsAtCaret(
        "two.three",
        "two",
      )

      .appliedToYamlFile("""
        one@bar:
          <caret>
      """.trimIndent())
      .hasCompletionVariantsAtCaret(
        "two.three",
        "two",
      )

      .appliedToYamlFile("""
        one@baz:
          <caret>
      """.trimIndent())
      .hasCompletionVariantsAtCaret(
        "two",
      )
  }

  fun `test nested completions stop at an isolated node`() {
    assertThatSchema("""
      {
        "properties": {
          "one": {
            "properties": {
              "two": {
                "properties": {
                  "three": {
                    "properties": {
                      "four": {
                        "type": "boolean"
                      }
                    }
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
          open("one") {
            isolated("two") {
              open("three")
            }
          }
        }
      )
      .appliedToYamlFile("""
        <caret>
      """.trimIndent())
      .hasCompletionVariantsAtCaret(
        "one",
        "one.two",
      )

      .appliedToYamlFile("""
        one:
          <caret>
      """.trimIndent())
      .hasCompletionVariantsAtCaret(
        "two",
      )

      .appliedToYamlFile("""
        one:
          two:
            <caret>
      """.trimIndent())
      .hasCompletionVariantsAtCaret(
        "three.four",
        "three",
      )
  }

  private fun JsonSchemaYamlSetup.hasCompletionVariantsAtCaret(vararg expectedVariants: String): JsonSchemaSetup {
    testNestedCompletionsWithPredefinedCompletionsRoot(schemaSetup.predefinedNestedCompletionsRoot) {
      testBySchema(
        schemaSetup.schemaJson,
        yaml,
        "yaml",
        { it.renderedText()!! },
        *expectedVariants,
      )
    }
    return schemaSetup
  }
}

internal data class JsonSchemaYamlSetup(val schemaSetup: JsonSchemaSetup, @Language("YAML") val yaml: String)

internal fun JsonSchemaSetup.appliedToYamlFile(@Language("YAML") yaml: String) = JsonSchemaYamlSetup(this, yaml)

private fun LookupElement.renderedText(): String? =
  LookupElementPresentation()
    .renderedBy(this)
    .itemText

private fun LookupElementPresentation.renderedBy(element: LookupElement): LookupElementPresentation =
  copied().also { element.renderInto(it) }

private fun LookupElementPresentation.copied(): LookupElementPresentation = LookupElementPresentation().also { this.copiedInto(it) }

private fun LookupElementPresentation.copiedInto(presentation: LookupElementPresentation) {
  copyFrom(presentation)
}

private fun LookupElement.renderInto(renderedPresentation: LookupElementPresentation) {
  renderElement(renderedPresentation)
}
