/*
 * User: anna
 * Date: 31-May-2007
 */
package com.theoryinpractice.testng.intentions;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PathUtil;
import com.theoryinpractice.testng.intention.ConvertAnnotationIntention;
import com.theoryinpractice.testng.intention.ConvertJUnitIntention;
import com.theoryinpractice.testng.intention.ConvertOldAnnotationIntention;
import com.theoryinpractice.testng.intention.ConvertJavadocIntention;
import junit.framework.TestCase;
import org.jetbrains.annotations.NonNls;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class ConvertIntentionsTest {
  private CodeInsightTestFixture myFixture;
  @NonNls private static final String BEFORE = "before";

  private LanguageLevel myLanguageLevel; 

  @BeforeMethod
  public void setUp() throws Exception {
    final IdeaTestFixtureFactory fixtureFactory = IdeaTestFixtureFactory.getFixtureFactory();
    final TestFixtureBuilder<IdeaProjectTestFixture> testFixtureBuilder = fixtureFactory.createFixtureBuilder();
    myFixture = fixtureFactory.createCodeInsightFixture(testFixtureBuilder.getFixture());
    myFixture.setTestDataPath(PathManager.getHomePath() + "/plugins/testng/testData");
    final JavaModuleFixtureBuilder builder =
      (JavaModuleFixtureBuilder)testFixtureBuilder.addModule(JavaModuleFixtureBuilder.class)
        .addContentRoot(myFixture.getTempDirPath()).addSourceRoot("convert");
    builder.addLibrary("junit", PathUtil.getJarPathForClass(TestCase.class));
    builder.addLibrary("testng", PathUtil.getJarPathForClass(AfterMethod.class));
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
  }

  @Test
  public void testSimpleClassConversion() throws Throwable {
    doConvert("Class", new ConvertJUnitIntention());
  }

  @Test
  public void testAssertStatements() throws Throwable {
    doConvert("Fail", new ConvertJUnitIntention());
  }

  @Test
  public void testConvertConfiguration() throws Throwable {
    doConvert("Configuration", new ConvertOldAnnotationIntention());
  }

  @Test
  public void testConvertToJavadoc() throws Throwable {
    doConvert("Javadoc", new ConvertAnnotationIntention());
  }

  public void testConvertJavadoc2Annotation() throws Throwable {
    doConvert("Javadoc2Annotation", new ConvertJavadocIntention());
  }

  private void doConvert(final String testName, final IntentionAction action) throws Throwable {
    myFixture.configureByFile("convert/" + BEFORE + testName + ".java");
    CommandProcessor.getInstance().executeCommand(myFixture.getProject(), new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            try {
              action.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile());
            }
            catch (IncorrectOperationException e) {
              e.printStackTrace();
              Assert.fail();
            }
          }
        });
      }
    }, "", "");
    myFixture.checkResultByFile("convert/after" + testName + ".java");
  }

}