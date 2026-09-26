// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.XmlTestUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.FetchExtResourceAction;
import com.intellij.codeInsight.intention.IntentionAction;
import org.jetbrains.annotations.NotNull;

public class FetchExternalResourcesFixTest extends LightQuickFixParameterizedTestCase {
  @Override
  protected String getBasePath() {
    return "/quickFix/fetchExternalResources";
  }

  // just check for action availability
  @Override
  protected void doAction(@NotNull ActionHint actionHint, @NotNull String testFullPath, @NotNull String testName) {
    IntentionAction action = findActionAndCheck(actionHint, testFullPath);
    if (action != null && testName.equals("5.xml")) {
      final String uri = FetchExtResourceAction.findUri(getFile(), getEditor().getCaretModel().getOffset());
      final String url = FetchExtResourceAction.findUrl(getFile(), getEditor().getCaretModel().getOffset(), uri);
      assertEquals("http://www.springframework.org/schema/aop/spring-aop.xsd",url);
    }
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return XmlTestUtil.getXmlTestDataPath();
  }
}
