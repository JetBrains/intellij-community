// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.schema

import com.intellij.codeInsight.navigation.action.GotoDeclarationUtil
import com.intellij.json.psi.JsonProperty
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.util.asSafely
import com.jetbrains.jsonSchema.newerFormats.JsonSchemaVersionTestBase
import com.jetbrains.jsonSchema.newerFormats.TestDataLanguage
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.junit.Assert

internal class YamlJsonSchema2020FeaturesTest : JsonSchemaVersionTestBase() {
  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(YamlJsonSchemaHighlightingInspection::class.java)
  }

  override val testDataLanguage: TestDataLanguage
    get() = TestDataLanguage.YAML

  override fun loadSchemaTextFromResources(schemaName: String): String {
    val resource = this.javaClass.classLoader.getResource("org/jetbrains/yaml/schema/data/newerFormats/$schemaName")
    Assert.assertNotNull(resource)
    return resource!!.readText()
  }

  private fun getResolvedElementText(elementAtCaret: PsiElement): String {
    val leafElementAtCaret = elementAtCaret.asSafely<YAMLKeyValue>()?.key
    Assert.assertTrue(leafElementAtCaret is LeafPsiElement)
    val target = GotoDeclarationUtil.findTargetElementsFromProviders(leafElementAtCaret,
                                                                     myFixture.elementAtCaret.textOffset,
                                                                     myFixture.editor)
    Assert.assertNotNull(target)
    Assert.assertEquals(1, target!!.size)
    val singleTargetParent = target.single().parent
    Assert.assertTrue(singleTargetParent is JsonProperty)
    return singleTargetParent.text
  }

  fun `test if-then-else validation with inlined branch schemas`() {
    doTestSchemaValidation(
      """
        {
          "type": "object",
          "properties": {
            "street_address": {
              "type": "string"
            },
            "country": {
              "enum": ["United States of America", "Canada", "Netherlands"]
            }
          },
          "allOf": [
            {
              "if": {
                "required": ["firstBranch"]
              },
              "then": {
                "properties": { "postal_code": { "pattern": "[0-9]{5}(-[0-9]{4})?" } }
              }
            },
            {
              "if": {
                "required": ["secondBranch"]
              },
              "then": {
                "properties": { "postal_code": { "pattern": "[A-Z][0-9][A-Z] [0-9][A-Z][0-9]" } }
              }
            },
            {
              "if": {
                "required": ["thirdBranch"]
              },
              "then": {
                "properties": { "postal_code": { "pattern": "[0-9]{4} [A-Z]{2}" } }
              }
            }
          ]
        }
      """.trimIndent(),
      """
        thirdBranch: true
        postal_code: <warning descr="Schema validation: String violates the pattern: '[0-9]{4} [A-Z]{2}'">"20500"</warning>
      """.trimIndent()
    )
  }

  fun `test gtd from yaml to top level if-then-else branch`() {
    doTestGtdWithSingleTarget(
      loadSchemaTextFromResources("IfElseSchemaWithDepth1.json"),

      """
          buz:
            bar_<caret>additional: false
            bar_required: 123
        """.trimIndent()
        to
        """
          "bar_additional": {
                    "type": "boolean"
                  }
        """.trimIndent(),

      """
          buz:
            foo_<caret>additional: false
            foo_required: 123
        """.trimIndent()
        to
        """
          "foo_additional": {
                    "type": "string"
                  }
        """.trimIndent()
    ) {
      getResolvedElementText(it)
    }
  }

  fun `test gtd from yaml to nested if-then-else branch`() {
    doTestGtdWithSingleTarget(
      loadSchemaTextFromResources("IfElseSchemaWithDepth2.json"),

      """
        buz:
          a: 123
          fo<caret>o: 123
      """.trimIndent()
        to
        """
        "foo": {
                  "type": "integer"
                }
      """.trimIndent(),

      """
        buz:
          b: 123
          fo<caret>o: 123
      """.trimIndent()
        to
        """
        "foo": {
                  "type": "boolean"
                }
      """.trimIndent(),

      """
        buz:
          a: 123
          ba<caret>r: 123
      """.trimIndent()
        to
        """
        "bar": {
                  "type": "string"
                }
      """.trimIndent(),

      """
        buz:
          b: 123
          ba<caret>r: 123
      """.trimIndent()
        to
        """
        "bar": {
                  "type": "array"
                }
      """.trimIndent()) {
      getResolvedElementText(it)
    }
  }

  fun `test validate yaml against schema with nested if-then-else expressions`() {
    doTestSchemaValidation(
      loadSchemaTextFromResources("IfElseSchemaWithDepth2.json"),
      """
        test: "1) Empty buz validates to false in both branches -> expected one is bar_b"
        buz:
          <warning descr="Schema validation: Missing required properties 'b', 'bar'">test: true</warning>
      """.trimIndent(),
      """
        test: "2) a property present -> top level if is false, nested if is true -> expected branch is bar_a, where a is string"
        buz:
          a: <warning descr="Schema validation: Incompatible types.
         Required: string. Actual: integer.">123</warning>
      """.trimIndent(),
      """
        test: "3) a property is present and valid -> top level if is false, nested if is true -> expected branch is bar_a, where bar is missing"
        buz:
          <warning descr="Schema validation: Missing required property 'bar'">a: "str"</warning>
      """.trimIndent(),
      """
        test: "4) only foo is present -> top level if is true, nested is false -> expected branch is foo_b, where foo is boolean"
        buz:
          foo: <warning descr="Schema validation: Incompatible types.
         Required: boolean. Actual: integer.">123</warning>
      """.trimIndent(),
      """
        test: "5) only b is present -> top level if is false, nested is false -> expected branch is bar_b, where b is array"
        buz:
          b: <warning descr="Schema validation: Incompatible types.
         Required: array. Actual: integer.">123</warning>
      """.trimIndent(),
      """
        test: "6) only bar is present -> top level if is false, nested is false -> expected branch is bar_b, where bar is array"
        buz:
          bar: <warning descr="Schema validation: Incompatible types.
         Required: array. Actual: integer.">123</warning>
      """.trimIndent(),
      """
        test: "7) foo and a are present -> expected branch is foo_a -> object is valid against it"
        buz:
          a: 123
          foo: 123
      """.trimIndent(),
      """
        test: "8) bar and b are present -> expected branch is bar_b -> all properties are arrays there"
        buz:
          b: <warning descr="Schema validation: Incompatible types.
         Required: array. Actual: integer.">123</warning>
          bar: <warning descr="Schema validation: Incompatible types.
         Required: array. Actual: integer.">123</warning>
      """.trimIndent()
    )
  }
}