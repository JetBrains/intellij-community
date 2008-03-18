package com.jetbrains.python;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.psi.*;
import com.intellij.testFramework.ResolveTestCase;

/**
 * @author yole
 */
public class PyToJavaResolveTest extends ResolveTestCase {
  protected Sdk getTestProjectJdk() {
    return JavaSdkImpl.getMockJdk15("java 1.5");
  }

  private PsiElement resolve() throws Exception {
    PsiReference ref = configureByFile(getTestName(false) + ".py");
    return ref.resolve();
  }

  public void testSimple() throws Exception {
    PsiElement target = resolve();
    assertTrue(target instanceof PsiClass);
    assertEquals("java.util.ArrayList", ((PsiClass) target).getQualifiedName());
  }

  public void testMethod() throws Exception {
    PsiElement target = resolve();
    assertTrue(target instanceof PsiMethod);
    assertEquals("java.util.ArrayList", ((PsiMethod) target).getContainingClass().getQualifiedName());
  }

  public void testField() throws Exception {
    PsiElement target = resolve();
    assertTrue(target instanceof PsiField);
    assertEquals("java.lang.System", ((PsiField) target).getContainingClass().getQualifiedName());
  }

  public void testReturnValue() throws Exception {
    PsiElement target = resolve();
    assertTrue(target instanceof PsiMethod);
    assertEquals("java.util.List", ((PsiMethod) target).getContainingClass().getQualifiedName());
  }

  @Override
  protected String getTestDataPath() {
    return PathManager.getHomePath() + "/plugins/python/testData/resolve/pyToJava/";
  }
}
