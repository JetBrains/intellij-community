// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.schema;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.jetbrains.jsonSchema.impl.JsonBySchemaCompletionBaseTest;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class YamlByJsonSchemaCompletionTest extends JsonBySchemaCompletionBaseTest {
  @NotNull
  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath() + "/plugins/yaml/backend/testData/org/jetbrains/yaml/schema/data/completion";
  }

  public void testTopLevel() throws Exception {
    testBySchema("{\"properties\": {\"prima\": {}, \"proto\": {}, \"primus\": {}}}", "proto: 5\n<caret>", "someFile.yml",
                 "prima", "primus");
  }

  public void testNested() throws Exception {
    testBySchema("{\"properties\": {\"prima\": {\"properties\": {\"proto\": {}, \"primus\": {}}}}}",
       "prima:\n  <caret>", "someFile.yml",
                 "primus", "proto");
  }

  public void testNestedInArray() throws Exception {
    testBySchema("""
                   {
                     "properties": {
                       "colorMap": {
                         "type": "array",
                         "items": {
                           "properties": {
                             "hue": {
                               "type": "string"
                             },
                             "saturation": {
                               "type": "string"
                             },
                             "value": {
                               "type": "string"
                             }
                           }
                         }
                       }
                     }
                   }""", "colorMap:\n  - <caret>", "someFile.yml", "hue", "saturation", "value");
  }

  public void testEnumInArray() throws Exception {
    testBySchema("""
                   {
                     "properties": {
                       "colorMap": {
                         "type": "array",
                         "items": {
                           "enum": ["white", "blue", "red"]
                         }
                       }
                     }
                   }""", "colorMap:\n  - <caret>", "someFile.yml", "blue", "red", "white");
  }

  public void testBeforeProps() throws Exception {
    testBySchema("""
                   {
                     "properties": {
                       "root": {
                         "properties": {
                           "item1": { },
                           "item2": { },
                           "item3": { },
                           "item4": { },
                           "item5": { },
                           "item6": { }
                         }
                       }
                     }
                   }""", """
                   root:
                       item1: 1
                       item3: 3
                       <caret>
                       item5: 5""", "someFile.yml", "item2", "item4", "item6");
  }

  public void testPropInArray() throws Exception {
    @Language("JSON") String schemaText = FileUtil.loadFile(new File(getTestDataPath() + "/ifThenElseSchema.json"));
    testBySchema(schemaText, """
      provider:
        - select: A
          <caret>
        - select: B""",
     "someFile.yml",
     LookupElement::getLookupString,
     CompletionType.SMART,
     "var_a_1", "var_a_2");
    testBySchema(schemaText, """
      provider:
        - select: A
        - select: B
          <caret>""",
     "someFile.yml",
     LookupElement::getLookupString,
     CompletionType.SMART,
     "var_b_1", "var_b_2", "var_b_3");
  }

  public void testNestedPropertyNotSuppressedByOuter() throws Exception {
    @Language("JSON") String schema = "{\"properties\": {\"Identifier\": {\"type\": \"string\"}, \"Baa\": {\"properties\": {\"Identifier\": {\"type\": \"string\"}}}}}";
    testBySchema(schema, "Identifier: \"123\"\nBaa:\n  <caret>", "someFile.yml", "Identifier");
  }

  public void testNestedPropertySuppressedBySameLevel() throws Exception {
    @Language("JSON") String schema = "{\"properties\": {\"Identifier\": {\"type\": \"string\"}, \"Baa\": {\"properties\": {\"Identifier\": {\"type\": \"string\"}}}}}";
    testBySchema(schema, "Identifier: \"123\"\nBaa:\n  Identifier: \"already here\"\n  <caret>", "someFile.yml");
  }

  public void testDeepNestingIsolation() throws Exception {
    @Language("JSON") String schema = """
      {
        "properties": {
          "Level1": {
            "properties": {
              "Level2": {
                "properties": {
                  "Level3": {
                    "properties": {
                      "Level1": { "type": "string" },
                      "Other": { "type": "string" }
                    }
                  }
                }
              }
            }
          }
        }
      }""";
    testBySchema(schema, "Level1:\n  Level2:\n    Level3:\n      Level1: \"value\"\n      <caret>", "someFile.yml", "Other");
  }

  public void testTransitionFromKeyToNewObject() throws Exception {
    @Language("JSON") String schema = "{\"properties\": {\"Identifier\": {}, \"Baa\": {\"properties\": {\"Nested\": {}}}}}";
    // Caret not yet indented - should suggest top-level properties
    testBySchema(schema, "Baa:\n<caret>", "someFile.yml", "Identifier");
    // Caret indented - should suggest nested properties
    testBySchema(schema, "Baa:\n  <caret>", "someFile.yml", "Nested");
  }

  public void testSiblingMappingIsolation() throws Exception {
    @Language("JSON") String schema = """
      {
        "properties": {
          "ObjectA": { "properties": { "Identifier": {} } },
          "ObjectB": { "properties": { "Identifier": {} } }
        }
      }""";
    testBySchema(schema, "ObjectA:\n  Identifier: \"A\"\nObjectB:\n  <caret>", "someFile.yml", "Identifier");
  }

  public void testEmptyMappingAtEndOfFile() throws Exception {
    @Language("JSON") String schema = "{\"properties\": {\"Identifier\": {}, \"Baa\": {\"properties\": {\"Identifier\": {}}}}}";
    testBySchema(schema, "Identifier: \"123\"\nBaa:\n  <caret>", "someFile.yml", "Identifier");
  }
}
