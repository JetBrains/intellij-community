// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.refactoring;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.LightMultiFileTestCase;
import com.intellij.refactoring.wrapreturnvalue.WrapReturnValueProcessor;
import org.jetbrains.annotations.NonNls;

/**
 * @author anna
 */
public class WrapReturnValueTest extends LightMultiFileTestCase {
  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("rareJavaRefactorings") + "/testData/wrapReturnValue/";
  }

  public void testSimple() { doTest(false); }
  public void testGenerics() { doTest(false); }
  public void testInconsistentWrapper() { doTest(true, "Existing class does not have getter for selected field"); }
  public void testWrapper() { doTest(true); }
  public void testStrip() { doTest(true); }
  public void testNoConstructor() { doTest(true, "Existing class does not have appropriate constructor"); }
  public void testInnerClass() { doTest(false, null, true); }
  public void testHierarchy() { doTest(false, null, true); }
  public void testAnonymous() { doTest(true, null, false); }
  public void testWrongFieldAssignment() { doTest(true, "Existing class does not have appropriate constructor", false); }
  public void testInferFieldType() { doTest(true, null, false); }
  public void testInferFieldTypeArg() { doTest(true, null, false); }
  public void testWrongFieldType() { doTest(true, "Existing class does not have appropriate constructor", false); }
  public void testStaticMethodInnerClass() { doTest(false, null, true); }
  public void testOpenMethodReference() { doTest(false, null, true); }
  public void testRawReturnType() { doTest(true, "Existing class does not have appropriate constructor"); }
  public void testReturnInsideLambda() { doTest(false, null, true); }
  public void testTypeAnnotations() { doTest(false); }
  public void testWithLambdaInside() { doTest(true); }

  private void doTest(final boolean existing) {
    doTest(existing, null);
  }

  private void doTest(final boolean existing, @NonNls String exceptionMessage) {
    doTest(existing, exceptionMessage, false);
  }

  private void doTest(final boolean existing, String exceptionMessage, final boolean createInnerClass) {
    try {
      doTest(() -> {
        PsiClass aClass = myFixture.findClass("Test");
        PsiMethod method = aClass.findMethodsByName("foo", false)[0];
        String wrapperClassName = "Wrapper";
        PsiClass wrapperClass = myFixture.getJavaFacade().findClass(wrapperClassName, GlobalSearchScope.projectScope(getProject()));
        assertTrue(!existing || wrapperClass != null);
        PsiField delegateField = existing ? wrapperClass.findFieldByName("myField", false) : null;
        new WrapReturnValueProcessor(wrapperClassName, "", null, method, existing, createInnerClass, delegateField).run();
      });
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      if (exceptionMessage != null) {
        assertEquals(exceptionMessage, e.getMessage());
        return;
      }
      throw e;
    }
    if (exceptionMessage != null) {
      fail("Conflict was not found");
    }
  }
}