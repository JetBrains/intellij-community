/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.annotations.NotNull;

public class PropertiesCommenterTest extends LightPlatformCodeInsightTestCase {
  public void testProp1() { doTest(); }
  public void testUncomment() { doTest(); }
  public void testExclamationMark() { doTest(); }
  public void testEmptyLineDuringUncomment() { doTest(); }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("properties") + "/tests/testData";
  }

  private void doTest() {
    configureByFile("/propertiesFile/comment/before" + getTestName(false) + ".properties");
    PlatformTestUtil.invokeNamedAction(IdeActions.ACTION_COMMENT_LINE);
    checkResultByFile("/propertiesFile/comment/after" + getTestName(false) + ".properties");
  }
}
