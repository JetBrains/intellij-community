/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.lang.properties;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PropertiesJoinLinesTest extends LightPlatformCodeInsightTestCase {

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("properties") + "/testData/propertiesFile/joinLines/";
  }

  public void testProperties() throws Exception { doTest(); }
  public void testPropertiesBackSlash() throws Exception { doTest(); }
  public void testPropertiesBackSlashSlash() throws Exception { doTest(); }

  private void doTest() throws Exception {
    configureByFile(getTestName(false) + ".properties");
    performAction();
    checkResultByFile(getTestName(false) + "_after.properties");
  }

  private void performAction() {
    EditorActionManager actionManager = EditorActionManager.getInstance();
    EditorActionHandler actionHandler = actionManager.getActionHandler(IdeActions.ACTION_EDITOR_JOIN_LINES);

    actionHandler.execute(getEditor(), DataManager.getInstance().getDataContext());
  }
}
