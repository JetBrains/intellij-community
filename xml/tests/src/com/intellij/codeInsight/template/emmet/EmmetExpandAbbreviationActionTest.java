// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet;

import com.intellij.application.options.emmet.EmmetOptions;
import com.intellij.codeInsight.template.impl.TemplateSettings;
import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.ui.UIUtil;

public class EmmetExpandAbbreviationActionTest extends BasePlatformTestCase {
  private int myExpandShortcut;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myExpandShortcut = EmmetOptions.getInstance().getEmmetExpandShortcut();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      EmmetOptions.getInstance().setEmmetExpandShortcut(myExpandShortcut);
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }
  
  public void testExpandWithCustomShortcut() {
    EmmetOptions.getInstance().setEmmetExpandShortcut(TemplateSettings.CUSTOM_CHAR);
    myFixture.configureByText(HtmlFileType.INSTANCE, "div.class*2<caret>");
    myFixture.performEditorAction(IdeActions.ACTION_EXPAND_LIVE_TEMPLATE_CUSTOM);

    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    UIUtil.dispatchAllInvocationEvents();

    myFixture.checkResult("<div class=\"class\"></div>\n" +
                          "<div class=\"class\"></div>");
  }
  
  public void testDoNotExpandWithCustomShortcut() {
    myFixture.configureByText(HtmlFileType.INSTANCE, "div.class*2<caret>");
    myFixture.performEditorAction(IdeActions.ACTION_EXPAND_LIVE_TEMPLATE_CUSTOM);
    myFixture.checkResult("div.class*2<caret>");
  }
}
