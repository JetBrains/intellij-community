/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import com.intellij.testFramework.PlatformTestCase;
import org.jetbrains.annotations.NonNls;

/**
 * @author cdr
 */
public class PropertiesEnterTest extends LightPlatformCodeInsightTestCase {
  @NonNls private static final String BASE_PATH = "/propertiesFile/enter/";

  public PropertiesEnterTest() {
    PlatformTestCase.initPlatformLangPrefix();
  }

  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("properties") + "/testData";
  }

  private static void typeEnter() {
    EditorActionManager actionManager = EditorActionManager.getInstance();
    EditorActionHandler actionHandler = actionManager.getActionHandler(IdeActions.ACTION_EDITOR_ENTER);
    actionHandler.execute(getEditor(), DataManager.getInstance().getDataContext());
  }

  public void testEndLine() throws Exception { doTest(); }
  public void testComment() throws Exception { doTest(); }
  public void testKey() throws Exception { doTest(); }
  public void testValue() throws Exception { doTest(); }
  public void testBackslash() throws Exception { doTest(); }
  public void testBeforeComment() throws Exception { doTest(); }

  private void doTest() throws Exception {
    configureByFile(BASE_PATH + getTestName(false)+".properties");
    typeEnter();
    checkResultByFile(BASE_PATH + getTestName(false)+"_after.properties");
  }
}
