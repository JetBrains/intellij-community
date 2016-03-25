package de.plushnikov.intellij.plugin;

import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.InspectionTestCase;
import de.plushnikov.intellij.plugin.inspection.LombokInspection;

/**
 * @author Plushnikov Michail
 */
public class InspectionTest extends InspectionTestCase {

  @Override
  protected String getTestDataPath() {
    return "testData/inspection";
  }

  @Override
  protected Sdk getTestProjectSdk() {
    Sdk sdk = JavaSdk.getInstance().createJdk("java 1.7", "/lib/mockJDK-1.7", false);
    LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_1_7);
    return sdk;
  }

  private void doTest() throws Exception {
    doTest(getTestName(true), new LombokInspection(), "java 1.7");
  }

  public void testIssue37() throws Exception {
    doTest();
  }

  public void testBuilderRightType() throws Exception {
    doTest();
  }

  public void testBuilderInvalidIdentifier() throws Exception {
    doTest();
  }

  public void testDelegateConcreteType() throws Exception {
    doTest();
  }

  public void testDelegateOnMethodWithParameter() throws Exception {
    doTest();
  }

  public void testDelegateOnStaticFieldOrMethod() throws Exception {
    doTest();
  }

  public void testDataEqualsAndHashCodeOverride() throws Exception {
    doTest();
  }
}
