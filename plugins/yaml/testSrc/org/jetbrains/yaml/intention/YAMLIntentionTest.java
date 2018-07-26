// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.intention;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.inspections.YAMLDuplicatedKeysInspection;
import org.jetbrains.yaml.inspections.YAMLUnusedAnchorInspection;

import java.util.List;
import java.util.Optional;

public class YAMLIntentionTest extends LightPlatformCodeInsightFixtureTestCase {
  @Override
  protected String getTestDataPath() {
    return PathManagerEx.getCommunityHomePath() + "/plugins/yaml/testSrc/org/jetbrains/yaml/intention/data/";
  }

  public void testDeleteDuplicatedKey() {
    myFixture.enableInspections(YAMLDuplicatedKeysInspection.class);
    doTest("Remove key");
  }

  public void testDeleteAnchor1() {
    doDeleteAnchorTest();
  }

  public void testDeleteAnchor2() {
    doDeleteAnchorTest();
  }

  public void testDeleteAnchor3() {
    doDeleteAnchorTest();
  }

  private void doDeleteAnchorTest() {
    myFixture.enableInspections(YAMLUnusedAnchorInspection.class);
    doTest("Remove anchor");
  }

  private void doTest(@NotNull String intentionName) {
    String testName = getTestName(true);
    myFixture.configureByFile(testName + ".yml");
    IntentionAction quickFix = findIntention(intentionName);
    myFixture.launchAction(quickFix);
    myFixture.checkResultByFile(testName + ".txt");
  }

  @NotNull
  private IntentionAction findIntention(@NotNull String name) {
    List<IntentionAction> intentions = myFixture.getAvailableIntentions();
    Optional<IntentionAction> intention =
      intentions.stream().filter(it -> it.getText().contains(name)).findFirst();
    assert intention.isPresent();
    return intention.get();
  }
}
