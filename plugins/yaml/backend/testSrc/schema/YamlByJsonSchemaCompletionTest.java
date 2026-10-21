// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.schema;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static com.jetbrains.jsonSchema.impl.JsonSchemaCompletionTestUtil.testBySchema;

@TestApplication
public class YamlByJsonSchemaCompletionTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);

  private CodeInsightTestFixture myFixture;

  @BeforeEach
  void setUp() {
    myFixture = codeInsightFixture.get();
  }

  private static @NotNull String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath() + "/plugins/yaml/backend/testData/org/jetbrains/yaml/schema/data/completion";
  }

  @Test
  public void testTopLevel() throws Exception {
    testBySchema(myFixture, "{\"properties\": {\"prima\": {}, \"proto\": {}, \"primus\": {}}}", "proto: 5\n<caret>", "someFile.yml",
                 "prima", "primus");
  }

  @Test
  public void testNested() throws Exception {
    testBySchema(myFixture, "{\"properties\": {\"prima\": {\"properties\": {\"proto\": {}, \"primus\": {}}}}}",
       "prima:\n  <caret>", "someFile.yml",
                 "primus", "proto");
  }

  @Test
  public void testNestedInArray() throws Exception {
    testBySchema(myFixture, """
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

  @Test
  public void testEnumInArray() throws Exception {
    testBySchema(myFixture, """
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

  @Test
  public void testBeforeProps() throws Exception {
    testBySchema(myFixture, """
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

  @Test
  public void testPropInArray() throws Exception {
    @Language("JSON") String schemaText = FileUtil.loadFile(new File(getTestDataPath() + "/ifThenElseSchema.json"));
    testBySchema(myFixture, schemaText, """
      provider:
        - select: A
          <caret>
        - select: B""",
     "someFile.yml",
     LookupElement::getLookupString,
     CompletionType.SMART,
     "var_a_1", "var_a_2");
    testBySchema(myFixture, schemaText, """
      provider:
        - select: A
        - select: B
          <caret>""",
     "someFile.yml",
     LookupElement::getLookupString,
     CompletionType.SMART,
     "var_b_1", "var_b_2", "var_b_3");
  }

  @Test
  public void testNestedPropertyNotSuppressedByOuter() throws Exception {
    @Language("JSON") String schema = "{\"properties\": {\"Identifier\": {\"type\": \"string\"}, \"Baa\": {\"properties\": {\"Identifier\": {\"type\": \"string\"}}}}}";
    testBySchema(myFixture, schema, "Identifier: \"123\"\nBaa:\n  <caret>", "someFile.yml", "Identifier");
  }

  @Test
  public void testNestedPropertySuppressedBySameLevel() throws Exception {
    @Language("JSON") String schema = "{\"properties\": {\"Identifier\": {\"type\": \"string\"}, \"Baa\": {\"properties\": {\"Identifier\": {\"type\": \"string\"}}}}}";
    testBySchema(myFixture, schema, "Identifier: \"123\"\nBaa:\n  Identifier: \"already here\"\n  <caret>", "someFile.yml");
  }

  @Test
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
    testBySchema(myFixture, schema, "Level1:\n  Level2:\n    Level3:\n      Level1: \"value\"\n      <caret>", "someFile.yml", "Other");
  }

  @Test
  public void testTransitionFromKeyToNewObject() throws Exception {
    @Language("JSON") String schema = "{\"properties\": {\"Identifier\": {}, \"Baa\": {\"properties\": {\"Nested\": {}}}}}";
    // Caret not yet indented - should suggest top-level properties
    testBySchema(myFixture, schema, "Baa:\n<caret>", "someFile.yml", "Identifier");
    // Caret indented - should suggest nested properties
    testBySchema(myFixture, schema, "Baa:\n  <caret>", "someFile.yml", "Nested");
  }

  @Test
  public void testSiblingMappingIsolation() throws Exception {
    @Language("JSON") String schema = """
      {
        "properties": {
          "ObjectA": { "properties": { "Identifier": {} } },
          "ObjectB": { "properties": { "Identifier": {} } }
        }
      }""";
    testBySchema(myFixture, schema, "ObjectA:\n  Identifier: \"A\"\nObjectB:\n  <caret>", "someFile.yml", "Identifier");
  }

  @Test
  public void testEmptyMappingAtEndOfFile() throws Exception {
    @Language("JSON") String schema = "{\"properties\": {\"Identifier\": {}, \"Baa\": {\"properties\": {\"Identifier\": {}}}}}";
    testBySchema(myFixture, schema, "Identifier: \"123\"\nBaa:\n  <caret>", "someFile.yml", "Identifier");
  }

  @Test
  public void testNoCompletionInsideComment() throws Exception {
    // IJPL-249582: typing inside a YAML comment must not trigger JSON-schema key completion.
    @Language("JSON") String schema = "{\"properties\": {\"author\": {}, \"branding\": {}, \"name\": {}}}";
    // Full-line comment, with and without a leading space after '#'
    testBySchema(myFixture, schema, "name: CI\n# <caret>", "someFile.yml");
    testBySchema(myFixture, schema, "name: CI\n#<caret>", "someFile.yml");
    // Trailing comment on a key-value line
    testBySchema(myFixture, schema, "name: CI # <caret>", "someFile.yml");
  }
}
