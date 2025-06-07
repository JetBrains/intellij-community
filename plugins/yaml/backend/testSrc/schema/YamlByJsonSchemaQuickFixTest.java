// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.schema;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.jsonSchema.fixes.JsonSchemaQuickFixTestBase;
import org.intellij.lang.annotations.Language;
import org.jetbrains.yaml.YAMLLanguage;

import java.util.function.Predicate;

public class YamlByJsonSchemaQuickFixTest extends JsonSchemaQuickFixTestBase {
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

  public void testAddMissingProperty() {
    @Language("JSON") String schema = """
      {
        "properties": {
          "a": {
            "default": "q"
          }
        },
        "required": ["a", "b"]
      }""";
    String text = "<warning>c: 5</warning>";
    String expectedAfterFix = """
      c: 5
      a: q
      b:""";
    doTest(schema, text, "Add missing properties 'a', 'b'", expectedAfterFix);
  }

  public void testRemoveProhibitedProperty() {
    doTest("""
             {
               "properties": {
                 "a": {},
                 "c": {}
               },
               "additionalProperties": false
             }""", "a: 5\n<warning>b<caret></warning>: 6\nc: 7", "Remove prohibited property 'b'", "a: 5\n" +
                                                                                                   "c: 7");
  }

  public void testEmptyFile() {
    doTest("""
             {
               "type": "object",

               "properties": {
                 "versionAsStringArray": {
                   "type": "array"
                 }
               },
               "required": ["versionAsStringArray"]
             }""", "<warning></warning>", "Add missing property 'versionAsStringArray'", "versionAsStringArray:\n" +
                                                                                         "  - ");
  }

  public void testEmptyObject() {
    doTest("""
             {
               "type": "object",

               "properties": {
                 "versionAsStringArray": {
                   "type": "object",
                   "properties": {
                     "xxx": {
                       "type": "array"
                     }
                   },
                   "required": ["xxx"]
                 }
               },
               "required": ["versionAsStringArray"]
             }""", "versionAsStringArray:\n" +
                   "<warning>  <caret></warning>", "Add missing property 'xxx'", """
             versionAsStringArray:
               xxx:
                 -\s""");
  }

  public void testAddPropAfterObjectProp() {
    @Language("JSON") String schema = """
      {
        "type": "object",
        "properties": {
          "obj": {
            "type": "object",
            "properties": {
              "foo": {
                "type": "number"
              },
              "bar": {
                "type": "object"
              },
              "baz": {
                "type": "number"
              }
            },
            "required": ["foo", "bar", "baz"]
          }
        },
        "required": ["obj"]
      }""";
    String text = """
      obj:
        <warning>foo: 42
        bar:
          aa: 42
          ab: 42<caret></warning>""";
    String afterFix = """
      obj:
        foo: 42
        bar:
          aa: 42
          ab: 42
        baz: 0""";
    doTest(schema, text, "Add missing property 'baz'", afterFix);
  }

  public void testAddPropInHashMapping() {
    @Language("JSON") String schema = """
      {
        "type": "object",
        "properties": {
          "name": {
            "type": "string"
          },
          "age": {
            "type": "integer",
            "minimum": 0
          },
          "addresses": {
             "type": "array",
             "items": {
               "type": "object",
               "properties": {
                 "street": {
                   "type": "string"
                 },
                 "city": {
                   "type": "string"
                 },
                 "postalCode": {
                   "type": "string",
                   "pattern": "\\\\d{5}"
                 }
               },
               "required": ["street", "city", "postalCode"]
             }
          }
        },
        "required": ["name", "age"]
      }""";
    String text = """
      name: masha
      age: 18
      addresses:
        - <warning>{ city: DefaultCity<caret> }</warning>""";
    String afterFix = """
      name: masha
      age: 18
      addresses:
        - { city: DefaultCity, street:,postalCode: }""";
    doTest(schema, text, "Add missing properties 'postalCode', 'street'", afterFix);
  }

