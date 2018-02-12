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
package com.theoryinpractice.testng.inspection;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.openapi.util.Comparing;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.PathUtil;
import junit.framework.TestCase;
import org.jetbrains.annotations.NonNls;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;

/**
 * @author Dmitry Batkovich
 */
public abstract class BaseTestNGInspectionsTest extends JavaCodeInsightFixtureTestCase {
  @NonNls private static final String BEFORE = "before";
  @NonNls private static final String AFTER = "after";

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) {
    moduleBuilder.setMockJdkLevel(JavaModuleFixtureBuilder.MockJdkLevel.jdk15);
    moduleBuilder.addLibrary("junit", PathUtil.getJarPathForClass(TestCase.class));
    moduleBuilder.addLibrary("testng", PathUtil.getJarPathForClass(AfterMethod.class));
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(getEnabledTool());
  }

  public void doTest() {
    IntentionAction resultAction = null;
    final String testName = getTestName(false);
    final String resultActionName = getActionName();
    for (IntentionAction action : myFixture.getAvailableIntentions(BEFORE + testName + ".java")) {
      if (Comparing.strEqual(action.getText(), resultActionName)) {
        resultAction = action;
        break;
      }
    }
    Assert.assertNotNull(resultAction, "action isn't found");
    myFixture.launchAction(resultAction);
    myFixture.checkResultByFile(AFTER + testName + ".java");
  }


  protected abstract LocalInspectionTool getEnabledTool();

  protected String getActionName() {
    return getEnabledTool().getDisplayName();
  };
}
