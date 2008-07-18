/*
 * User: anna
 * Date: 28-Feb-2008
 */
package com.intellij.refactoring;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.util.duplicates.MethodDuplicatesHandler;
import com.intellij.testFramework.LightCodeInsightTestCase;

public class FindMethodDuplicatesTest extends LightCodeInsightTestCase{
  private LanguageLevel myPreviousLanguageLevel;

  protected void setUp() throws Exception {
    super.setUp();
    myPreviousLanguageLevel = LanguageLevelProjectExtension.getInstance(getJavaFacade().getProject()).getLanguageLevel();
    LanguageLevelProjectExtension.getInstance(getJavaFacade().getProject()).setLanguageLevel(LanguageLevel.JDK_1_5);
  }

  protected void tearDown() throws Exception {
    LanguageLevelProjectExtension.getInstance(getJavaFacade().getProject()).setLanguageLevel(myPreviousLanguageLevel);
    super.tearDown();
  }

  protected Sdk getProjectJDK() {
    return JavaSdkImpl.getMockJdk15("java 1.5");
  }

  private void doTest() throws Exception {
    doTest(true);
  }

  private void doTest(final boolean shouldSucceed) throws Exception {
     final String filePath = "/refactoring/methodDuplicates/" + getTestName(false) + ".java";
    configureByFile(filePath);
    final PsiElement targetElement = TargetElementUtilBase.findTargetElement(getEditor(), TargetElementUtilBase.ELEMENT_NAME_ACCEPTED);
    assertTrue("<caret> is not on method name", targetElement instanceof PsiMethod);
    final PsiMethod psiMethod = (PsiMethod)targetElement;

    try {
      MethodDuplicatesHandler.invokeOnScope(getProject(), psiMethod, new AnalysisScope(getFile()));
    }
    catch (RuntimeException e) {
      if (shouldSucceed) {
        fail("duplicates were not found");
      }
      return;
    }
    if (shouldSucceed) {
      checkResultByFile(filePath + ".after");
    } else {
      fail("duplicates found");
    }
  }

  public void testIdentityComplete() throws Exception {
    doTest();
  }

  public void testIdentityComment() throws Exception {
    doTest();
  }

  public void testIdentityName() throws Exception {
    doTest();
  }

  public void testIdentityWhitespace() throws Exception {
    doTest();
  }

  public void testLocationQuantity() throws Exception {
    doTest();
  }

  public void testMappingExpression2Field() throws Exception {
    doTest(false);
  }

  public void testMappingExpression2LocalVar() throws Exception {
    doTest(false);
  }

  public void testMappingExpression2ParameterLiterals() throws Exception {
    doTest();
  }

  public void testMappingExpression2ParameterLValues() throws Exception {
    doTest();
  }

  public void testMappingExpression2ParameterMultiple() throws Exception {
    doTest();
  }

  public void testMappingExpression2This() throws Exception {
    doTest(false);
  }

  public void testMappingField2Field() throws Exception {
    doTest();
  }

  public void testMappingField2LocalVar() throws Exception {
    doTest(false);
  }

  public void testMappingField2Parameter() throws Exception {
    doTest();
  }

  public void testMappingField2This() throws Exception {
    doTest(false);
  }

  public void testMappingLocalVar2Expression() throws Exception {
    doTest(false);
  }

  public void testMappingLocalVar2Field() throws Exception {
    doTest(false);
  }

  public void testMappingLocalVar2LocalVar() throws Exception {
    doTest();
  }

  public void testMappingLocalVar2Parameter() throws Exception {
    doTest();
  }

  public void testMappingLocalVar2This() throws Exception {
    doTest(false);
  }

  public void testMappingMember2MemberDifferent() throws Exception {
    doTest();
  }

  public void testMappingParameter2Field() throws Exception {
    doTest(false);
  }

  public void testMappingParameter2LocalVar() throws Exception {
    doTest(false);
  }

  public void testMappingParameter2Parameter() throws Exception {
    doTest();
  }

  public void testMappingParameter2This() throws Exception {
    doTest(false);
  }

  public void testMappingThis2Field() throws Exception {
    doTest(false);
  }

  public void testMappingThis2LocalVar() throws Exception {
    doTest(false);
  }

  public void testMappingThis2Parameter() throws Exception {
    doTest();
  }

  public void testMappingThis2ThisDifferent() throws Exception {
    doTest();
  }

  public void testMappingThis2ThisQualified() throws Exception {
    doTest();
  }

  public void testPostFragmentUsage() throws Exception {
    doTest();
  }

  public void testReturnExpression() throws Exception {
    doTest();
  }

  public void testReturnField() throws Exception {
    doTest();
  }

  public void testReturnLocalVar() throws Exception {
    doTest();
  }

  public void testReturnParameter() throws Exception {
    doTest();
  }

  public void testReturnThis() throws Exception {
    doTest();
  }

  public void testTypesExtends() throws Exception {
    doTest();
  }

  public void testTypesExtendsReturn() throws Exception {
    doTest();
  }

  public void testTypesExtendsReturnDifferent() throws Exception {
    doTest();
  }

  public void testTypesGenericsConcrete2Concrete() throws Exception {
    doTest();
  }

  public void testTypesGenericsConcrete2ConcreteDifferent() throws Exception {
    doTest(false);
  }

  public void testTypesGenericsConcrete2Extends() throws Exception {
    doTest();
  }

  public void testTypesGenericsConcrete2ExtendsDifferent() throws Exception {
    doTest(false);
  }

  public void testTypesGenericsConcrete2Super() throws Exception {
    doTest();
  }

  public void testTypesGenericsConcrete2SuperDifferent() throws Exception {
    doTest(false);
  }

  public void testTypesGenericsConcrete2Raw() throws Exception {
    doTest();
  }

  public void testTypesGenericsRaw2Concrete() throws Exception {
    doTest();
  }

  public void testTypesGenericsRaw2Raw() throws Exception {
    doTest();
  }

  public void testTypesImplements() throws Exception {
    doTest();
  }

  public void testTypesNoRelationship() throws Exception {
    doTest(false);
  }

  public void testAnonymousTest() throws Exception {
    doTest();
  }

  public void testAnonymousTest1() throws Exception {
    doTest();
  }

  public void testAnonymousTest2() throws Exception {
    doTest(false);
  }

  public void testReturnVoidTest() throws Exception {
    doTest();
  }

  public void testThisReferenceTest() throws Exception {
    doTest();
  }

  public void testAddStaticTest() throws Exception {
    doTest();
  }

  public void testStaticMethodReplacement() throws Exception {
    doTest();
  }

  public void testRefReplacement() throws Exception {
    doTest();
  }

  public void testRefReplacement1() throws Exception {
    doTest();
  }

  public void testReturnVariable() throws Exception {
    doTest();
  }

  public void testReturnExpressionDifferent() throws Exception {
    doTest(false);
  }

  public void testTypeInheritance() throws Exception {
    doTest();
  }

  public void testTypeInheritance1() throws Exception {
    doTest(false);
  }

  public void testUnusedParameter() throws Exception {
    doTest();
  }

  public void testUnusedParameter1() throws Exception {
    doTest();
  }

  public void testInheritance() throws Exception {
    doTest();
  }

  public void testVarargs() throws Exception {
    doTest();
  }

  public void testDeclarationUsage() throws Exception {
    doTest(false);
  }

  public void testChangingReturnType() throws Exception {
    doTest();
  }
}