  public void testAddPropAfterObjectProp_wrongFormatting() {
    @Language("JSON") String schema = """
      {
        "type": "object",
        "properties": {
          "obj": {
            "type": "object",
            "properties": {
              "foo": {
                "type": "number"
              },
              "bar": {
                "type": "object"
              },
              "baz": {
                "type": "number"
              }
            },
            "required": ["foo", "bar", "baz"]
          }
        },
        "required": ["obj"]
      }""";
    String text = """
      obj:
           <warning>foo: 42
           bar:
              aa: 42
              ab: 42<caret></warning>""";
    String afterFix = """
      obj:
        foo: 42
        bar:
          aa: 42
          ab: 42
        baz: 0""";
    doTest(schema, text, "Add missing property 'baz'", afterFix);
  }

  public void testEmptyObjectMultipleProps() {
    String text = """
      xyzObject:
      <warning>  <caret></warning>""";
    String afterFix = """
      xyzObject:
        xxx: 0
        yyy:
        zzz: 0""";
    doTest(SCHEMA_WITH_NESTED_XYZ, text, "Add missing properties 'xxx', 'yyy', 'zzz'", afterFix);
  }

  public void testAddMultiplePropsToNestedObject() {
    String text = """
      xyzObject:
        <warning>yyy: value<caret></warning>
      """;
    String afterFix = """
      xyzObject:
        yyy: value
        xxx: 0
        zzz: 0
      """;
    doTest(SCHEMA_WITH_NESTED_XYZ, text, "Add missing properties 'xxx', 'zzz'", afterFix);
  }

  @Language("JSON") private static final String SCHEMA_WITH_NESTED_XYZ = """
    {
      "type": "object",

      "properties": {
        "xyzObject": {
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
      "required": ["xyzObject"]
    }
    """;

  public void testAddMissingPropertyAfterComment() {
    doTest(SCHEMA_WITH_NESTING, "image: #aaa\n" +
                                "<warning descr=\"Schema validation: Missing required property 'tag'\"> <caret>  </warning>", "Add missing property 'tag'", "image: #aaa\n" +
                                                                                                                                           "  tag: ");
  }

  public void testAddMissingPropertyAfterWhitespace() {
    doTest(SCHEMA_WITH_NESTING, "image: \n" +
                                "<warning descr=\"Schema validation: Missing required property 'tag'\"> <caret>  </warning>", "Add missing property 'tag'", "image:\n" +
                                                                                                                                           "  tag: ");
  }

  public void testSuggestEnumValuesFix() {
    @Language("JSON") String schema = """
      {
        "required": ["x", "y"],
        "properties": {
          "x": {
            "enum": ["xxx", "yyy", "zzz"]
          },
          "y": {
            "enum": [1, 2, 3, 4, 5]
          }
        }
      }""";
    doTest(schema, """
      "x"<warning descr="Schema validation: Value should be one of: \\"xxx\\", \\"yyy\\", \\"zzz\\""><caret>:</warning>
      """, "Replace with allowed value", "\"x\": xxx\n");
    doTest(schema, """
      "y": <warning descr="Schema validation: Value should be one of: 1, 2, 3, 4, 5"><caret>no</warning>
      """, "Replace with allowed value", "\"y\": 1\n");
  }
  
  public void testSuggestEnumValuesFixInjection() {
    myFixture.setCaresAboutInjection(false);
    @Language("JSON") String schema = """
      {
        "properties": {
          "inner": {
            "enum": ["xxx", "yyy", "zzz"]
          },
          "outer": {
            "x-intellij-language-injection": "yaml"
          }
        }
      }""";
    doTest(schema, """
      "outer": |
        "inner": <warning descr="Schema validation: Value should be one of: \\"xxx\\", \\"yyy\\", \\"zzz\\""><caret>"oops"</warning>""",
           "Replace with allowed value", """
             "outer": |
               "inner": xxx""");
  }

  @Language("JSON") private static final String SCHEMA_WITH_NESTING = """
    {
      "properties": {
        "image": {
          "description": "Container Image",
          "properties": {
            "repo": {
              "type": "string"
            },
            "tag": {
              "type": "string"
            }
          },
          "required": [
            "tag"
          ],
          "type": "object"
        }
      },
      "title": "Values",
      "type": "object"
    }
    """;

}
