/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.jython;

import com.intellij.psi.*;
import com.intellij.testFramework.ResolveTestCase;
import com.intellij.testFramework.TestDataPath;
import com.jetbrains.python.PythonHelpersLocator;
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
    Assert.assertEquals(CommonClassNames.JAVA_UTIL_LIST, ((PsiMethod) target).getContainingClass().getQualifiedName());
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
    return PythonHelpersLocator.getPythonCommunityPath() + "/testData/resolve/pyToJava/";
  }
}
