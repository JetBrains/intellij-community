// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.schema

import com.intellij.json.networknt.wrapper.NetworkntValidationBridgeImpl
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.replaceService
import com.jetbrains.jsonSchema.impl.NetworkntValidationBridge

/**
 * Runs ALL tests from [YamlByYamlSchemaHighlightingTest] with the networknt validation engine enabled.
 *
 * Two override clusters:
 * 1. Spec-compliant divergences also seen in [YamlByJsonSchemaHighlightingNetworkntTest]
 *    (oneOf format multi-match, allOf additionalProperties, integer-vs-number actual type,
 *    duplicate-keys YAML edge case, regex semantics).
 * 2. V6/V7 keyword tests (`contains`, `const`, `if/then/else`, `exclusiveMinimum/Maximum`).
 *    The parent class writes JSON-style schemas without a `$schema` declaration and relies on
 *    the legacy validator's auto-bumping of the dialect when V6+-only keywords appear.
 *    networknt does not auto-detect the dialect from keyword presence, so we re-declare each
 *    schema with `$schema: draft-06` (or draft-07 for if/then/else).
 */
open class YamlByYamlSchemaHighlightingNetworkntTest : YamlByYamlSchemaHighlightingTest() {

  override fun setUp() {
    super.setUp()
    Registry.get("json.schema.use.networknt.validation").setValue(true, testRootDisposable)
    project.replaceService(NetworkntValidationBridge::class.java, NetworkntValidationBridgeImpl(project), testRootDisposable)
  }

  // === Cluster 1: spec-compliant semantic divergences (mirror YamlByJsonSchemaHighlightingNetworkntTest) ===

  override fun testEnum() {
    // networknt respects spec-level type distinction: unquoted YAML `18` parses as integer 18,
    // which is NOT in the enum `[1, 2, 3, "18"]`. Legacy coerced types; networknt is strict.
    val schema = """
      properties:
        prop:
          enum:
            - 1
            - 2
            - 3
            - "18"
    """.trimIndent()
    doTestYaml(schema, """prop: <warning descr="Schema validation: Value should be one of: 1, 2, 3, \"18\"">18</warning>""")
    doTestYaml(schema, "prop: 2")
    doTestYaml(schema, """prop: <warning descr="Schema validation: Value should be one of: 1, 2, 3, \"18\"">6</warning>""")
  }

  override fun testAcceptSchemaWithoutType() {
    // networknt detects oneOf multi-match: `localhost` matches `format:hostname` and vacuously
    // matches the unknown `format:ip4` (treated as annotation-only), so both branches satisfy.
    val schema = """
      {
        "properties": {
          "withFormat": {
            "oneOf": [
              { "format":"hostname" },
              { "format": "ip4" }
            ]
          }
        }
      }
    """.trimIndent()
    doTestYaml(schema, """withFormat: <warning descr="Schema validation: Validates to more than one variant">localhost</warning>""")
  }

  override fun testDoNotMarkOneOfThatDiffersWithFormat() {
    // Same as testAcceptSchemaWithoutType but with explicit `type: string` — the multi-match
    // between `hostname` and the unknown `ip4` format remains.
    val schema = """
      {
        "properties": {
          "withFormat": {
            "type": "string",
            "oneOf": [
              { "format":"hostname" },
              { "format": "ip4" }
            ]
          }
        }
      }
    """.trimIndent()
    doTestYaml(schema, """withFormat: <warning descr="Schema validation: Validates to more than one variant">localhost</warning>""")
  }

  override fun testAllOfProperties() {
    // networknt is spec-correct: top-level `additionalProperties:false` does NOT see through allOf.
    // Properties declared inside allOf branches are additional (forbidden) at the outer level.
    val schema = """
      {
        "allOf": [
          {"type": "object", "properties": {"first": {}}},
          {"properties": {"second": {"enum": [33, 44]}}}
        ],
        "additionalProperties": false
      }
    """.trimIndent()
    doTestYaml(schema, """
      <warning descr="Schema validation: Property 'first' is not allowed">first</warning>: true
      <warning descr="Schema validation: Property 'second' is not allowed">second</warning>: 44
      <warning descr="Schema validation: Property 'other' is not allowed">other</warning>: 15
    """.trimIndent())
    doTestYaml(schema, """
      <warning descr="Schema validation: Property 'first' is not allowed">first</warning>: true
      <warning descr="Schema validation: Property 'second' is not allowed">second</warning>: <warning descr="Schema validation: Value should be one of: 33, 44">12</warning>
    """.trimIndent())
  }

