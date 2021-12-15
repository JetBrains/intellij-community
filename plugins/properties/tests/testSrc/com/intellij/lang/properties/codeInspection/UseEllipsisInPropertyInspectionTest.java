// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties.codeInspection;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixture4TestCase;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

public class UseEllipsisInPropertyInspectionTest extends LightPlatformCodeInsightFixture4TestCase {

  @NotNull
  @Override
  protected String getBasePath() {
    return PluginPathManager.getPluginHomePathRelative("properties") + "/tests/testData/propertiesFile/ellipsis";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new UseEllipsisInPropertyInspection());
  }

  @Test
  public void testSimple() {
    EncodingProjectManager.getInstance(getProject()).setDefaultCharsetForPropertiesFiles(null, StandardCharsets.UTF_8);
    UIUtil.dispatchAllInvocationEvents();
    String filePath = "threeDots.properties";
    myFixture.configureByFile(filePath);

    List<IntentionAction> fixes = myFixture.getAllQuickFixes(filePath);
    IntentionAction fix = assertOneElement(fixes);
    myFixture.launchAction(fix);

    myFixture.checkResultByFile("threeDotsCorrect.properties");
  }
}
