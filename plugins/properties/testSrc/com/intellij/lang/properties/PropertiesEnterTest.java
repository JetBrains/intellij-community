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

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author cdr
 */
public class PropertiesEnterTest extends LightPlatformCodeInsightTestCase {
  @NonNls private static final String BASE_PATH = "/propertiesFile/enter/";

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("properties") + "/testData";
  }

  private void typeEnter() {
    type('\n');
  }

  public void testEndLine() { doTest(); }
  public void testComment() { doTest(); }
  public void testKey() { doTest(); }
  public void testValue() { doTest(); }
  public void testBackslash() { doTest(); }
  public void testBeforeComment() { doTest(); }

  public void testPerformance() {
    String line = "some.relatively.long.property.name=And here's some property value for that really unique key, nice to have\n";
    String text = StringUtil.repeat(line, 20000) + "<caret>\n" + StringUtil.repeat(line, 10000);
    configureFromFileText("performance.properties", text);
    PlatformTestUtil.startPerformanceTest("Property files editing", 2500, () -> {
      type("aaaa=bbb");
      PsiDocumentManager.getInstance(ourProject).commitAllDocuments();
    }).assertTiming();
  }

  private void doTest() {
    configureByFile(BASE_PATH + getTestName(false)+".properties");
    typeEnter();
    checkResultByFile(BASE_PATH + getTestName(false)+"_after.properties");
  }
}
