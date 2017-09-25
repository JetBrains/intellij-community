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

package com.theoryinpractice.testng.intention;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.util.Comparing;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.*;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.List;

@Test
public class AddTestNGJarFixTest {
  protected CodeInsightTestFixture myFixture;

  private LanguageLevel myLanguageLevel;

  @BeforeMethod
  public void setUp() {
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        try {
          final IdeaTestFixtureFactory fixtureFactory = IdeaTestFixtureFactory.getFixtureFactory();
          final TestFixtureBuilder<IdeaProjectTestFixture> testFixtureBuilder = fixtureFactory.createFixtureBuilder(getClass().getSimpleName());
          myFixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(testFixtureBuilder.getFixture());
          final String dataPath = PluginPathManager.getPluginHomePath("testng") + "/testData";
          myFixture.setTestDataPath(dataPath);
          final JavaModuleFixtureBuilder builder = testFixtureBuilder.addModule(JavaModuleFixtureBuilder.class);

          builder.addContentRoot(myFixture.getTempDirPath()).addSourceRoot("");
          //    builder.addContentRoot(dataPath);
          builder.setMockJdkLevel(JavaModuleFixtureBuilder.MockJdkLevel.jdk15);
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
  public void tearDown() {
    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
      try {
        LanguageLevelProjectExtension.getInstance(myFixture.getProject()).setLanguageLevel(myLanguageLevel);
        myFixture.tearDown();
        myFixture = null;
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    });
  }

  @NonNls
  @DataProvider
  public Object[][] data() {
    return new String[][]{new String[]{"InsideReference"}, new String[]{"AfterReference"}};
  }

  @Test(dataProvider = "data")
  public void doTest(final String testName) {
    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
      try {
        IntentionAction resultAction = null;
        final List<IntentionAction> actions = myFixture.getAvailableIntentions("intention/testNGJar" + "/" + testName + ".java");
        for (IntentionAction action : actions) {
          if (Comparing.strEqual(action.getText(), "Add testng.jar to classpath")) {
            resultAction = action;
            break;
          }
        }
        Assert.assertNotNull(resultAction);
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    });
  }


}
