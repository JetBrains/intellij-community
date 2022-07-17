// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import org.jetbrains.annotations.NotNull;

public class XmlSplitLineActionTest extends LightPlatformCodeInsightTestCase {
  @Override
  protected @NotNull String getTestDataPath() {
    return XmlTestUtil.getXmlTestDataPath();
  }

  public void testAtTheBeginOfLine() {
    String path = "/codeInsight/splitLineAction/";

    configureByFile(path + "SCR506_before.html");
    performAction();
    checkResultByFile(path + "SCR506_after.html");
  }

  private void performAction() {
    EditorActionManager actionManager = EditorActionManager.getInstance();
    EditorActionHandler actionHandler = actionManager.getActionHandler(IdeActions.ACTION_EDITOR_SPLIT);

    actionHandler.execute(getEditor(), null, DataManager.getInstance().getDataContext());
  }
}
