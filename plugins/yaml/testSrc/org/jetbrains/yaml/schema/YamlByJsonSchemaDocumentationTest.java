// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.schema;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.jetbrains.jsonSchema.JsonBySchemaDocumentationBaseTest;

public class YamlByJsonSchemaDocumentationTest extends JsonBySchemaDocumentationBaseTest {
  @Override
  public String getTestDataPath() {
    return PathManagerEx.getCommunityHomePath() + "/plugins/yaml/testSrc/org/jetbrains/yaml/schema/data";
  }

  @Override
  protected String getBasePath() {
    return ""; // unused
  }

  public void testDoc() throws Exception {
    doTest(true, "yml");
  }
}
