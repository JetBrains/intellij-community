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

import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.ResolveTestCase;
import com.intellij.testFramework.TestDataPath;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.psi.PyFile;

import java.util.List;

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
    assertEquals(CommonClassNames.JAVA_UTIL_LIST, ((PsiMethod) target).getContainingClass().getQualifiedName());
  }

  public void testPackageType() throws Exception {
    PsiElement target = resolve();
    assertTrue(target instanceof PsiClass);
    assertEquals("java.util.ArrayList", ((PsiClass) target).getQualifiedName());
  }

  public void testJavaPackage() throws Exception {
    PsiElement target = resolve();
    assertTrue(target instanceof PsiPackage);
    assertEquals("java", ((PsiPackage) target).getQualifiedName());
  }

  public void testJavaLangPackage() throws Exception {
    PsiElement target = resolve();
    assertTrue(target instanceof PsiPackage);
    assertEquals("java.lang", ((PsiPackage) target).getQualifiedName());
  }

  public void testSuperMethod() throws Exception {
    PsiElement target = resolve();
    assertTrue(target instanceof PsiMethod);
    assertEquals("size", ((PsiMethod) target).getName());
  }

  public void testFieldType() throws Exception {
    PsiElement target = resolve();
    assertTrue(target instanceof PsiMethod);
    assertEquals("println", ((PsiMethod) target).getName());
  }

  // PY-23265, PY-23857
  public void testPurePythonSymbolsFirst() throws Exception {
    final LocalFileSystem fs = LocalFileSystem.getInstance();
    final VirtualFile tmpDir = fs.findFileByIoFile(createTempDirectory());
    copyDirContentsTo(fs.findFileByPath(getTestDataPath() + getTestName(true)), tmpDir);
    PsiTestUtil.addSourceRoot(getModule(), tmpDir.findChild("src"));

    final PsiReference ref = configureByFile(getTestName(true) + "/src/main.py", tmpDir);
    final PsiPolyVariantReference multiRef = (PsiPolyVariantReference)ref;
    final ResolveResult[] results = multiRef.multiResolve(false);
    final List<PsiElement> targets = ContainerUtil.map(results, ResolveResult::getElement);

    assertSize(2, targets);
    assertInstanceOf(targets.get(0), PyFile.class);
    assertInstanceOf(targets.get(1), PsiPackage.class);
  }

  @Override
  protected String getTestDataPath() {
    return PythonHelpersLocator.getPythonCommunityPath() + "/testData/resolve/pyToJava/";
  }
}
