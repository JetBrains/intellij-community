// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.schema;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.jsonSchema.JsonSchemaHighlightingTestBase;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.YAMLLanguage;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class YamlByYamlSchemaHighlightingTest extends JsonSchemaHighlightingTestBase {
  @NotNull
  @Override
  public String getTestDataPath() {
    return PathManagerEx.getCommunityHomePath() + "/plugins/yaml/testSrc/org/jetbrains/yaml/schema/data/highlighting";
  }

  @Override
  protected String getTestFileName() {
    return "config.yml";
  }

  @Override
  protected InspectionProfileEntry getInspectionProfile() {
    return new YamlJsonSchemaHighlightingInspection();
  }


  @Override
  protected Predicate<VirtualFile> getAvailabilityPredicate() {
    return file -> file.getFileType() instanceof LanguageFileType && ((LanguageFileType)file.getFileType()).getLanguage().isKindOf(
      YAMLLanguage.INSTANCE);
  }

  private void doTestYaml(@Language("YAML") @NotNull final String schema, @NotNull final String text) {
    configureInitially(schema, text, "yaml");
    myFixture.checkHighlighting(true, false, false);
  }

  public void testEnum1() {
    @Language("YAML") final String schema = """
      properties:
        prop:\s
          "enum":
            - 1
            - 2
            - 3
            - "18\"""";
    doTestYaml(schema, "prop: 1");
    doTestYaml(schema, "prop: <warning>foo</warning>");
  }

  public void testMissingProp() {
    @Language("YAML") final String schema = """
      properties:\s
        prop: {}
        flop: {}
      required:
        - flop""";

    doTestYaml(schema, "<warning>prop: 2</warning>");
    doTestYaml(schema, "prop: 2\nflop: a");
    doTestYaml(schema, "flop: a");
  }

  public void testNumberMultiple() {
    @Language("YAML") String schema = """
      properties:
        prop:
          type: number
          multipleOf: 2""";
    doTestYaml(schema, "prop: <warning descr=\"Schema validation: Is not multiple of 2\">3</warning>");
    doTestYaml(schema, "prop: 4");
  }

  public void testNumberMinMax() {
    doTestYaml("""
                 properties:
                   prop:\s
                     type: number
                     minimum: 0
                     maximum: 100
                     exclusiveMaximum: true""", "prop: 14");
  }

  public void testEnum() {
    @Language("YAML") final String schema = """
      properties:
        prop:
          enum:
            - 1
            - 2
            - 3
            - "18\"""";
    doTestYaml(schema, "prop: 18");
    doTestYaml(schema, "prop: 2");
    doTestYaml(schema, "prop: <warning descr=\"Schema validation: Value should be one of: 1, 2, 3, \\\"18\\\"\">6</warning>");
  }

  public void testSimpleString() {
    @Language("YAML") final String schema = """
      properties:
        prop:\s
          type: string
          minLength: 2
          maxLength: 3""";
    doTestYaml(schema, "prop: <warning descr=\"Schema validation: String is shorter than 2\">s</warning>");
    doTestYaml(schema, "prop: sh");
    doTestYaml(schema, "prop: sho");
    doTestYaml(schema, "prop: <warning descr=\"Schema validation: String is longer than 3\">shor</warning>");
  }

  public void testArray() {
    @Language("YAML") final String schema = schema("""
                                                     {
                                                       "type": "array",
                                                       "items": {
                                                         "type": "number", "minimum": 18  }
                                                     }""");
    doTestYaml(schema, "prop:\n - 101\n - 102");
    doTestYaml(schema, "prop:\n - <warning descr=\"Schema validation: Less than the minimum 18\">16</warning>");
    doTestYaml(schema, "prop:\n - <warning descr=\"Schema validation: Incompatible types.\n Required: number. Actual: string.\">test</warning>");
  }

  public void testTopLevelArray() {
    @Language("YAML") final String schema = """
      type: array
      items:
        type: number
        minimum: 18
      """;
    doTestYaml(schema, "- 101\n- 102");
  }

  public void testTopLevelObjectArray() {
    @Language("YAML") final String schema = """
      {
        "type": "array",
        "items": {
          "type": "object", "properties": {"a": {"type": "number"}}  }
      }""";
    doTestYaml(schema, "- a: <warning descr=\"Schema validation: Incompatible types.\n Required: number. Actual: boolean.\">true</warning>");
    doTestYaml(schema, "- a: 18");
  }

  public void testArrayTuples1() {
    @Language("YAML") final String schema = schema("""
                                                     {
                                                       "type": "array",
                                                       "items": [{
                                                         "type": "number", "minimum": 18  }, {"type" : "string"}]
                                                     }""");
    doTestYaml(schema,
               "prop:\n - 101\n - <warning descr=\"Schema validation: Incompatible types.\n Required: string. Actual: integer.\">102</warning>");
  }

  public void testArrayTuples2() {
    @Language("YAML") final String schema2 = schema("""
                                                      {
                                                        "type": "array",
                                                        "items": [{
                                                          "type": "number", "minimum": 18  }, {"type" : "string"}],
                                                      "additionalItems": false}""");
    doTestYaml(schema2, "prop:\n - 101\n - <warning descr=\"Schema validation: Incompatible types.\n Required: string. Actual: integer.\">102</warning>\n - <warning descr=\"Schema validation: Additional items are not allowed\">additional</warning>");
  }

  public void testArrayLength() {
    @Language("YAML") final String schema = schema("{\"type\": \"array\", \"minItems\": 2, \"maxItems\": 3}");
    doTestYaml(schema, "prop:\n <warning descr=\"Schema validation: Array is shorter than 2\">- 1</warning>");
    doTestYaml(schema, "prop:\n - 1\n - 2");
    doTestYaml(schema, "prop:\n <warning descr=\"Schema validation: Array is longer than 3\">- 1\n - 2\n - 3\n - 4</warning>");
  }

  public void testArrayUnique() {
    @Language("YAML") final String schema = schema("{\"type\": \"array\", \"uniqueItems\": true}");
    doTestYaml(schema, "prop:\n - 1\n - 2");
    doTestYaml(schema, "prop:\n - <warning descr=\"Schema validation: Item is not unique\">1</warning>\n - 2\n - test\n - <warning descr=\"Schema validation: Item is not unique\">1</warning>");
  }

  public void testMetadataIsOk() {
    @Language("YAML") final String schema = """
      {
        "title" : "Match anything",
        "description" : "This is a schema that matches anything.",
        "default" : "Default value"
      }""";
    doTestYaml(schema, "anything: 1");
  }

  public void testRequiredField() {
    @Language("YAML") final String schema = "{\"type\": \"object\", \"properties\": {\"a\": {}, \"b\": {}}, \"required\": [\"a\"]}";
    doTestYaml(schema, "a: 11");
    doTestYaml(schema, "a: 1\nb: true");
    doTestYaml(schema, "<warning descr=\"Schema validation: Missing required property 'a'\">b: alarm</warning>");
  }

  public void testInnerRequired() {
    @Language("YAML") final String schema = schema("{\"type\": \"object\", \"properties\": {\"a\": {}, \"b\": {}}, \"required\": [\"a\"]}");
    doTestYaml(schema, "prop:\n a: 11");
    doTestYaml(schema, "prop:\n a: 1\n b: true");
    doTestYaml(schema, "prop:\n <warning descr=\"Schema validation: Missing required property 'a'\">b: alarm</warning>");
  }

  public void testAdditionalPropertiesAllowed() {
    @Language("YAML") final String schema = schema("{}");
    doTestYaml(schema, "prop:\n q: true\n someStuff: 20");
  }

  public void testAdditionalPropertiesDisabled() {
    @Language("YAML") final String schema = "{\"type\": \"object\", \"properties\": {\"prop\": {}}, \"additionalProperties\": false}";
    // not sure abt inner object
    doTestYaml(schema, "prop:\n q: true\n<warning descr=\"Schema validation: Property 'someStuff' is not allowed\">someStuff: 20</warning>");
  }

  public void testAdditionalPropertiesSchema() {
    @Language("YAML") final String schema = "{\"type\": \"object\", \"properties\": {\"a\": {}}," +
                                            "\"additionalProperties\": {\"type\": \"number\"}}";
    doTestYaml(schema, "a: moo\nb: 5\nc: <warning descr=\"Schema validation: Incompatible types.\n Required: number. Actual: string.\">foo</warning>");
  }

  public void testMinMaxProperties() {
    @Language("YAML") final String schema = "{\"type\": \"object\", \"minProperties\": 2, \"maxProperties\": 3}";
    doTestYaml(schema, "<warning descr=\"Schema validation: Number of properties is less than 2\">a: 3</warning>");
    doTestYaml(schema, "a: 1\nb: 5");
    doTestYaml(schema, "<warning descr=\"Schema validation: Number of properties is greater than 3\">a: 1\nb: 22\nc: 333\nd: 4444</warning>");
  }

  public void testOneOf() {
    final List<String> subSchemas = new ArrayList<>();
    subSchemas.add("{\"type\": \"number\"}");
    subSchemas.add("{\"type\": \"boolean\"}");
    @Language("YAML") final String schema = schema("{\"oneOf\": [" + StringUtil.join(subSchemas, ", ") + "]}");
    doTestYaml(schema, "prop: 5");
    doTestYaml(schema, "prop: true");
    doTestYaml(schema, "prop: <warning descr=\"Schema validation: Incompatible types.\n Required one of: boolean, number. Actual: string.\">aaa</warning>");
  }

  public void testOneOfForTwoMatches() {
    final List<String> subSchemas = new ArrayList<>();
    subSchemas.add("{\"type\": \"string\", \"enum\": [\"a\", \"b\"]}");
    subSchemas.add("{\"type\": \"string\", \"enum\": [\"a\", \"c\"]}");
    @Language("YAML") final String schema = schema("{\"oneOf\": [" + StringUtil.join(subSchemas, ", ") + "]}");
    doTestYaml(schema, "prop: b");
    doTestYaml(schema, "prop: c");
    doTestYaml(schema, "prop: <warning descr=\"Schema validation: Validates to more than one variant\">a</warning>");
  }

  public void testOneOfSelectError() {
    final List<String> subSchemas = new ArrayList<>();
    subSchemas.add("""
                     {"type": "string",
                               "enum": [
                                 "off", "warn", "error"
                               ]}""");
    subSchemas.add("{\"type\": \"integer\"}");
    @Language("YAML") final String schema = schema("{\"oneOf\": [" + StringUtil.join(subSchemas, ", ") + "]}");
    doTestYaml(schema, "prop: off");
    doTestYaml(schema, "prop: 12");
    doTestYaml(schema, "prop: <warning descr=\"Schema validation: Value should be one of: \\\"off\\\", \\\"warn\\\", \\\"error\\\"\">wrong</warning>");
  }

  public void testAnyOf() {
    final List<String> subSchemas = new ArrayList<>();
    subSchemas.add("{\"type\": \"string\", \"enum\": [\"a\", \"b\"]}");
    subSchemas.add("{\"type\": \"string\", \"enum\": [\"a\", \"c\"]}");
    @Language("YAML") final String schema = schema("{\"anyOf\": [" + StringUtil.join(subSchemas, ", ") + "]}");
    doTestYaml(schema, "prop: b");
    doTestYaml(schema, "prop: c");
    doTestYaml(schema, "prop: a");
    doTestYaml(schema, "prop: <warning descr=\"Schema validation: Value should be one of: \\\"a\\\", \\\"b\\\", \\\"c\\\"\">d</warning>");
  }

  public void testAllOf() {
    final List<String> subSchemas = new ArrayList<>();
    subSchemas.add("{\"type\": \"integer\", \"multipleOf\": 2}");
    subSchemas.add("{\"enum\": [1,2,3]}");
    @Language("YAML") final String schema = schema("{\"allOf\": [" + StringUtil.join(subSchemas, ", ") + "]}");
    doTestYaml(schema, "prop: <warning descr=\"Schema validation: Is not multiple of 2\">1</warning>");
    doTestYaml(schema, "prop: <warning descr=\"Schema validation: Value should be one of: 1, 2, 3\">4</warning>");
    doTestYaml(schema, "prop: 2");
  }

  // ----

  public void testObjectInArray() {
    @Language("YAML") final String schema = schema("{\"type\": \"array\", \"items\": {\"type\": \"object\"," +
                                                   "\"properties\": {" +
                                                   "\"innerType\":{}, \"innerValue\":{}" +
                                                   "}, \"additionalProperties\": false" +
                                                   "}}");
    doTestYaml(schema, "prop:\n- innerType: aaa\n  <warning descr=\"Schema validation: Property 'alien' is not allowed\">alien: bee</warning>");
  }

  public void testObjectDeeperInArray() {
    final String innerTypeSchema = "{\"properties\": {\"only\": {}}, \"additionalProperties\": false}";
    @Language("YAML") final String schema = schema("{\"type\": \"array\", \"items\": {\"type\": \"object\"," +
                                                   "\"properties\": {" +
                                                   "\"innerType\":" + innerTypeSchema +
                                                   "}, \"additionalProperties\": false" +
                                                   "}}");
    doTestYaml(schema,
           "prop:\n- innerType:\n   only: true\n   <warning descr=\"Schema validation: Property 'hidden' is not allowed\">hidden: false</warning>");
  }

  public void testInnerObjectPropValueInArray() {
    @Language("YAML") final String schema = "{\"properties\": {\"prop\": {\"type\": \"array\", \"items\": {\"enum\": [1,2,3]}}}}";
    doTestYaml(schema, "prop:\n - 1\n - 3");
    doTestYaml(schema, "prop:\n - <warning descr=\"Schema validation: Value should be one of: 1, 2, 3\">out</warning>");
  }

  public void testAllOfProperties() {
    @Language("YAML") final String schema = "{\"allOf\": [{\"type\": \"object\", \"properties\": {\"first\": {}}}," +
                                            " {\"properties\": {\"second\": {\"enum\": [33,44]}}}], \"additionalProperties\": false}";
    //    doTestYaml(schema, "first: true\nsecond: <warning descr=\"Schema validation: Value should be one of: [33, 44]\">null</warning>");
    doTestYaml(schema, "first: true\nsecond: 44\n<warning descr=\"Schema validation: Property 'other' is not allowed\">other: 15</warning>");
    doTestYaml(schema, "first: true\nsecond: <warning descr=\"Schema validation: Value should be one of: 33, 44\">12</warning>");
  }

  public void testWithWaySelection() {
    final String subSchema1 = "{\"enum\": [1,2,3,4,5]}";
    final String subSchema2 = "{\"type\": \"array\", \"items\": {\"properties\": {\"kilo\": {}}, \"additionalProperties\": false}}";
    @Language("YAML") final String schema = "{\"properties\": {\"prop\": {\"oneOf\": [" + subSchema1 + ", " + subSchema2 + "]}}}";
    doTestYaml(schema, "prop:\n - <warning descr=\"Schema validation: Property 'foxtrot' is not allowed\">foxtrot: 15</warning>\n   kilo: 20");
  }

  public void testPatternPropertiesHighlighting() {
    @Language("YAML") final String schema = """
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
    doTestYaml(schema, """
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

  public void testPatternPropertiesFromIssue() {
    @Language("YAML") final String schema = """
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
    doTestYaml(schema,
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

  public void testPatternForPropertyValue() {
    @Language("YAML") final String schema = """
      {
        "properties": {
          "withPattern": {
            "pattern": "p[0-9]"
          }
        }
      }""";
    final String correctText = "withPattern: p1";
    final String wrongText = "withPattern: <warning descr=\"Schema validation: String violates the pattern: 'p[0-9]'\">wrong</warning>";
    doTestYaml(schema, correctText);
    doTestYaml(schema, wrongText);
  }

  public void testPatternWithSpecialEscapedSymbols() {
    @Language("YAML") final String schema = """
      {
        "properties": {
          "withPattern": {
            "pattern": "^\\\\d{4}\\\\-(0?[1-9]|1[012])\\\\-(0?[1-9]|[12][0-9]|3[01])$"
          }
        }
      }""";
    @Language("yaml") final String correctText = "withPattern: 1234-11-11";
    final String wrongText = "withPattern: <warning descr=\"Schema validation: String violates the pattern: '^\\d{4}\\-(0?[1-9]|1[012])\\-(0?[1-9]|[12][0-9]|3[01])$'\">wrong</warning>\n";
    doTestYaml(schema, correctText);
    doTestYaml(schema, wrongText);
  }

  // ---


  public void testRootObjectRedefinedAdditionalPropertiesForbidden() {
    doTestYaml(rootObjectRedefinedSchema(), "<warning descr=\"Schema validation: Property 'a' is not allowed\">a: true</warning>\n" +
                                        "r1: allowed!");
  }

  public void testNumberOfSameNamedPropertiesCorrectlyChecked() {
    @Language("YAML") final String schema = """
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
    doTestYaml(schema,
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

  public void testManyDuplicatesInArray() {
    @Language("YAML") final String schema = """
      {
        "properties": {
          "array":{
            "type": "array",
            "uniqueItems": true
          }
        }
      }""";
    doTestYaml(schema, """
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

  public void testPropertyValueAlsoHighlightedIfPatternIsInvalid() {
    @Language("YAML") final String schema = """
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
    doTestYaml(schema, text);
  }

  public void testNotSchema() {
    @Language("YAML") final String schema = """
      {"properties": {
          "not_type": { "not": { "type": "string" } }
        }}""";
    doTestYaml(schema, "not_type: <warning descr=\"Schema validation: Validates against 'not' schema\">wrong</warning>");
  }

  public void testNotSchemaCombinedWithNormal() {
    @Language("YAML") final String schema = """
      {"properties": {
          "not_type": {
            "pattern": "^[a-z]*[0-5]*$",
            "not": { "pattern": "^[a-z]{1}[0-5]$" }
          }
        }}""";
    doTestYaml(schema, "not_type: va4");
    doTestYaml(schema, "not_type: <warning descr=\"Schema validation: Validates against 'not' schema\">a4</warning>");
    doTestYaml(schema, "not_type: <warning descr=\"Schema validation: String violates the pattern: '^[a-z]*[0-5]*$'\">4a4</warning>");
  }

  public void testDoNotMarkOneOfThatDiffersWithFormat() {
    @Language("YAML") final String schema = """
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
    doTestYaml(schema, "withFormat: localhost");
  }

  public void testAcceptSchemaWithoutType() {
    @Language("YAML") final String schema = """
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
    doTestYaml(schema, "withFormat: localhost");
  }

  public void testArrayItemReference() {
    @Language("YAML") final String schema = """
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
    doTestYaml(schema, "- 1\n- 2");
    doTestYaml(schema, "- 1\n- <warning>foo</warning>");
  }

  public void testValidateAdditionalItems() {
    @Language("YAML") final String schema = """
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
    doTestYaml(schema, "- true\n- true");
    doTestYaml(schema, "- true\n- true\n- 1\n- 2\n- 3");
    doTestYaml(schema, "- true\n- true\n- 1\n- <warning>qq</warning>");
  }



  public void testExclusiveMinMaxV6_1() {
    @Language("YAML") String exclusiveMinSchema = "{\"properties\": {\"prop\": {\"exclusiveMinimum\": 3}}}";
    doTestYaml(exclusiveMinSchema, "prop: <warning>2</warning>");
    doTestYaml(exclusiveMinSchema, "prop: <warning>3</warning>");
    doTestYaml(exclusiveMinSchema, "prop: 4");
  }

  public void testExclusiveMinMaxV6_2() {
    @Language("YAML") String exclusiveMaxSchema = "{\"properties\": {\"prop\": {\"exclusiveMaximum\": 3}}}";
    doTestYaml(exclusiveMaxSchema, "prop: 2");
    doTestYaml(exclusiveMaxSchema, "prop: <warning>3</warning>");
    doTestYaml(exclusiveMaxSchema, "prop: <warning>4</warning>");
  }

  /*todo later
  public void testPropertyNamesV6() throws Exception {
    doTestYaml("{\"propertyNames\": {\"minLength\": 7}}", "{<warning>\"prop\"</warning>: 2}");
    doTestYaml("{\"properties\": {\"prop\": {\"propertyNames\": {\"minLength\": 7}}}}", "{\"prop\": {<warning>\"qq\"</warning>: 7}}");
  }*/

  public void testContainsV6() {
    @Language("YAML") String schema = "{\"properties\": {\"prop\": {\"type\": \"array\", \"contains\": {\"type\": \"number\"}}}}";
    doTestYaml(schema, "prop:\n <warning>- a\n - true</warning>");
    doTestYaml(schema, "prop:\n - a\n - true\n - 1");
  }

  public void testConstV6() {
    @Language("YAML") String schema = "{\"properties\": {\"prop\": {\"type\": \"string\", \"const\": \"foo\"}}}";
    doTestYaml(schema, "prop: <warning>a</warning>");
    doTestYaml(schema, "prop: <warning>5</warning>");
    doTestYaml(schema, "prop: foo");
  }

  public void testIfThenElseV7() {
    @Language("YAML") String schema = """
      {
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
    doTestYaml(schema, "c: <warning>5</warning>");
    doTestYaml(schema, "c: true");
    doTestYaml(schema, "<warning>a: a\nc: true</warning>");
    doTestYaml(schema, "a: a\nb: <warning>true</warning>");
    doTestYaml(schema, "a: a\nb: 5");
  }

  public void testNestedOneOf() {
    @Language("YAML") String schema = """
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

    doTestYaml(schema, "type: good");
    doTestYaml(schema, "type: ok");
    doTestYaml(schema, "type: <warning>doog</warning>");
    doTestYaml(schema, "type: <warning>ko</warning>");
  }

  public void testArrayRefs() {
    @Language("YAML") String schema = """
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

    doTestYaml(schema, "- 1\n- <warning>2</warning>");
    doTestYaml(schema, "- <warning>a</warning>\n- <warning>2</warning>");
    doTestYaml(schema, "- <warning>a</warning>\n- true");
    doTestYaml(schema, "- 1\n- false");
  }

  public void testWithTags() {
    @Language("YAML") String schema = "{\"properties\": { \"platform\": { \"enum\": [\"x86\", \"x64\"] } }}";
    doTestYaml(schema, "platform:\n  !!str x64");
    doTestYaml(schema, "platform:\n  <warning>a x64</warning>");
  }

  public void testAmazonElasticSchema() throws Exception {
    @Language("YAML") String schema = FileUtil.loadFile(new File(getTestDataPath() + "/cloudformation.schema.json"));
    doTestYaml(schema, """
      Resources:
        ElasticsearchCluster:
          Type: "AWS::Elasticsearch::Domain"
          Properties:
            ElasticsearchVersion: !FindInMap [ElasticSearchConfig, !Ref AccountType, Version]
      Conditions:
        IsDev: !Equals [!Ref AccountType, dev]""");
  }

  public void testGitlabSchema() throws Exception {
    @Language("YAML") String schema = FileUtil.loadFile(new File(getTestDataPath() + "/gitlab-ci.schema.json"));
    doTestYaml(schema, """
      a:
        extends: .b
        script: echo""");
  }

  @Language("YAML")
  private static final String SCHEMA_FOR_REFS  = """
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

  public void testRefExtends() {
    // no warning about missing required property - it should be discovered in referenced object
    // no warning about extra 'property' with name '<<' with additionalProperties=false
    doTestYaml(SCHEMA_FOR_REFS, """
      a: &a
        a: <warning descr="Schema validation: Incompatible types.
       Required: array. Actual: integer.">7</warning>

      bar:
        <<: *a
        b: 5
      """);
  }

  public void testRefRefValid() {
    // no warnings - &a references &b, which is an array - validation passes
    doTestYaml(SCHEMA_FOR_REFS, """
      x: &b
        - x
        - y

      a: &a
        a: *b

      bar:
        <<: *a
        b: 5""");
  }

  public void testRefRefInvalid() {
    doTestYaml(SCHEMA_FOR_REFS, """
      x: &b <warning descr="Schema validation: Incompatible types.
       Required: array. Actual: number.">7</warning>

      a: &a
        a: *b

      bar:
        <<: *a
        b: 5""");
  }
  public void testRefRefScalarValid() {
    doTestYaml(SCHEMA_FOR_REFS, """
      x: &b 7

      a: &a
        b: *b

      bar:
        <<: *a
        a: <warning descr="Schema validation: Incompatible types.
       Required: array. Actual: integer.">5</warning>""");
  }

  public void testInlineRef() {
    doTestYaml(SCHEMA_FOR_REFS, """
      bar:
        <<: &q
          a: <warning descr="Schema validation: Incompatible types.
       Required: array. Actual: integer.">5</warning>
        b: 5""");
  }

  static String schema(final String s) {
    return "{\"type\": \"object\", \"properties\": {\"prop\": " + s + "}}";
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

  public void testTravisPythonVersion() throws Exception {
    @Language("YAML") String schema = FileUtil.loadFile(new File(getTestDataPath() + "/travis.schema.json"));
    doTestYaml(schema, "python: 3.5"); // validates as 'number'
    doTestYaml(schema, "python: 3.50"); // validates as 'number'
    doTestYaml(schema, "python: 3.50a"); // validates as 'string'
    doTestYaml(schema, "python: <warning descr=\"Schema validation: Incompatible types.\n Required one of: array, number, string. Actual: null.\">null</warning>");
  }

  public void testTravisNode() throws Exception {
    @Language("YAML") String schema = FileUtil.loadFile(new File(getTestDataPath() + "/travis.schema.json"));
    doTestYaml(schema, """
      node_js:\s
        - <warning descr="Schema validation: Incompatible types.
       Required: string. Actual: number.">2.10</warning>""");
  }

  public void testExpNumberNotation() {
    doTestYaml("""
                 {
                   "properties": {
                     "x": {
                       "type": "number"
                     }
                   }
                 }""", "x: 2.99792458e8");
  }

  public void testTreatEmptyValueAsNull_1() {
    doTestYaml("""
                 {
                   "properties": {
                     "x": {
                       "type": "number"
                     }
                   }
                 }""", "x:<warning descr=\"Schema validation: Incompatible types.\n Required: number. Actual: null.\"> </warning>");
  }

  public void testTreatEmptyValueAsNull_2() {
    doTestYaml("""
                 {
                   "properties": {
                     "x": {
                       "type": "null"
                     }
                   }
                 }""", "x: ");
  }

  public void testEmptyValueInArray() {
    doTestYaml("""
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
                 }""", """
                 versionAsStringArray:
                   -<warning descr="Schema validation: Incompatible types.
                  Required: string. Actual: null."> </warning>
                   <warning descr="Schema validation: Incompatible types.
                  Required: string. Actual: null.">-</warning>
                   - a""");
  }

  public void testEmptyFile() {
    doTestYaml("""
                 {
                   "type": "object",

                   "properties": {
                     "versionAsStringArray": {
                       "type": "array"
                     }
                   },
                   "required": ["versionAsStringArray"]
                 }""", "<warning descr=\"Schema validation: Missing required property 'versionAsStringArray'\"></warning>");
  }

  public void testEmptyValueBetweenProps() {
    doTestYaml("""
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
                 }""", """
                 versionAsStringArray:
                   zzz: 0
                   yyy:<warning descr="Schema validation: Incompatible types.
                  Required: string. Actual: null.">  </warning>
                   xxx: 0""");
  }

  public void testDeprecation() {
    doTestYaml("""
                 {"properties": {
                     "myPropertyXxx": {
                       "deprecationMessage": "Baz",
                       "description": "Foo bar"
                     }
                   }}""", "<weak_warning descr=\"Key 'myPropertyXxx' is deprecated: Baz\">myPropertyXxx</weak_warning>: a");
  }
}
