/*
 * User: anna
 * Date: 31-May-2007
 */
package com.theoryinpractice.testng.inspection;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.util.PathUtil;
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
    final IdeaTestFixtureFactory fixtureFactory = IdeaTestFixtureFactory.getFixtureFactory();
    final TestFixtureBuilder<IdeaProjectTestFixture> testFixtureBuilder = fixtureFactory.createFixtureBuilder();
    myFixture = fixtureFactory.createCodeInsightFixture(testFixtureBuilder.getFixture());
    myFixture.setTestDataPath(PathManager.getHomePath() + "/plugins/testng/testData");
    final JavaModuleFixtureBuilder builder =
      (JavaModuleFixtureBuilder)testFixtureBuilder.addModule(JavaModuleFixtureBuilder.class)
        .addContentRoot(myFixture.getTempDirPath()).addSourceRoot(getSourceRoot());
    builder.setMockJdkLevel(JavaModuleFixtureBuilder.MockJdkLevel.jdk15);
    builder.addLibrary("junit", PathUtil.getJarPathForClass(TestCase.class));
    builder.addLibrary("testng", PathUtil.getJarPathForClass(AfterMethod.class));
    myEnabledTool = getEnabledTool();
    myFixture.enableInspections(myEnabledTool);
    myFixture.setUp();
    final PsiManager psiManager = PsiManager.getInstance(myFixture.getProject());
    myLanguageLevel = psiManager.getEffectiveLanguageLevel();
    psiManager.setEffectiveLanguageLevel(LanguageLevel.JDK_1_5);
  }


  @AfterMethod
  public void tearDown() throws Exception {
    PsiManager.getInstance(myFixture.getProject()).setEffectiveLanguageLevel(myLanguageLevel);
    myFixture.tearDown();
    myFixture = null;
    myEnabledTool = null;
  }

  protected void doTest(String testName) throws Throwable {
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
    myFixture.checkResultByFile(getSourceRoot() + "/"+ AFTER + testName +".java");
  }

  protected abstract String getSourceRoot();
  protected abstract LocalInspectionTool getEnabledTool();

  protected String getActionName() {
    return myEnabledTool.getDisplayName();
  }
}