// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class XmlBackspaceTest extends LightPlatformCodeInsightTestCase {
  @Override
  protected @NotNull String getTestDataPath() {
    return XmlTestUtil.getXmlTestDataPath();
  }

  public void testQuoteInXml() { doTest(); }
  public void testQuoteInXml2() { doTest(); }

  private void doTest() {
    @NonNls String path = "/codeInsight/backspace/";

    configureByFile(path + getTestName(false) + ".xml");
    backspace();
    checkResultByFile(path + getTestName(false) + "_after.xml");
  }
}
