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
    testBySchema("{\n" +
                 "  \"properties\": {\n" +
                 "    \"colorMap\": {\n" +
                 "      \"type\": \"array\",\n" +
                 "      \"items\": {\n" +
                 "        \"properties\": {\n" +
                 "          \"hue\": {\n" +
                 "            \"type\": \"string\"\n" +
                 "          },\n" +
                 "          \"saturation\": {\n" +
                 "            \"type\": \"string\"\n" +
                 "          },\n" +
                 "          \"value\": {\n" +
                 "            \"type\": \"string\"\n" +
                 "          }\n" +
                 "        }\n" +
                 "      }\n" +
                 "    }\n" +
                 "  }\n" +
                 "}", "colorMap:\n  - <caret>", "yml", "hue", "saturation", "value");
  }

  public void testEnumInArray() throws Exception {
    testBySchema("{\n" +
                 "  \"properties\": {\n" +
                 "    \"colorMap\": {\n" +
                 "      \"type\": \"array\",\n" +
                 "      \"items\": {\n" +
                 "        \"enum\": [\"white\", \"blue\", \"red\"]\n" +
                 "      }\n" +
                 "    }\n" +
                 "  }\n" +
                 "}", "colorMap:\n  - <caret>", "yml", "blue", "red", "white");
  }

  public void testBeforeProps() throws Exception {
    testBySchema("{\n" +
                 "  \"properties\": {\n" +
                 "    \"root\": {\n" +
                 "      \"properties\": {\n" +
                 "        \"item1\": { },\n" +
                 "        \"item2\": { },\n" +
                 "        \"item3\": { },\n" +
                 "        \"item4\": { },\n" +
                 "        \"item5\": { },\n" +
                 "        \"item6\": { }\n" +
                 "      }\n" +
                 "    }\n" +
                 "  }\n" +
                 "}", "root:\n" +
                      "    item1: 1\n" +
                      "    item3: 3\n" +
                      "    <caret>\n" +
                      "    item5: 5", "yml", "item2", "item4", "item6");
  }

  public void testPropInArray() throws Exception {
    @Language("JSON") String schemaText = FileUtil.loadFile(new File(getTestDataPath() + "/ifThenElseSchema.json"));
    testBySchema(schemaText, "provider:\n" +
                             "  - select: A\n" +
                             "    <caret>\n" +
                             "  - select: B", "yml", "var_a_1", "var_a_2");
    testBySchema(schemaText, "provider:\n" +
                             "  - select: A\n" +
                             "  - select: B\n" +
                             "    <caret>", "yml", "var_b_1", "var_b_2", "var_b_3");
  }
}