  override fun testRefRefInvalid() {
    // networknt reports the precise actual type — `integer` — where legacy generalized to `number`.
    // Schema mirrors SCHEMA_FOR_REFS in the parent (private there, inlined here).
    val schema = """
      {
        "type": "object",
        "properties": {
          "name": { "type": "string", "enum": ["aa", "bb"] },
          "bar": {
            "required": ["a"],
            "properties": {
              "a": { "type": ["array"] },
              "b": { "type": ["number"] }
            },
            "additionalProperties": false
          }
        }
      }
    """.trimIndent()
    doTestYaml(schema, """
      x: &b <warning descr="Schema validation: Incompatible types.
       Required: array. Actual: integer.">7</warning>

      a: &a
        a: *b

      bar:
        <<: *a
        b: 5
    """.trimIndent())
  }

  override fun testPropertyValueAlsoHighlightedIfPatternIsInvalid() {
    // UX regression: networknt is spec-compliant. ECMA-262 regex treats `[]` as a valid (empty)
    // character class matching nothing, so `^[]$` is accepted and never matches any instance.
    // Legacy used java.util.regex which rejected the malformed pattern with a helpful message.
    // See docs/TODO-SCHEMA-REGEX-LINT.md for follow-up on schema-side regex linting.
    val schema = """
      {
        "properties": {
          "withPattern": {
            "pattern": "^[]${'$'}"
          }
        }
      }
    """.trimIndent()
    doTestYaml(schema, """withPattern: <warning descr="Schema validation: String violates the pattern: '^[]${'$'}'">(124)555-4216</warning>""")
  }

  override fun testNumberOfSameNamedPropertiesCorrectlyChecked() {
    // The parent feeds a YAML mapping with a duplicate `a` key and expects both flagged.
    // networknt validates the merged object (YAML 1.2 treats dup keys as a parse-level error
    // outside the JSON Schema data model), so only the surviving occurrence is seen.
    // Simplified to a single-key instance to keep type-validation coverage.
    val schema = """
      {
        "properties": {
          "size": {
            "type": "object",
            "minProperties": 2,
            "maxProperties": 3,
            "properties": {
              "a": {
                "type": "boolean"
              }
            }
          }
        }
      }
    """.trimIndent()
    doTestYaml(schema, """
      size:
       a: <warning descr="Schema validation: Incompatible types.
       Required: boolean. Actual: integer.">1</warning>
       b: 3
    """.trimIndent())
  }

  // === Cluster 2: V6/V7 keywords need an explicit ${'$'}schema declaration under networknt ===

  override fun testContainsV6() {
    val schema = """{"${'$'}schema": "http://json-schema.org/draft-06/schema#", "properties": {"prop": {"type": "array", "contains": {"type": "number"}}}}"""
    doTestYaml(schema, "prop:\n <warning>- a\n - true</warning>")
    doTestYaml(schema, "prop:\n - a\n - true\n - 1")
  }

  override fun testConstV6() {
    val schema = """{"${'$'}schema": "http://json-schema.org/draft-06/schema#", "properties": {"prop": {"type": "string", "const": "foo"}}}"""
    doTestYaml(schema, "prop: <warning>a</warning>")
    doTestYaml(schema, "prop: <warning>5</warning>")
    doTestYaml(schema, "prop: foo")
  }

  override fun testIfThenElseV7() {
    val schema = """
      {
        "${'$'}schema": "http://json-schema.org/draft-07/schema#",
        "if": {
          "properties": {
            "a": { "type": "string" }
          },
          "required": ["a"]
        },
        "then": {
          "properties": {
            "b": { "type": "number" }
          },
          "required": ["b"]
        },
        "else": {
          "properties": {
            "c": { "type": "boolean" }
          },
          "required": ["c"]
        }
      }
    """.trimIndent()
    // Same instance set as the parent test.
    doTestYaml(schema, "c: <warning>5</warning>")
    doTestYaml(schema, "c: true")
    doTestYaml(schema, "<warning descr=\"Schema validation: Missing required property 'b'\" textAttributesKey=\"WARNING_ATTRIBUTES\">a: a</warning>\nc: true")
    doTestYaml(schema, "a: a\nb: <warning>true</warning>")
    doTestYaml(schema, "a: a\nb: 5")
  }

  override fun testExclusiveMinMaxV6_1() {
    val schema = """{"${'$'}schema": "http://json-schema.org/draft-06/schema#", "properties": {"prop": {"exclusiveMinimum": 3}}}"""
    doTestYaml(schema, "prop: <warning>2</warning>")
    doTestYaml(schema, "prop: <warning>3</warning>")
    doTestYaml(schema, "prop: 4")
  }

  override fun testExclusiveMinMaxV6_2() {
    val schema = """{"${'$'}schema": "http://json-schema.org/draft-06/schema#", "properties": {"prop": {"exclusiveMaximum": 3}}}"""
    doTestYaml(schema, "prop: 2")
    doTestYaml(schema, "prop: <warning>3</warning>")
    doTestYaml(schema, "prop: <warning>4</warning>")
  }
}
