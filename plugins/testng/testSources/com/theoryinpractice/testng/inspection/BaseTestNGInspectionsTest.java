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

/*
 * User: anna
 * Date: 31-May-2007
 */
package com.theoryinpractice.testng.inspection;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.util.Comparing;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.*;
import com.intellij.util.PathUtil;
import com.intellij.util.ui.UIUtil;
import junit.framework.TestCase;
import org.jetbrains.annotations.NonNls;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import java.util.List;

public abstract class BaseTestNGInspectionsTest {
  protected CodeInsightTestFixture myFixture;
  @NonNls private static final String BEFORE = "before";
  @NonNls private static final String AFTER = "after";

  private LanguageLevel myLanguageLevel;
  private LocalInspectionTool myEnabledTool;

  @BeforeMethod
  public void setUp() throws Exception {
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        try {
          final IdeaTestFixtureFactory fixtureFactory = IdeaTestFixtureFactory.getFixtureFactory();
          final TestFixtureBuilder<IdeaProjectTestFixture> testFixtureBuilder = fixtureFactory.createFixtureBuilder();
          myFixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(testFixtureBuilder.getFixture());
          final String dataPath = PluginPathManager.getPluginHomePath("testng") + "/testData";
          myFixture.setTestDataPath(dataPath);
          final JavaModuleFixtureBuilder builder = testFixtureBuilder.addModule(JavaModuleFixtureBuilder.class);

          builder.addContentRoot(myFixture.getTempDirPath()).addSourceRoot("");
          //    builder.addContentRoot(dataPath);
          builder.setMockJdkLevel(JavaModuleFixtureBuilder.MockJdkLevel.jdk15);
          builder.addLibrary("junit", PathUtil.getJarPathForClass(TestCase.class));
          builder.addLibrary("testng", PathUtil.getJarPathForClass(AfterMethod.class));
          myEnabledTool = getEnabledTool();
          myFixture.enableInspections(myEnabledTool);
          myFixture.setUp();
          final JavaPsiFacade facade = JavaPsiFacade.getInstance(myFixture.getProject());
          myLanguageLevel = LanguageLevelProjectExtension.getInstance(facade.getProject()).getLanguageLevel();
          LanguageLevelProjectExtension.getInstance(facade.getProject()).setLanguageLevel(LanguageLevel.JDK_1_5);
        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    });
  }


  @AfterMethod
  public void tearDown() throws Exception {
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        try {
          LanguageLevelProjectExtension.getInstance(myFixture.getProject()).setLanguageLevel(myLanguageLevel);
          myFixture.tearDown();
          myFixture = null;
          myEnabledTool = null;
        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    });
  }

  protected void doTest(final String testName) throws Throwable {
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        try {
          final String resultActionName = getActionName();
          IntentionAction resultAction = null;
          final List<IntentionAction> actions = myFixture.getAvailableIntentions(getSourceRoot() + "/" + BEFORE + testName + ".java");
          for (IntentionAction action : actions) {
            if (Comparing.strEqual(action.getText(), resultActionName)) {
              resultAction = action;
              break;
            }
          }
          Assert.assertNotNull(resultAction);
          myFixture.launchAction(resultAction);
          myFixture.checkResultByFile(getSourceRoot() + "/" + AFTER + testName + ".java");
        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    });
  }

  protected abstract String getSourceRoot();
  protected abstract LocalInspectionTool getEnabledTool();

  protected String getActionName() {
    return myEnabledTool.getDisplayName();
  }
}
