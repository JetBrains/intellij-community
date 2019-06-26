/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python;

import com.intellij.psi.PsiElement;
import com.jetbrains.python.actions.PyQualifiedNameProvider;
import com.jetbrains.python.fixtures.PyTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Mikhail Golubev
 */
public class PyQualifiedNameProviderTest extends PyTestCase {
  public void testTopLevelFunctionReference() {
    myFixture.copyDirectoryToProject(getTestName(true) + "/a", "a");
    myFixture.configureByFile("a/b/c/module.py");
    assertEquals("a.b.c.module.func", getQualifiedNameOfElementUnderCaret());
  }

  @Nullable
  private String getQualifiedNameOfElementUnderCaret() {
    return new PyQualifiedNameProvider().getQualifiedName(myFixture.getElementAtCaret());
  }

  public void testNestedClassReference() {
    doDirectoryTest("pkg/subpkg/mod.py", "pkg.subpkg.mod.MyClass.Nested");
  }

  public void testMethodReference() {
    doDirectoryTest("pkg/subpkg/mod.py", "pkg.subpkg.mod.MyClass.method");
  }

  private void doDirectoryTest(@NotNull String targetFile, @NotNull String expectedQualifiedName) {
    myFixture.copyDirectoryToProject(getTestName(true), "");
    myFixture.configureByFile(targetFile);
    final PsiElement target = myFixture.getElementAtCaret();
    final PyQualifiedNameProvider provider = new PyQualifiedNameProvider();
    final String actualQualifiedName = provider.getQualifiedName(myFixture.getElementAtCaret());
    assertEquals(expectedQualifiedName, actualQualifiedName);
    final PsiElement element = provider.qualifiedNameToElement(expectedQualifiedName, myFixture.getProject());
    assertEquals(target, element);
  }

  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/qualifiedName";
  }
}
