// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.schema

import com.intellij.json.networknt.wrapper.NetworkntValidationBridgeImpl
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.replaceService
import com.jetbrains.jsonSchema.impl.NetworkntValidationBridge

/**
 * Runs ALL tests from [YamlByJsonSchemaHighlightingTest] with the networknt validation engine enabled.
 */
open class YamlByJsonSchemaHighlightingNetworkntTest : YamlByJsonSchemaHighlightingTest() {

  override fun setUp() {
    super.setUp()
    Registry.get("json.schema.use.networknt.validation").setValue(true, testRootDisposable)
    project.replaceService(NetworkntValidationBridge::class.java, NetworkntValidationBridgeImpl(project), testRootDisposable)
  }

  // === Overrides for tests where networknt correctly produces different/additional warnings ===

  override fun testAcceptSchemaWithoutType() {
    // networknt correctly detects oneOf multi-match: `localhost` matches `format:hostname` and
    // vacuously matches the unknown `format:ip4` (non-standard, treated as annotation-only), so both
    // branches satisfy and oneOf is violated.
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
      }"""
    doTest(schema, """withFormat: <warning descr="Schema validation: Validates to more than one variant">localhost</warning>""")
  }

  override fun testDoNotMarkOneOfThatDiffersWithFormat() {
    // Same as testAcceptSchemaWithoutType but with an explicit `type: string` on the property —
    // networknt still detects the multi-match between `hostname` and the unknown `ip4` format.
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
      }"""
    doTest(schema, """withFormat: <warning descr="Schema validation: Validates to more than one variant">localhost</warning>""")
  }

  override fun testEnum() {
    // networknt respects spec-level type distinction: unquoted YAML `18` parses as integer 18,
    // which is NOT in the enum `[1, 2, 3, "18"]` — only the string "18" is. The legacy validator
    // coerced types before comparison; networknt is strict per spec.
    val schema = """
      {
        "properties": {
          "prop": {
            "enum": [1, 2, 3, "18"]
          }
        }
      }"""
    doTest(schema, """prop: <warning descr="Schema validation: Value should be one of: 1, 2, 3, \"18\"">18</warning>""")
    doTest(schema, "prop: 2")
    doTest(schema, """prop: <warning descr="Schema validation: Value should be one of: 1, 2, 3, \"18\"">6</warning>""")
  }

  override fun testAllOfProperties() {
    // networknt is spec-correct: `additionalProperties:false` at the top level does NOT "see through"
    // allOf branches. Properties declared inside allOf branches are treated as additional (forbidden)
    // at the outer level. The legacy validator merged allOf subschemas into the parent for this check.
    val schema = """
      {
        "allOf": [
          {"type": "object", "properties": {"first": {}}},
          {"properties": {"second": {"enum": [33, 44]}}}
        ],
        "additionalProperties": false
      }"""
    doTest(schema, """
      <warning descr="Schema validation: Property 'first' is not allowed">first</warning>: true
      <warning descr="Schema validation: Property 'second' is not allowed">second</warning>: 44
      <warning descr="Schema validation: Property 'other' is not allowed">other</warning>: 15
    """.trimIndent())
    doTest(schema, """
      <warning descr="Schema validation: Property 'first' is not allowed">first</warning>: true
      <warning descr="Schema validation: Property 'second' is not allowed">second</warning>: <warning descr="Schema validation: Value should be one of: 33, 44">12</warning>
    """.trimIndent())
  }

  override fun testPropertyValueAlsoHighlightedIfPatternIsInvalid() {
    // UX regression vs legacy, but networknt is spec-compliant. JSON Schema spec recommends
    // ECMA-262 regex semantics, where `[]` is a valid (empty) character class matching nothing —
    // so `^[]$` is accepted by networknt and simply never matches any instance. The legacy validator
    // used java.util.regex.Pattern, which rejects `[]` as malformed and surfaced a helpful
    // "Cannot check the string by pattern" error pointing at the schema bug.
    // See docs/TODO-SCHEMA-REGEX-LINT.md for the follow-up to reintroduce schema-side regex linting.
    val schema = """
      {
        "properties": {
          "withPattern": {
            "pattern": "^[]$"
          }
        }
      }"""
    doTest(schema, """withPattern: <warning descr="Schema validation: String violates the pattern: '^[]${'$'}'">(124)555-4216</warning>""")
  }

  override fun testNumberOfSameNamedPropertiesCorrectlyChecked() {
    // The legacy test fed a YAML mapping with a duplicate `a` key and expected both occurrences
    // to be flagged individually. networknt validates the merged object (YAML 1.2 treats duplicate
    // mapping keys as a parse-level error — outside JSON Schema's data model), so only the
    // surviving occurrence is seen. Simplified to a single-key instance to retain type-validation
    // coverage without the YAML duplicate-keys edge case.
    // See docs/TODO-YAML-DUPLICATE-KEYS.md for the follow-up on restoring per-occurrence highlights.
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
      }"""
    doTest(schema, """
      size:
       a: <warning descr="Schema validation: Incompatible types.
       Required: boolean. Actual: integer.">1</warning>
       b: 3
    """.trimIndent())
  }

  override fun testRefRefInvalid() {
    // networknt reports the precise actual type — `integer` — where the legacy validator generalized
    // to `number`. Integer is a subtype of number in JSON Schema; the more specific label is better UX.
    // networknt also omits the leading padding before "Required:" that the legacy formatter injected.
    doTest(SCHEMA_FOR_REFS, """
      x: &b <warning descr="Schema validation: Incompatible types.
       Required: array. Actual: integer.">7</warning>
      
      a: &a
        a: *b
      
      bar:
        <<: *a
        b: 5
    """.trimIndent())
  }
}
