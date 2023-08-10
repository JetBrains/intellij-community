// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.defaultAction;

import com.intellij.codeInsight.XmlTestUtil;
import org.jetbrains.annotations.NotNull;

public class XmlQuoteTest extends DefaultActionTestCase {
  @Override
  protected @NotNull String getTestDataPath() {
    return XmlTestUtil.getXmlTestDataPath();
  }

  public void testAttributeStart() { doTest('"'); }
  public void testAttributeEnd() { doTest('"'); }

  private void doTest(char c) {
    String path = "/codeInsight/defaultAction/quote/";
    configureByFile(path + getTestName(false) + ".xml");
    performAction(c);
    checkResultByFile(path + getTestName(false) + "_after.xml");
  }
}
