/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.daemon.impl.quickfix.FetchExtResourceAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class FetchExternalResourcesFixTest extends LightQuickFixParameterizedTestCase {
  public void test() { doAllTests(); }

  @Override
  protected String getBasePath() {
    return "/quickFix/fetchExternalResources";
  }

  // just check for action availability
  @Override
  protected void doAction(@NotNull ActionHint actionHint, String testFullPath, String testName) {
    IntentionAction action = findActionAndCheck(actionHint, testFullPath);
    if (action != null && testName.equals("5.xml")) {
      final String uri = FetchExtResourceAction.findUri(myFile, myEditor.getCaretModel().getOffset());
      final String url = FetchExtResourceAction.findUrl(myFile, myEditor.getCaretModel().getOffset(),uri);
      assertEquals("http://www.springframework.org/schema/aop/spring-aop.xsd",url);
    }
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') + "/xml/tests/testData/";
  }
}
