package com.jetbrains.jython;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.psi.*;
import com.intellij.testFramework.ResolveTestCase;
import com.intellij.testFramework.TestDataPath;
import com.jetbrains.python.PythonTestUtil;
import junit.framework.Assert;

/**
 * @author yole
 */
@TestDataPath("$CONTENT_ROOT/../testData/resolve/pyToJava/")
public class PyToJavaResolveTest extends ResolveTestCase {
  private PsiElement resolve() throws Exception {
    PsiReference ref = configureByFile(getTestName(false) + ".py");
    return ref.resolve();
  }

  public void testSimple() throws Exception {
    PsiElement target = resolve();
    Assert.assertTrue(target instanceof PsiClass);
    Assert.assertEquals("java.util.ArrayList", ((PsiClass) target).getQualifiedName());
  }

  public void testMethod() throws Exception {
    PsiElement target = resolve();
    Assert.assertTrue(target instanceof PsiMethod);
    Assert.assertEquals("java.util.ArrayList", ((PsiMethod) target).getContainingClass().getQualifiedName());
  }

  public void testField() throws Exception {
    PsiElement target = resolve();
    Assert.assertTrue(target instanceof PsiField);
    Assert.assertEquals("java.lang.System", ((PsiField) target).getContainingClass().getQualifiedName());
  }

  public void testReturnValue() throws Exception {
    PsiElement target = resolve();
    Assert.assertTrue(target instanceof PsiMethod);
    Assert.assertEquals("java.util.List", ((PsiMethod) target).getContainingClass().getQualifiedName());
  }

  @Override
  protected String getTestDataPath() {
    return PythonTestUtil.getTestDataPath() + "/resolve/pyToJava/";
  }
}
