// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.schema;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.jetbrains.jsonSchema.JsonSchemaHighlightingTestFixture;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.YAMLLanguage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static com.jetbrains.jsonSchema.JsonSchemaHighlightingTestFixtureKt.jsonSchemaHighlightingFixture;

@TestApplication
public class YamlByJsonSchemaHighlightingTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);
  private final TestFixture<JsonSchemaHighlightingTestFixture> schemaFixture =
    jsonSchemaHighlightingFixture(codeInsightFixture, "config.yml", () -> new YamlJsonSchemaHighlightingInspection(), YAMLLanguage.INSTANCE);

  protected CodeInsightTestFixture myFixture;
  protected JsonSchemaHighlightingTestFixture mySchemaFixture;

  @BeforeEach
  void setUp() {
    myFixture = codeInsightFixture.get();
    mySchemaFixture = schemaFixture.get();
    Registry.get("json.schema.use.networknt.validation").setValue(false, myFixture.getTestRootDisposable());
  }

  @NotNull
  public String getTestDataPath() {
    return PathManagerEx.getCommunityHomePath() + "/plugins/yaml/backend/testData/org/jetbrains/yaml/schema/data/highlighting";
  }

  @Test
  public void testEnum1() {
    @Language("JSON") final String schema =
      """
        {
          "properties": {
            "prop": {
              "enum": [1, 2, 3, "18"]
            }
          }
        }""";
    mySchemaFixture.doTest(schema, "prop: 1");
    mySchemaFixture.doTest(schema, "prop: <warning>foo</warning>");
  }

  @Test
  public void testMissingProp() {
    @Language("JSON") final String schema =
      """
        {
          "properties": {
            "prop": {},
            "flop": {}
          },
          "required": ["flop"]
        }""";

    mySchemaFixture.doTest(schema, "<warning>prop: 2</warning>");
    mySchemaFixture.doTest(schema, """
      prop: 2
      flop: a
      """);
    mySchemaFixture.doTest(schema, "flop: a");
  }

  @Test
  public void testNumberMultipleWrong() {
    mySchemaFixture.doTest(
      """
        {
          "properties": {
            "prop": {
              "type": "number",
              "multipleOf": 2
            }
          }
        }""",
      "prop: <warning descr=\"Schema validation: Is not multiple of 2\">3</warning>");
  }

  @Test
  public void testNumberMultipleCorrect() {
    mySchemaFixture.doTest(
      """
        {
          "properties": {
            "prop": {
              "type": "number",
              "multipleOf": 2
            }
          }
        }""",
      "prop: 4");
  }

  @Test
  public void testNumberMinMax() {
    mySchemaFixture.doTest(
      """
        {
          "properties": {
            "prop": {
              "type": "number",
              "minimum": 0,
              "maximum": 100,
              "exclusiveMaximum": true
            }
          }
        }""",
      "prop: 14");
  }

  @Test
  public void testEnum() {
    @Language("JSON") final String schema =
      """
        {
          "properties": {
            "prop": {
              "enum": [1, 2, 3, "18"]
            }
          }
        }""";
    mySchemaFixture.doTest(schema, "prop: 18");
    mySchemaFixture.doTest(schema, "prop: 2");
    mySchemaFixture.doTest(schema, "prop: <warning descr=\"Schema validation: Value should be one of: 1, 2, 3, \\\"18\\\"\">6</warning>");
  }

  @Test
  public void testSimpleString() {
    @Language("JSON") final String schema =
      """
        {
          "properties": {
            "prop": {
              "type": "string",
              "minLength": 2,
              "maxLength": 3
            }
          }
        }""";
    mySchemaFixture.doTest(schema, "prop: <warning descr=\"Schema validation: String is shorter than 2\">s</warning>");
    mySchemaFixture.doTest(schema, "prop: sh");
    mySchemaFixture.doTest(schema, "prop: sho");
    mySchemaFixture.doTest(schema, "prop: <warning descr=\"Schema validation: String is longer than 3\">shor</warning>");
  }

  @Test
  public void testArray() {
    @Language("JSON") final String schema = schema(
      """
        {
          "type": "array",
          "items": {
            "type": "number", "minimum": 18  }
        }
        """);
    mySchemaFixture.doTest(schema, """
      prop:
       - 101
       - 102
      """);
    mySchemaFixture.doTest(schema, """
      prop:
       - <warning descr="Schema validation: Less than the minimum 18">16</warning>\
      """);
    mySchemaFixture.doTest(schema, """
      prop:
       - <warning descr="Schema validation: Incompatible types.
       Required: number. Actual: string.">test</warning>\
      """);
  }

  @Test
  public void testTopLevelArray() {
    @Language("JSON") final String schema =
      """
        {
          "type": "array",
          "items": {
            "type": "number", "minimum": 18  }
        }""";
    mySchemaFixture.doTest(schema, """
      - 101
      - 102
      """);
  }

  @Test
  public void testTopLevelObjectArray() {
    @Language("JSON") final String schema =
      """
        {
          "type": "array",
          "items": {
            "type": "object", "properties": {"a": {"type": "number"}}  }
        }""";
    mySchemaFixture.doTest(schema, """
      - a: <warning descr="Schema validation: Incompatible types.
       Required: number. Actual: boolean.">true</warning>\
      """);
    mySchemaFixture.doTest(schema, "- a: 18");
  }

  @Test
  public void testArrayTuples1() {
    @Language("JSON") final String schema = schema(
      """
        {
          "type": "array",
          "items": [{
            "type": "number", "minimum": 18  }, {"type" : "string"}]
        }""");
    mySchemaFixture.doTest(schema, """
      prop:
       - 101
       - <warning descr="Schema validation: Incompatible types.
       Required: string. Actual: integer.">102</warning>\
      """);
  }

  @Test
  public void testArrayTuples2() {
    @Language("JSON") final String schema2 = schema(
      """
        {
          "type": "array",
          "items": [{
            "type": "number", "minimum": 18  }, {"type" : "string"}],
        "additionalItems": false}
        """);
    mySchemaFixture.doTest(schema2, """
      prop:
       - 101
       - <warning descr="Schema validation: Incompatible types.
       Required: string. Actual: integer.">102</warning>
       - <warning descr="Schema validation: Additional items are not allowed">additional</warning>\
      """);
  }

  @Test
  public void testArrayLength() {
    @Language("JSON") final String schema = schema(
      """
        {
          "type": "array",
          "minItems": 2,
          "maxItems": 3
        }""");
    mySchemaFixture.doTest(schema, """
      prop:
       <warning descr="Schema validation: Array is shorter than 2">- 1</warning>\
      """);
    mySchemaFixture.doTest(schema, """
      prop:
       - 1
       - 2\
      """);
    mySchemaFixture.doTest(schema, """
      prop:
       <warning descr="Schema validation: Array is longer than 3">- 1
       - 2
       - 3
       - 4</warning>\
      """);
  }

  @Test
  public void testArrayUnique() {
    @Language("JSON") final String schema = schema(
      """
        {
          "type": "array",
          "uniqueItems": true
        }""");
    mySchemaFixture.doTest(schema, """
      prop:
       - 1
       - 2\
      """);
    mySchemaFixture.doTest(schema, """
      prop:
       - <warning descr="Schema validation: Item is not unique">1</warning>
       - 2
       - test
       - <warning descr="Schema validation: Item is not unique">1</warning>\
      """);
  }

  @Test
  public void testMetadataIsOk() {
    @Language("JSON") final String schema =
      """
        {
          "title" : "Match anything",
          "description" : "This is a schema that matches anything.",
          "default" : "Default value"
        }""";
    mySchemaFixture.doTest(schema, "anything: 1");
  }

  @Test
  public void testRequiredField() {
    @Language("JSON") final String schema =
      """
        {
          "type": "object",
          "properties": {
            "a": {},
            "b": {}
          },
          "required": ["a"]
        }""";
    mySchemaFixture.doTest(schema, "a: 11");
    mySchemaFixture.doTest(schema, """
      a: 1
      b: true\
      """);
    mySchemaFixture.doTest(schema, "<warning descr=\"Schema validation: Missing required property 'a'\">b: alarm</warning>");
  }

  @Test
  public void testInnerRequired() {
    @Language("JSON") final String schema = schema(
      """
        {
          "type": "object",
          "properties": {
            "a": {},
            "b": {}
          },
          "required": ["a"]
        }""");
    mySchemaFixture.doTest(schema, """
      prop:
       a: 11\
      """);
    mySchemaFixture.doTest(schema, """
      prop:
       a: 1
       b: true\
      """);
    mySchemaFixture.doTest(schema, """
      prop:
       <warning descr="Schema validation: Missing required property 'a'">b: alarm</warning>\
      """);
  }

  @Test
  public void testAdditionalPropertiesAllowed() {
    @Language("JSON") final String schema = schema(
      """
        {}""");
    mySchemaFixture.doTest(schema, """
      prop:
       q: true
       someStuff: 20\
      """);
  }

  @Test
  public void testAdditionalPropertiesDisabled() {
    @Language("JSON") final String schema =
      """
        {
          "type": "object",
          "properties": {
            "prop": {}
          },
          "additionalProperties": false
        }""";
    // not sure abt inner object
    mySchemaFixture.doTest(schema, """
      prop:
       q: true
      <warning descr="Schema validation: Property 'someStuff' is not allowed">someStuff</warning>: 20\
      """);
  }

  @Test
  public void testAdditionalPropertiesSchema() {
    @Language("JSON") final String schema =
      """
        {
          "type": "object",
          "properties": {
            "a": {}
          },
          "additionalProperties": {
            "type": "number"
          }
        }""";
    mySchemaFixture.doTest(schema, """
      a: moo
      b: 5
      c: <warning descr="Schema validation: Incompatible types.
       Required: number. Actual: string.">foo</warning>\
      """);
  }

  @Test
  public void testMinMaxProperties() {
    @Language("JSON") final String schema =
      """
        {
          "type": "object",
          "minProperties": 2,
          "maxProperties": 3
        }""";
    mySchemaFixture.doTest(schema, "<warning descr=\"Schema validation: Number of properties is less than 2\">a: 3</warning>");
    mySchemaFixture.doTest(schema, """
      a: 1
      b: 5\
      """);
    mySchemaFixture.doTest(schema, """
      <warning descr="Schema validation: Number of properties is greater than 3" textAttributesKey="WARNING_ATTRIBUTES">a: 1</warning>
      b: 22
      c: 333
      d: 4444""");
  }

  @Test
  public void testOneOf() {
    final List<String> subSchemas = new ArrayList<>();
    subSchemas.add("""
                     {
                       "type": "number"
                     }""");
    subSchemas.add("""
                     {
                       "type": "boolean"
                     }""");
    @Language("JSON") final String schema = schema(
      """
        {
          "oneOf": [%s]
        }""".formatted(StringUtil.join(subSchemas, ", ")));
    mySchemaFixture.doTest(schema, "prop: 5");
    mySchemaFixture.doTest(schema, "prop: true");
    mySchemaFixture.doTest(schema, """
      prop: <warning descr="Schema validation: Incompatible types.
       Required one of: boolean, number. Actual: string.">aaa</warning>\
      """);
  }

  @Test
  public void testOneOfForTwoMatches() {
    final List<String> subSchemas = new ArrayList<>();
    subSchemas.add("""
                     {
                       "type": "string",
                       "enum": ["a", "b"]
                     }""");
    subSchemas.add("""
                     {
                       "type": "string",
                       "enum": ["a", "c"]
                     }""");
    @Language("JSON") final String schema = schema(
      """
        {
          "oneOf": [%s]
        }""".formatted(StringUtil.join(subSchemas, ", ")));
    mySchemaFixture.doTest(schema, "prop: b");
    mySchemaFixture.doTest(schema, "prop: c");
    mySchemaFixture.doTest(schema, "prop: <warning descr=\"Schema validation: Validates to more than one variant\">a</warning>");
  }

  @Test
  public void testOneOfSelectError() {
    final List<String> subSchemas = new ArrayList<>();
    subSchemas.add("""
                     {"type": "string",
                               "enum": [
                                 "off", "warn", "error"
                               ]}""");
    subSchemas.add("""
                     {
                       "type": "integer"
                     }""");
    @Language("JSON") final String schema = schema(
      """
        {
          "oneOf": [%s]
        }""".formatted(StringUtil.join(subSchemas, ", ")));
    mySchemaFixture.doTest(schema, "prop: off");
    mySchemaFixture.doTest(schema, "prop: 12");
    mySchemaFixture.doTest(schema,
                           "prop: <warning descr=\"Schema validation: Value should be one of: \\\"off\\\", \\\"warn\\\", \\\"error\\\"\">wrong</warning>");
  }

  @Test
  public void testAnyOf() {
    final List<String> subSchemas = new ArrayList<>();
    subSchemas.add("""
                     {
                       "type": "string",
                       "enum": ["a", "b"]
                     }""");
    subSchemas.add("""
                     {
                       "type": "string",
                       "enum": ["a", "c"]
                     }""");
    @Language("JSON") final String schema = schema(
      """
        {
          "anyOf": [%s]
        }""".formatted(StringUtil.join(subSchemas, ", ")));
    mySchemaFixture.doTest(schema, "prop: b");
    mySchemaFixture.doTest(schema, "prop: c");
    mySchemaFixture.doTest(schema, "prop: a");
    mySchemaFixture.doTest(schema, "prop: <warning descr=\"Schema validation: Value should be one of: \\\"a\\\", \\\"b\\\", \\\"c\\\"\">d</warning>");
  }

  @Test
  public void testAllOf() {
    final List<String> subSchemas = new ArrayList<>();
    subSchemas.add("""
                     {
                       "type": "integer",
                       "multipleOf": 2
                     }""");
    subSchemas.add("""
                     {
                       "enum": [1, 2, 3]
                     }""");
    @Language("JSON") final String schema = schema(
      """
        {
          "allOf": [%s]
        }""".formatted(StringUtil.join(subSchemas, ", ")));
    mySchemaFixture.doTest(schema, "prop: <warning descr=\"Schema validation: Is not multiple of 2\">1</warning>");
    mySchemaFixture.doTest(schema, "prop: <warning descr=\"Schema validation: Value should be one of: 1, 2, 3\">4</warning>");
    mySchemaFixture.doTest(schema, "prop: 2");
  }

  // ----

  @Test
  public void testObjectInArray() {
    @Language("JSON") final String schema = schema(
      """
        {
          "type": "array",
          "items": {
            "type": "object",
            "properties": {
              "innerType": {},
              "innerValue": {}
            },
            "additionalProperties": false
          }
        }""");
    mySchemaFixture.doTest(schema, """
      prop:
      - innerType: aaa
        <warning descr="Schema validation: Property 'alien' is not allowed">alien</warning>: bee\
      """);
  }

  @Test
  public void testObjectDeeperInArray() {
    final String innerTypeSchema = """
      {
        "properties": {
          "only": {}
        },
        "additionalProperties": false
      }""";
    @Language("JSON") final String schema = schema(
      """
        {
          "type": "array",
          "items": {
            "type": "object",
            "properties": {
              "innerType": %s
            },
            "additionalProperties": false
          }
        }""".formatted(innerTypeSchema));
    mySchemaFixture.doTest(schema,
                           """
                             prop:
                             - innerType:
                                only: true
                                <warning descr="Schema validation: Property 'hidden' is not allowed">hidden</warning>: false\
                             """);
  }

  @Test
  public void testInnerObjectPropValueInArray() {
    @Language("JSON") final String schema =
      """
        {
          "properties": {
            "prop": {
              "type": "array",
              "items": {
                "enum": [1, 2, 3]
              }
            }
          }
        }""";
    mySchemaFixture.doTest(schema, """
      prop:
       - 1
       - 3\
      """);
    mySchemaFixture.doTest(schema, """
      prop:
       - <warning descr="Schema validation: Value should be one of: 1, 2, 3">out</warning>\
      """);
  }

  @Test
  public void testAllOfProperties() {
    @Language("JSON") final String schema =
      """
        {
          "allOf": [
            {
              "type": "object",
              "properties": {
                "first": {}
              }
            },
            {
              "properties": {
                "second": {
                  "enum": [33, 44]
                }
              }
            }
          ],
          "additionalProperties": false
        }""";
    //    doTest(schema, """
    //      first: true
    //      second: <warning descr="Schema validation: Value should be one of: [33, 44]">null</warning>\
    //      """);
    mySchemaFixture.doTest(schema, """
      first: true
      second: 44
      <warning descr="Schema validation: Property 'other' is not allowed">other</warning>: 15\
      """);
    mySchemaFixture.doTest(schema, """
      first: true
      second: <warning descr="Schema validation: Value should be one of: 33, 44">12</warning>\
      """);
  }

  @Test
  public void testWithWaySelection() {
    final String subSchema1 = """
      {
        "enum": [1, 2, 3, 4, 5]
      }""";
    final String subSchema2 = """
      {
        "type": "array",
        "items": {
          "properties": {
            "kilo": {}
          },
          "additionalProperties": false
        }
      }""";
    @Language("JSON") final String schema =
      """
        {
          "properties": {
            "prop": {
              "oneOf": [%s, %s]
            }
          }
        }""".formatted(subSchema1, subSchema2);
    mySchemaFixture.doTest(schema, """
      prop:
       - <warning descr="Schema validation: Property 'foxtrot' is not allowed">foxtrot</warning>: 15
         kilo: 20\
      """);
  }

  @Test
  public void testPatternPropertiesHighlighting() {
    @Language("JSON") final String schema = """
      {
        "patternProperties": {
          "^A" : {
            "type": "number"
          },
          "B": {
            "type": "boolean"
          },
          "C": {
            "enum": ["test", "em"]
          }
        }
      }""";
    mySchemaFixture.doTest(schema, """
      Abezjana: 2
      Auto: <warning descr="Schema validation: Incompatible types.
       Required: number. Actual: string.">no</warning>
      BAe: <warning descr="Schema validation: Incompatible types.
       Required: boolean. Actual: integer.">22</warning>
      Boloto: <warning descr="Schema validation: Incompatible types.
       Required: boolean. Actual: integer.">2</warning>
      Cyan: <warning descr="Schema validation: Value should be one of: \\"test\\", \\"em\\"">me</warning>
      """);
  }

  @Test
  public void testPatternPropertiesFromIssue() {
    @Language("JSON") final String schema = """
      {
        "type": "object",
        "additionalProperties": false,
        "patternProperties": {
          "p[0-9]": {
            "type": "string"
          },
          "a[0-9]": {
            "enum": ["auto!"]
          }
        }
      }""";
    mySchemaFixture.doTest(schema,
                           """
                             p1: <warning descr="Schema validation: Incompatible types.
                              Required: string. Actual: integer.">1</warning>
                             p2: <warning descr="Schema validation: Incompatible types.
                              Required: string. Actual: integer.">3</warning>
                             a2: auto!
                             a1: <warning descr="Schema validation: Value should be one of: \\"auto!\\"">moto!</warning>
                             """
                    );
  }

  @Test
  public void testPatternForPropertyValue() {
    @Language("JSON") final String schema = """
      {
        "properties": {
          "withPattern": {
            "pattern": "p[0-9]"
          }
        }
      }""";
    final String correctText = "withPattern: p1";
    final String wrongText = """
      withPattern: <warning descr="Schema validation: String violates the pattern: 'p[0-9]'">wrong</warning>\
      """;
    mySchemaFixture.doTest(schema, correctText);
    mySchemaFixture.doTest(schema, wrongText);
  }

  @Test
  public void testPatternWithSpecialEscapedSymbols() {
    @Language("JSON") final String schema = """
      {
        "properties": {
          "withPattern": {
            "pattern": "^\\\\d{4}\\\\-(0?[1-9]|1[012])\\\\-(0?[1-9]|[12][0-9]|3[01])$"
          }
        }
      }""";
    @Language("yaml") final String correctText = "withPattern: 1234-11-11";
    final String wrongText = """
      withPattern: <warning descr="Schema validation: String violates the pattern: '^\\d{4}\\-(0?[1-9]|1[012])\\-(0?[1-9]|[12][0-9]|3[01])$'">wrong</warning>
      """;
    mySchemaFixture.doTest(schema, correctText);
    mySchemaFixture.doTest(schema, wrongText);
  }

  // ---


  @Test
  public void testRootObjectRedefinedAdditionalPropertiesForbidden() {
    mySchemaFixture.doTest(rootObjectRedefinedSchema(), """
      <warning descr="Schema validation: Property 'a' is not allowed">a</warning>: true
      r1: allowed!\
      """);
  }

  @Test
  public void testNumberOfSameNamedPropertiesCorrectlyChecked() {
    @Language("JSON") final String schema = """
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
      }""";
    mySchemaFixture.doTest(schema,
                           """
                             size:\s
                              a: <warning descr="Schema validation: Incompatible types.
                              Required: boolean. Actual: integer.">1</warning>
                              b: 3
                              c: 4
                              a: <warning descr="Schema validation: Incompatible types.
                              Required: boolean. Actual: integer.">5</warning>
                             """);
  }

  @Test
  public void testManyDuplicatesInArray() {
    @Language("JSON") final String schema = """
      {
        "properties": {
          "array":{
            "type": "array",
            "uniqueItems": true
          }
        }
      }""";
    mySchemaFixture.doTest(schema, """
      array:\s
       - <warning descr="Schema validation: Item is not unique">1</warning>
       - <warning descr="Schema validation: Item is not unique">1</warning>
       - <warning descr="Schema validation: Item is not unique">1</warning>
       - <warning descr="Schema validation: Item is not unique">2</warning>
       - <warning descr="Schema validation: Item is not unique">2</warning>
       - <warning descr="Schema validation: Item is not unique">2</warning>
       - 5
       - <warning descr="Schema validation: Item is not unique">3</warning>
       - <warning descr="Schema validation: Item is not unique">3</warning>
      """);
  }

  // ----

  @Test
  public void testPropertyValueAlsoHighlightedIfPatternIsInvalid() {
    @Language("JSON") final String schema = """
      {
        "properties": {
          "withPattern": {
            "pattern": "^[]$"
          }
        }
      }""";
    final String text = """
      withPattern: <warning descr="Schema validation: Cannot check the string by pattern because of an error: Unclosed character class near index 3
      ^[]$
         ^">(124)555-4216</warning>""";
    mySchemaFixture.doTest(schema, text);
  }

  @Test
  public void testNotSchema() {
    @Language("JSON") final String schema = """
      {"properties": {
          "not_type": { "not": { "type": "string" } }
        }}""";
    mySchemaFixture.doTest(schema, "not_type: <warning descr=\"Schema validation: Validates against 'not' schema\">wrong</warning>");
  }

  @Test
  public void testNotSchemaCombinedWithNormal() {
    @Language("JSON") final String schema = """
      {"properties": {
          "not_type": {
            "pattern": "^[a-z]*[0-5]*$",
            "not": { "pattern": "^[a-z]{1}[0-5]$" }
          }
        }}""";
    mySchemaFixture.doTest(schema, "not_type: va4");
    mySchemaFixture.doTest(schema, "not_type: <warning descr=\"Schema validation: Validates against 'not' schema\">a4</warning>");
    mySchemaFixture.doTest(schema, "not_type: <warning descr=\"Schema validation: String violates the pattern: '^[a-z]*[0-5]*$'\">4a4</warning>");
  }

  @Test
  public void testDoNotMarkOneOfThatDiffersWithFormat() {
    @Language("JSON") final String schema = """
      {
      
        "properties": {
          "withFormat": {
            "type": "string",      "oneOf": [
              {
                "format":"hostname"
              },
              {
                "format": "ip4"
              }
            ]
          }
        }
      }""";
    mySchemaFixture.doTest(schema, "withFormat: localhost");
  }

  @Test
  public void testAcceptSchemaWithoutType() {
    @Language("JSON") final String schema = """
      {
      
        "properties": {
          "withFormat": {
            "oneOf": [
              {
                "format":"hostname"
              },
              {
                "format": "ip4"
              }
            ]
          }
        }
      }""";
    mySchemaFixture.doTest(schema, "withFormat: localhost");
  }

  @Test
  public void testArrayItemReference() {
    @Language("JSON") final String schema = """
      {
        "items": [
          {
            "type": "integer"
          },
          {
            "$ref": "#/items/0"
          }
        ]
      }""";
    mySchemaFixture.doTest(schema, """
      - 1
      - 2\
      """);
    mySchemaFixture.doTest(schema, """
      - 1
      - <warning>foo</warning>\
      """);
  }

  @Test
  public void testValidateAdditionalItems() {
    @Language("JSON") final String schema = """
      {
        "definitions": {
          "options": {
            "type": "array",
            "items": {
              "type": "number"
            }
          }
        },
        "items": [
          {
            "type": "boolean"
          },
          {
            "type": "boolean"
          }
        ],
        "additionalItems": {
          "$ref": "#/definitions/options/items"
        }
      }""";
    mySchemaFixture.doTest(schema, """
      - true
      - true\
      """);
    mySchemaFixture.doTest(schema, """
      - true
      - true
      - 1
      - 2
      - 3\
      """);
    mySchemaFixture.doTest(schema, """
      - true
      - true
      - 1
      - <warning>qq</warning>\
      """);
  }

  @Test
  public void testExclusiveMinMaxV6_1() {
    @Language("JSON") String exclusiveMinSchema =
      """
        {
          "$schema": "http://json-schema.org/draft-06/schema#",
          "properties": {
            "prop": {
              "exclusiveMinimum": 3
            }
          }
        }""";
    mySchemaFixture.doTest(exclusiveMinSchema, "prop: <warning>2</warning>");
    mySchemaFixture.doTest(exclusiveMinSchema, "prop: <warning>3</warning>");
    mySchemaFixture.doTest(exclusiveMinSchema, "prop: 4");
  }


  @Test
  public void testExclusiveMinMaxV6_2() {
    @Language("JSON") String exclusiveMaxSchema =
      """
        {
          "$schema": "http://json-schema.org/draft-06/schema#",
          "properties": {
            "prop": {
              "exclusiveMaximum": 3
            }
          }
        }""";
    mySchemaFixture.doTest(exclusiveMaxSchema, "prop: 2");
    mySchemaFixture.doTest(exclusiveMaxSchema, "prop: <warning>3</warning>");
    mySchemaFixture.doTest(exclusiveMaxSchema, "prop: <warning>4</warning>");
  }

  /*todo later
  public void testPropertyNamesV6() {
    mySchemaFixture.doTest("{\"propertyNames\": {\"minLength\": 7}}", "{<warning>\"prop\"</warning>: 2}");
    mySchemaFixture.doTest("{\"properties\": {\"prop\": {\"propertyNames\": {\"minLength\": 7}}}}", "{\"prop\": {<warning>\"qq\"</warning>: 7}}");
  }*/

  @Test
  public void testContainsV6() {
    @Language("JSON") String schema =
      """
        {
          "$schema": "http://json-schema.org/draft-06/schema#",
          "properties": {
            "prop": {
              "type": "array",
              "contains": {
                "type": "number"
              }
            }
          }
        }""";
    mySchemaFixture.doTest(schema, """
      prop:
       <warning>- a
       - true</warning>\
      """);
    mySchemaFixture.doTest(schema, """
      prop:
       - a
       - true
       - 1\
      """);
  }

  @Test
  public void testConstV6() {
    @Language("JSON") String schema =
      """
        {
          "$schema": "http://json-schema.org/draft-06/schema#",
          "properties": {
            "prop": {
              "type": "string",
              "const": "foo"
            }
          }
        }""";
    mySchemaFixture.doTest(schema, "prop: <warning>a</warning>");
    mySchemaFixture.doTest(schema, "prop: <warning>5</warning>");
    mySchemaFixture.doTest(schema, "prop: foo");
  }

  @Test
  public void testIfThenElseV7() {
    @Language("JSON") String schema = """
      {
        "$schema": "http://json-schema.org/draft-07/schema#",
        "if": {
          "properties": {
            "a": {
              "type": "string"
            }
          },
          "required": ["a"]
        },
        "then": {
          "properties": {
            "b": {
              "type": "number"
            }
          },
          "required": ["b"]
        },
        "else": {
          "properties": {
            "c": {
              "type": "boolean"
            }
          },
          "required": ["c"]
        }
      }""";
    mySchemaFixture.doTest(schema, "c: <warning>5</warning>");
    mySchemaFixture.doTest(schema, "c: true");
    mySchemaFixture.doTest(schema, """
      <warning descr="Schema validation: Missing required property 'b'" textAttributesKey="WARNING_ATTRIBUTES">a: a</warning>
      c: true\
      """);
    mySchemaFixture.doTest(schema, """
      a: a
      b: <warning>true</warning>\
      """);
    mySchemaFixture.doTest(schema, """
      a: a
      b: 5\
      """);
  }

  @Test
  public void testNestedOneOf() {
    @Language("JSON") String schema = """
      {"type":"object",
        "oneOf": [
          {
            "properties": {
              "type": {
                "type": "string",
                "oneOf": [
                  {
                    "pattern": "(good)"
                  },
                  {
                    "pattern": "(ok)"
                  }
                ]
              }
            }
          },
          {
            "properties": {
              "type": {
                "type": "string",
                "pattern": "^(fine)"
              },
              "extra": {
                "type": "string"
              }
            },
            "required": ["type", "extra"]
          }
        ]}""";

    mySchemaFixture.doTest(schema, "type: good");
    mySchemaFixture.doTest(schema, "type: ok");
    mySchemaFixture.doTest(schema, "type: <warning>doog</warning>");
    mySchemaFixture.doTest(schema, "type: <warning>ko</warning>");
  }

  @Test
  public void testArrayRefs() {
    @Language("JSON") String schema = """
      {
        "myDefs": {
          "myArray": [
            {
              "type": "number"
            },
            {
              "type": "boolean"
            }
          ]
        },
        "type": "array",
        "items": [
          {
            "$ref": "#/myDefs/myArray/0"
          },
          {
            "$ref": "#/myDefs/myArray/1"
          }
        ]
      }""";

    mySchemaFixture.doTest(schema, """
      - 1
      - <warning>2</warning>\
      """);
    mySchemaFixture.doTest(schema, """
      - <warning>a</warning>
      - <warning>2</warning>\
      """);
    mySchemaFixture.doTest(schema, """
      - <warning>a</warning>
      - true\
      """);
    mySchemaFixture.doTest(schema, """
      - 1
      - false\
      """);
  }

  @Test
  public void testWithTags() {
    @Language("JSON") String schema =
      """
        {
          "properties": {
            "platform": {
              "enum": ["x86", "x64"]
            }
          }
        }""";
    mySchemaFixture.doTest(schema, """
      platform:
        !!str x64\
      """);
    mySchemaFixture.doTest(schema, """
      platform:
        <warning>a x64</warning>\
      """);
  }

  @Test
  public void testAmazonElasticSchema() throws Exception {
    @Language("JSON") String schema = FileUtil.loadFile(new File(getTestDataPath() + "/cloudformation.schema.json"));
    mySchemaFixture.doTest(schema, """
      Resources:
        ElasticsearchCluster:
          Type: "AWS::Elasticsearch::Domain"
          Properties:
            ElasticsearchVersion: !FindInMap [ElasticSearchConfig, !Ref AccountType, Version]
      Conditions:
        IsDev: !Equals [!Ref AccountType, dev]""");
  }

  @Test
  public void testGitlabSchema() throws Exception {
    @Language("JSON") String schema = FileUtil.loadFile(new File(getTestDataPath() + "/gitlab-ci.schema.json"));
    mySchemaFixture.doTest(schema, """
      a:
        extends: .b
        script: echo""");
  }

  @Language("JSON")
  protected static final String SCHEMA_FOR_REFS = """
    {
      "type": "object",
    
      "properties": {
        "name": { "type": "string", "enum": ["aa", "bb"] },
        "bar": {
          "required": [
            "a"
          ],
          "properties": {
            "a": {
              "type": ["array"]
            },
           "b": {          "type": ["number"]        }
          },
          "additionalProperties": false
        }
      }
    }
    """;

  @Test
  public void testRefExtends() {
    // no warning about missing required property - it should be discovered in referenced object
    // no warning about extra 'property' with name '<<' with additionalProperties=false
    mySchemaFixture.doTest(SCHEMA_FOR_REFS, """
      a: &a
        a: <warning descr="Schema validation: Incompatible types.
       Required: array. Actual: integer.">7</warning>
      
      bar:
        <<: *a
        b: 5
      """);
  }

  @Test
  public void testRefRefValid() {
    // no warnings - &a references &b, which is an array - validation passes
    mySchemaFixture.doTest(SCHEMA_FOR_REFS, """
      x: &b
        - x
        - y
      
      a: &a
        a: *b
      
      bar:
        <<: *a
        b: 5""");
  }

  @Test
  public void testRefRefInvalid() {
    mySchemaFixture.doTest(SCHEMA_FOR_REFS, """
      x: &b <warning descr="Schema validation: Incompatible types.
       Required: array. Actual: number.">7</warning>
      
      a: &a
        a: *b
      
      bar:
        <<: *a
        b: 5""");
  }

  @Test
  public void testRefRefScalarValid() {
    mySchemaFixture.doTest(SCHEMA_FOR_REFS, """
      x: &b 7
      
      a: &a
        b: *b
      
      bar:
        <<: *a
        a: <warning descr="Schema validation: Incompatible types.
       Required: array. Actual: integer.">5</warning>""");
  }

  @Test
  public void testInlineRef() {
    mySchemaFixture.doTest(SCHEMA_FOR_REFS, """
      bar:
        <<: &q
          a: <warning descr="Schema validation: Incompatible types.
       Required: array. Actual: integer.">5</warning>
        b: 5""");
  }

  static String schema(final String s) {
    return """
      {
        "type": "object",
        "properties": {
          "prop": %s
        }
      }""".formatted(s);
  }

  public static String rootObjectRedefinedSchema() {
    return """
      {
        "$schema": "http://json-schema.org/draft-04/schema#",
        "type": "object",
        "$ref" : "#/definitions/root",
        "definitions": {
          "root" : {
            "type": "object",
            "additionalProperties": false,
            "properties": {
              "r1": {
                "type": "string"
              },
              "r2": {
                "type": "string"
              }
            }
          }
        }
      }
      """;
  }

  @Test
  public void testTravisPythonVersion() throws Exception {
    @Language("JSON") String schema = FileUtil.loadFile(new File(getTestDataPath() + "/travis.schema.json"));
    mySchemaFixture.doTest(schema, "python: 3.5"); // validates as 'number'
    mySchemaFixture.doTest(schema, "python: 3.50"); // validates as 'number'
    mySchemaFixture.doTest(schema, "python: 3.50a"); // validates as 'string'
    mySchemaFixture.doTest(schema, """
      python: <warning descr="Schema validation: Incompatible types.
       Required one of: array, number, string. Actual: null.">null</warning>\
      """);
  }

  @Test
  public void testTravisNode() throws Exception {
    @Language("JSON") String schema = FileUtil.loadFile(new File(getTestDataPath() + "/travis.schema.json"));
    mySchemaFixture.doTest(schema, """
      node_js:\s
        - <warning descr="Schema validation: Incompatible types.
       Required: string. Actual: number.">2.10</warning>""");
  }

  @Test
  public void testTravisMultiDocument() throws Exception {
    @Language("JSON") String schema = FileUtil.loadFile(new File(getTestDataPath() + "/travis.schema.json"));
    mySchemaFixture.doTest(schema, """
      after_script: true
      sbt_args: <warning>1</warning>
      ---
      after_script: true
      sbt_args: <warning>1</warning>
      """);
  }

  @Test
  public void testExpNumberNotation() {
    mySchemaFixture.doTest(
      """
        {
          "properties": {
            "x": {
              "type": "number"
            }
          }
        }""",
      "x: 2.99792458e8");
  }

  @Test
  public void testTreatEmptyValueAsNull_1() {
    mySchemaFixture.doTest(
      """
        {
          "properties": {
            "x": {
              "type": "number"
            }
          }
        }""",
      """
        x:<warning descr="Schema validation: Incompatible types.
         Required: number. Actual: null."> </warning>\
        """);
  }

  @Test
  public void testTreatEmptyValueAsNull_2() {
    mySchemaFixture.doTest(
      """
        {
          "properties": {
            "x": {
              "type": "null"
            }
          }
        }""",
      "x: ");
  }

  @Test
  public void testEmptyValueInArray() {
    mySchemaFixture.doTest(
      """
        {
          "type": "object",
        
          "properties": {
            "versionAsStringArray": {
              "type": "array",
              "items": {
                "type": "string"
              }
            }
          }
        }""",
      """
        versionAsStringArray:
          -<warning descr="Schema validation: Incompatible types.
         Required: string. Actual: null."> </warning>
          <warning descr="Schema validation: Incompatible types.
         Required: string. Actual: null.">-</warning>
          - a\
        """);
  }

  @Test
  public void testEmptyFile() {
    mySchemaFixture.doTest(
      """
        {
          "type": "object",
        
          "properties": {
            "versionAsStringArray": {
              "type": "array"
            }
          },
          "required": ["versionAsStringArray"]
        }""",
      "<warning descr=\"Schema validation: Missing required property 'versionAsStringArray'\"></warning>");
  }

  @Test
  public void testEmptyValueBetweenProps() {
    mySchemaFixture.doTest(
      """
        {
          "type": "object",
        
          "properties": {
            "versionAsStringArray": {
              "type": "object",
              "properties": {
                "xxx": {
                  "type": "number"
                },
                "yyy": {
                  "type": "string"
                },
                "zzz": {
                  "type": "number"
                }
              },
              "required": ["xxx", "yyy", "zzz"]
            }
          },
          "required": ["versionAsStringArray"]
        }""",
      """
        versionAsStringArray:
          zzz: 0
          yyy:<warning descr="Schema validation: Incompatible types.
         Required: string. Actual: null.">  </warning>
          xxx: 0
        """);
  }

  @Test
  public void testDeprecation() {
    mySchemaFixture.doTest(
      """
        {
          "properties": {
            "myPropertyXxx": {
              "deprecationMessage": "Baz",
              "description": "Foo bar"
            }
          }
        }""",
      "<weak_warning descr=\"Key 'myPropertyXxx' is deprecated: Baz\">myPropertyXxx</weak_warning>: a");
  }

  @Test
  public void testDeprecationFromMatchingAnyOfBranchOnly() {
    myFixture.enableInspections(YamlJsonSchemaDeprecationInspection.class);
    mySchemaFixture.configureInitially("""
                         {
                           "type": "object",
                           "properties": {
                             "steps": {
                               "type": "array",
                               "items": {
                                 "type": "object",
                                 "properties": {
                                   "task": {
                                     "anyOf": [
                                       {
                                         "enum": ["Docker@2"]
                                       },
                                       {
                                         "deprecationMessage": "Deprecated task",
                                         "enum": ["AzurePowerShell@5"]
                                       }
                                     ]
                                   }
                                 }
                               }
                             }
                           }
                         }""",
                       """
                         steps:
                         - task: Docker@2
                         - <weak_warning descr="Key 'task' is deprecated: Deprecated task">task</weak_warning>: AzurePowerShell@5
                         """, "json");
    myFixture.checkHighlighting(true, false, true);
  }

  @Test
  public void testDeprecationFromAnyOfBranchForEmptyValue() {
    myFixture.enableInspections(YamlJsonSchemaDeprecationInspection.class);
    mySchemaFixture.configureInitially("""
                         {
                           "type": "object",
                           "properties": {
                             "task": {
                               "anyOf": [
                                 {
                                   "enum": ["Docker@2"]
                                 },
                                 {
                                   "deprecationMessage": "Deprecated task"
                                 }
                               ]
                             }
                           }
                         }""",
                       """
                         <weak_warning descr="Key 'task' is deprecated: Deprecated task">task</weak_warning>:
                         """, "json");
    myFixture.checkHighlighting(true, false, true);
  }

  @Test
  public void testPropertyNameSchema() {
    mySchemaFixture.doTest(
      """
        {
          "$schema": "http://json-schema.org/draft-06/schema#",
          "type": "object",
          "patternProperties": {
            ".*": {
              "type": "boolean"
            }
          },
          "propertyNames": {
            "enum": ["a", "b"]
          }
        }""",
      "<warning>r</warning>: true");
  }

  public void _testTypeVariants() throws IOException {
    @Language("JSON") String schema = FileUtil.loadFile(new File(getTestDataPath() + "/prometheus.schema.json"));
    mySchemaFixture.doTest(schema, """
      alerting:
        alertmanagers:
        - static_configs:
          - targets: <warning>1</warning> \s
            # - alertmanager:9093 \s
      
      rule_files:
        # - "first_rules.yml"
        # - "second_rules.yml\"""");
  }

  @Test
  public void testReducedTopLevelRangeHighlighting() {
    mySchemaFixture.doTest(
      """
        {
          "type": "object",
          "required": ["test3"]
        }""",
      """
        <warning descr="Schema validation: Missing required property 'test3'">test1: 123</warning>
        test2: 456
        """);
  }

  @Test
  public void testTopLevelRangeHighlighting() {
    mySchemaFixture.doTest(
      """
        {
          "type": "object",
          "required": ["test3"]
        }""",
      "<warning descr=\"Schema validation: Missing required property 'test3'\"></warning>");
  }
}
