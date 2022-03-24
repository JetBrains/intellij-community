// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.shellcheck;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.sh.settings.ShSettings;
import com.intellij.sh.shellcheck.intention.ShDisableInspectionIntention;
import com.intellij.sh.shellcheck.intention.ShSuppressInspectionIntention;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

public class ShShellcheckInspectionTest extends BasePlatformTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    InspectionProfileImpl.INIT_INSPECTIONS = true;
    ShShellcheckTestUtil.downloadShellcheck();
    assertTrue("Failed to download proper shellcheck executable",
               ShShellcheckUtil.isExecutionValidPath(ShSettings.getShellcheckPath()));
  }

  @Override
  protected void tearDown() throws Exception {
    InspectionProfileImpl.INIT_INSPECTIONS = false;
    super.tearDown();
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("sh") + "/testData/shellcheck/";
  }

  public void testSuppressInspection() {
    doTest(ShSuppressInspectionIntention.class);
  }

  public void testSuppressSameInspection() {
    doTest(ShSuppressInspectionIntention.class);
  }

  public void testQuickFix() {
    doTest(ShQuickFixIntention.class);
  }

  public void testDisableInspection() {
    configFile();
    ShShellcheckInspection inspectionBefore = ShShellcheckInspection.findShShellcheckInspection(myFixture.getFile());
    assertEmpty(inspectionBefore.getDisabledInspections());
    applyIntentions(ShDisableInspectionIntention.class);
    checkResult();

    ShShellcheckInspection inspectionAfter = ShShellcheckInspection.findShShellcheckInspection(myFixture.getFile());
    assertNotEmpty(inspectionAfter.getDisabledInspections());
    assertSameElements(inspectionAfter.getDisabledInspections(), Arrays.asList("SC2034", "SC2046"));
  }

  private void doTest(Class<? extends IntentionAction> intentionType) {
    configFile();
    applyIntentions(intentionType);
    checkResult();
  }

  private void configFile() {
    myFixture.enableInspections(new ShShellcheckInspection());
    myFixture.configureByFile(getTestName(true) + ".sh");
  }

  private void checkResult() {
    myFixture.checkHighlighting(true, false, true);
    myFixture.checkResultByFile(getTestName(true) + ".after.sh");
  }

  private void applyIntentions(Class<? extends IntentionAction> intentionType) {
    CaretModel caretModel = myFixture.getEditor().getCaretModel();
    List<HighlightInfo> inspections = myFixture.doHighlighting(HighlightSeverity.WEAK_WARNING);
    while (!inspections.isEmpty()) {
      List<Pair<HighlightInfo.IntentionActionDescriptor, TextRange>> ranges = inspections.get(0).quickFixActionRanges;
      if (ranges != null) {
        ranges.stream()
          .filter(pair -> intentionType.isInstance(pair.getFirst().getAction()))
          .forEach(pair -> {
            caretModel.moveToOffset(pair.getSecond().getStartOffset());
            myFixture.launchAction(pair.getFirst().getAction());
          });
      }
      inspections = myFixture.doHighlighting(HighlightSeverity.WARNING);
    }
  }
}