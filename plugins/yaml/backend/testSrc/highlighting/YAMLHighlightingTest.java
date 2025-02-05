// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.highlighting;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.yaml.inspections.YAMLDuplicatedKeysInspection;
import org.jetbrains.yaml.inspections.YAMLRecursiveAliasInspection;
import org.jetbrains.yaml.inspections.YAMLUnresolvedAliasInspection;
import org.jetbrains.yaml.inspections.YAMLUnusedAnchorInspection;

public class YAMLHighlightingTest extends BasePlatformTestCase {
  @Override
  protected String getTestDataPath() {
    return PathManagerEx.getCommunityHomePath() + "/plugins/yaml/backend/testData/org/jetbrains/yaml/highlighting/data/";
  }

  public void testBlockScalarHeaderError() {
    doTest();
  }

  public void testMultipleAnchorsError() {
    doTest();
  }

  public void testMultipleTagsError() {
    doTest();
  }

  public void testUnresolvedAlias() {
    myFixture.enableInspections(YAMLUnresolvedAliasInspection.class);
    doTest();
  }

  public void testRecursiveAlias() {
    myFixture.enableInspections(YAMLRecursiveAliasInspection.class);
    doTest();
  }

  public void testDuplicatedKeys() {
    myFixture.enableInspections(YAMLDuplicatedKeysInspection.class);
    doTest();
  }

  public void testUnusedAnchors() {
    myFixture.enableInspections(YAMLUnusedAnchorInspection.class);
    doTest();
  }

  public void testSameLineComposedValue() {
    doTest();
  }

  public void testInvalidBlockChildren() {
    doTest();
  }

  public void testInvalidIndent() {
    doTest();
  }

  public void testWebUrls() {
    doTest(true);
  }

  private void doTest() {
    doTest(false);
  }

  private void doTest(boolean checkInfos) {
    myFixture.testHighlighting(true, checkInfos, false, getTestName(true) + ".yml");
  }
}
