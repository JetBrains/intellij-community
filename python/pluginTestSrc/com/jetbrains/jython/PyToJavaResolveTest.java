package com.jetbrains.jython;

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

  public void testPackageType() throws Exception {
    PsiElement target = resolve();
    Assert.assertTrue(target instanceof PsiClass);
    Assert.assertEquals("java.util.ArrayList", ((PsiClass) target).getQualifiedName());
  }

  public void testJavaPackage() throws Exception {
    PsiElement target = resolve();
    Assert.assertTrue(target instanceof PsiPackage);
    Assert.assertEquals("java", ((PsiPackage) target).getQualifiedName());
  }

  public void testJavaLangPackage() throws Exception {
    PsiElement target = resolve();
    Assert.assertTrue(target instanceof PsiPackage);
    Assert.assertEquals("java.lang", ((PsiPackage) target).getQualifiedName());
  }

  public void testSuperMethod() throws Exception {
    PsiElement target = resolve();
    Assert.assertTrue(target instanceof PsiMethod);
    Assert.assertEquals("size", ((PsiMethod) target).getName());
  }

  public void testFieldType() throws Exception {
    PsiElement target = resolve();
    Assert.assertTrue(target instanceof PsiMethod);
    Assert.assertEquals("println", ((PsiMethod) target).getName());
  }

  @Override
  protected String getTestDataPath() {
    return PythonTestUtil.getTestDataPath() + "/resolve/pyToJava/";
  }
}
