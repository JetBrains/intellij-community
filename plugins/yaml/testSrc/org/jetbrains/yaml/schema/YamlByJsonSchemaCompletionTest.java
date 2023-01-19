// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.schema;

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
    return PlatformTestUtil.getCommunityPath() + "/plugins/yaml/testSrc/org/jetbrains/yaml/schema/data/completion";
  }

  public void testTopLevel() throws Exception {
    testBySchema("{\"properties\": {\"prima\": {}, \"proto\": {}, \"primus\": {}}}", "proto: 5\n<caret>", "yml",
                 "prima", "primus");
  }

  public void testNested() throws Exception {
    testBySchema("{\"properties\": {\"prima\": {\"properties\": {\"proto\": {}, \"primus\": {}}}}}",
       "prima:\n  <caret>", "yml",
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
                   }""", "colorMap:\n  - <caret>", "yml", "hue", "saturation", "value");
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
                   }""", "colorMap:\n  - <caret>", "yml", "blue", "red", "white");
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
                       item5: 5""", "yml", "item2", "item4", "item6");
  }

  public void testPropInArray() throws Exception {
    @Language("JSON") String schemaText = FileUtil.loadFile(new File(getTestDataPath() + "/ifThenElseSchema.json"));
    testBySchema(schemaText, """
      provider:
        - select: A
          <caret>
        - select: B""", "yml", "var_a_1", "var_a_2");
    testBySchema(schemaText, """
      provider:
        - select: A
        - select: B
          <caret>""", "yml", "var_b_1", "var_b_2", "var_b_3");
  }
}
