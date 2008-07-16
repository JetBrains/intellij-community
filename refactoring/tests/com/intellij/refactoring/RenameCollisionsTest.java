package com.intellij.refactoring;

import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.testFramework.LightCodeInsightTestCase;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;

/**
 * @author sashache
 */
public class RenameCollisionsTest extends LightCodeInsightTestCase {
  private static final String BASE_PATH = "/refactoring/renameCollisions/";
  private LanguageLevel myPreviousLanguageLevel;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myPreviousLanguageLevel = LanguageLevelProjectExtension.getInstance(getJavaFacade().getProject()).getLanguageLevel();
    LanguageLevelProjectExtension.getInstance(getJavaFacade().getProject()).setLanguageLevel(LanguageLevel.JDK_1_5);
  }

  @Override
  protected void tearDown() throws Exception {
    LanguageLevelProjectExtension.getInstance(getJavaFacade().getProject()).setLanguageLevel(myPreviousLanguageLevel);
    super.tearDown();
  }

  public void testRenameClassInnerToLocal() throws Exception {
    doTest("LocalClass");
  }

  public void testRenameClassLocalToAlien() throws Exception {
    doTest("String");
  }

  //Fails due to IDEADEV-25194.
  //public void testRenameClassLocalToAlienNoImports() throws Exception {
  //  doTest("String");
  //}

  public void testRenameClassLocalToInner() throws Exception {
    doTest("StaticInnerClass");
  }

  public void testRenameClassThisFqnToAlien() throws Exception {
    doTest("String");
  }

  public void testRenameClassThisToAlien() throws Exception {
    doTest("String");
  }

  public void testRenameMethodIndiInstancesInnerToOuter() throws Exception {
    doTest("method");
  }

  public void testRenameMethodIndiInstancesOuterToInner() throws Exception {
    doTest("siMethod");
  }

  public void testRenameMethodInnerInstanceToOuterInstance() throws Exception {
    doTest("method");
  }

  public void testRenameMethodInnerStaticToOuterStatic() throws Exception {
    doTest("staticMethod");
  }

  public void testRenameMethodOuterInstanceToInnerInstance() throws Exception {
    doTest("innerMethod");
  }

  public void testRenameMethodOuterStaticToInnerStatic() throws Exception {
    doTest("siStaticMethod");
  }

  public void testRenameMethodStaticToAlien() throws Exception {
    doTest("valueOf");
  }

  public void testRenameVarConstToAlien() throws Exception {
    doTest("CASE_INSENSITIVE_ORDER");
  }

  public void testRenameVarConstToParam() throws Exception {
    doTest("param3");
  }

  public void testRenameVarFieldToLocal() throws Exception {
    doTest("localVar3");
  }

  public void testRenameVarInnerConstToOuterConst() throws Exception {
    doTest("STATIC_FIELD");
  }

  public void testRenameVarInnerFieldToOuterField() throws Exception {
    doTest("myField");
  }

  public void testRenameVarLocalToAlien() throws Exception {
    doTest("BOTTOM");
  }

  public void testRenameVarLocalToConst() throws Exception {
    doTest("INNER_STATIC_FIELD");
  }

  public void testRenameVarLocalToOuterField() throws Exception {
    doTest("myField");
  }

  public void testRenameVarOuterConstToInnerConst() throws Exception {
    doTest("SI_STATIC_FIELD");
  }

  public void testRenameVarOuterConstToLocal() throws Exception {
    doTest("localVar3");
  }

  public void testRenameVarOuterConstToParam() throws Exception {
    doTest("param2");
  }

  public void testRenameVarOuterFieldToLocal() throws Exception {
    doTest("localVar3");
  }

  public void testRenameVarOuterFieldToParam() throws Exception {
    doTest("param3");
  }

  public void testRenameVarParamToAlien() throws Exception {
    doTest("BOTTOM");
  }

  public void testRenameVarParamToField() throws Exception {
    doTest("myInnerField");
  }

  public void testRenameVarParamToOuterConst() throws Exception {
    doTest("STATIC_FIELD");
  }

  private void doTest(final String newName) throws Exception {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    PsiElement element = TargetElementUtilBase
        .findTargetElement(myEditor, TargetElementUtilBase.ELEMENT_NAME_ACCEPTED | TargetElementUtilBase.REFERENCED_ELEMENT_ACCEPTED);
    assertNotNull(element);
    new RenameProcessor(getProject(), element, newName, true, true).run();
    checkResultByFile(BASE_PATH + getTestName(false) + ".java.after");
  }

  @Override
  protected Sdk getProjectJDK() {
    return JavaSdkImpl.getMockJdk15("java 1.5");
  }
}
