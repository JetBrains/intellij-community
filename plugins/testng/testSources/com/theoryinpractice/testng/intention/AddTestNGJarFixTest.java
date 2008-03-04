/*
 * User: anna
 * Date: 04-Mar-2008
 */
package com.theoryinpractice.testng.intention;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.util.Comparing;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
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
  public void setUp() throws Exception {
    final IdeaTestFixtureFactory fixtureFactory = IdeaTestFixtureFactory.getFixtureFactory();
    final TestFixtureBuilder<IdeaProjectTestFixture> testFixtureBuilder = fixtureFactory.createFixtureBuilder();
    myFixture = fixtureFactory.createCodeInsightFixture(testFixtureBuilder.getFixture());
    final String dataPath = PathManager.getHomePath() + "/svnPlugins/testng/testData";
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


  @AfterMethod
  public void tearDown() throws Exception {
    LanguageLevelProjectExtension.getInstance(myFixture.getProject()).setLanguageLevel(myLanguageLevel);
    myFixture.tearDown();
    myFixture = null;
  }

  @NonNls
  @DataProvider
  public Object[][] data() {
    return new String[][]{new String[]{"InsideReference"}, new String[]{"AfterReference"}};
  }

  @Test(dataProvider = "data")
  public void doTest(String testName) throws Throwable {
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


}