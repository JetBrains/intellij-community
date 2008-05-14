/*
 * User: anna
 * Date: 06-May-2008
 */
package com.intellij.refactoring;

import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.refactoring.replaceMethodWithMethodObject.ReplaceMethodWithMethodObjectProcessor;

public class ReplaceMethodWithMethodObjectTest extends CodeInsightTestCase {

  private void doTest() throws Exception {
    doTest(new Condition<PsiMethod>() {
      public boolean value(final PsiMethod method) {
        return OverridingMethodsSearch.search(method).findAll().isEmpty() && method.findSuperMethods().length == 0;
      }
    });
  }

  private void doTest(final Condition<PsiMethod> deleteOriginalMethod) throws Exception {
    final String testName = getTestName(true);
    configureByFile("/refactoring/replaceMethodWithMethodObject/" + testName + ".java");
    PsiElement element = TargetElementUtilBase.findTargetElement(myEditor, TargetElementUtilBase.ELEMENT_NAME_ACCEPTED);
    assertTrue(element instanceof PsiMethod);
    PsiMethod method = (PsiMethod) element;
    new ReplaceMethodWithMethodObjectProcessor(method, "InnerClass", deleteOriginalMethod.value(method)).run();
    checkResultByFile("/refactoring/replaceMethodWithMethodObject/" + testName + ".java" + ".after");
  }

  public void testStatic() throws Exception {
    doTest();
  }

  public void testStaticTypeParams() throws Exception {
    doTest();
  }

  public void testStaticTypeParamsReturn() throws Exception {
    doTest();
  }

  public void testTypeParamsReturn() throws Exception {
    doTest();
  }

  public void testTypeParams() throws Exception {
    doTest();
  }

  public void testMethodInHierarchy() throws Exception {
    doTest();
  }

  public void testQualifier() throws Exception {
    doTest();
  }

  public void testVarargs() throws Exception {
    doTest();
  }

  public void testFieldUsage() throws Exception {
    doTest();
  }

  public void testMethodInHierarchyReturn() throws Exception {
    doTest();
  }

  public void testStaticTypeParamsReturnNoDelete() throws Exception {
    doTest(Condition.FALSE);
  }

  public void testStaticTypeParamsRecursive() throws Exception {
    doTest();
  }

  public void testRecursion() throws Exception {
    doTest();
  }
}