package de.plushnikov.intellij.plugin;

import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.InspectionTestCase;
import de.plushnikov.intellij.plugin.inspection.LombokInspection;

import java.io.File;

/**
 * @author Plushnikov Michail
 */
public class InspectionTest extends InspectionTestCase {

  private static final String LIB_MOCK_JDK = "lib/mockJDK-1.7";
  private static final String JDK_NAME = "java 1.7";

  @Override
  protected void setUp() throws Exception {
    VfsRootAccess.allowRootAccess(new File("./" + LIB_MOCK_JDK).getCanonicalPath());
    super.setUp();
  }

  @Override
  protected String getTestDataPath() {
    return "testData/inspection";
  }

  @Override
  protected Sdk getTestProjectSdk() {
    Sdk sdk = JavaSdk.getInstance().createJdk(JDK_NAME, LIB_MOCK_JDK, false);
    LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_1_7);
    return sdk;
  }

  private void doTest() throws Exception {
    doTest(getTestName(true), new LombokInspection(), JDK_NAME);
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

  public void testEqualsAndHashCodeCallSuper() throws Exception {
    doTest();
  }

  public void testEqualsAndHashCodeCallSuperConfigSkip() throws Exception {
    doTest();
  }

  public void testValInspection() throws Exception {
    doTest();
  }
}
