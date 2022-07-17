// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import org.jetbrains.annotations.NotNull;

public class GenerateDTDTest extends LightPlatformCodeInsightTestCase {
  @Override
  protected @NotNull String getTestDataPath() {
    return XmlTestUtil.getXmlTestDataPath();
  }

  public void test() {
    String basePath = "/codeInsight/generateDTD/";

    configureByFile(basePath + "before1.xml");
    performAction();
    checkResultByFile(basePath + "after1.xml");

    configureByFile(basePath + "before2.xml");
    performAction();
    checkResultByFile(basePath + "after2.xml");
  }

  private void performAction() {
    ActionManager actionManager = ActionManager.getInstance();
    AnAction action = actionManager.getAction("GenerateDTD");

    action.actionPerformed(AnActionEvent.createFromAnAction(action, null, "", DataManager.getInstance().getDataContext()));
  }
}
