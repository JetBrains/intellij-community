/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.psi.impl;

import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.ResolveImportUtil;
import com.jetbrains.python.psi.types.PyImportedModuleType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PyImportedModule extends LightElement implements PyTypedElement {
  @Nullable private final PyImportElement myImportElement;
  @NotNull private final PyFile myContainingFile;
  @NotNull private final QualifiedName myImportedPrefix;

  /**
   * @param importElement  parental import element, may be {@code null} if we're resolving {@code module} part in {@code from module import ...} statement
   * @param containingFile file to be used as anchor e.g. to determine relative import position
   * @param importedPrefix qualified name to resolve
   *
   * @see ResolveImportUtil
   */
  public PyImportedModule(@Nullable PyImportElement importElement, @NotNull PyFile containingFile, @NotNull QualifiedName importedPrefix) {
    super(containingFile.getManager(), PythonLanguage.getInstance());
    myImportElement = importElement;
    myContainingFile = containingFile;
    myImportedPrefix = importedPrefix;
  }

  @NotNull
  @Override
  public PyFile getContainingFile() {
    return myContainingFile;
  }

  @NotNull
  public QualifiedName getImportedPrefix() {
    return myImportedPrefix;
  }

  public String getText() {
    return "import " + myImportedPrefix;
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    visitor.visitElement(this);
  }

  public PsiElement copy() {
    return new PyImportedModule(myImportElement, myContainingFile, myImportedPrefix);
  }

  @Override
  public String toString() {
    return "PyImportedModule:" + myImportedPrefix;
  }

  @NotNull
  @Override
  public PsiElement getNavigationElement() {
    if (myImportElement != null) {
      final PsiElement element = resolve(myImportElement, myImportedPrefix);
      if (element != null) {
        return element;
      }
    }
    return super.getNavigationElement();
  }

  @Nullable
  public PyImportElement getImportElement() {
    return myImportElement;
  }

  @Nullable
  public PsiElement resolve() {
    final PsiElement element;
    if (myImportElement != null) {
      element = ResolveImportUtil.resolveImportElement(myImportElement, myImportedPrefix);
    }
    else {
      element = ResolveImportUtil.resolveModuleInRoots(myImportedPrefix, myContainingFile);
    }
    if (element instanceof PsiDirectory) {
      return PyUtil.getPackageElement((PsiDirectory)element, this);
    }
    return element;
  }

  @Nullable
  @Override
  public PyType getType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
    return new PyImportedModuleType(this);
  }

  @Nullable
  private static PsiElement resolve(PyImportElement importElement, @NotNull final QualifiedName prefix) {
    final PsiElement resolved = ResolveImportUtil.resolveImportElement(importElement, prefix);
    final PsiElement packageInit = PyUtil.turnDirIntoInit(resolved);
    return packageInit != null ? packageInit : resolved;
  }
}
