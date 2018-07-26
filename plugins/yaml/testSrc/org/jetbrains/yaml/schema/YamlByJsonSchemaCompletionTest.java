// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.schema;

import com.jetbrains.jsonSchema.impl.JsonBySchemaCompletionBaseTest;

public class YamlByJsonSchemaCompletionTest extends JsonBySchemaCompletionBaseTest {
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
}
