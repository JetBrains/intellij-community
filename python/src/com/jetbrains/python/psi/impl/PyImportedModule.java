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
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyTypedElement;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.resolve.ResolveImportUtil;
import com.jetbrains.python.psi.types.PyImportedModuleType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

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

  @Override
  public String getText() {
    return "import " + myImportedPrefix;
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    visitor.visitElement(this);
  }

  @Override
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

  @Override
  public boolean isValid() {
    return (myImportElement == null || myImportElement.isValid()) && myContainingFile.isValid();
  }

  @Nullable
  public PyImportElement getImportElement() {
    return myImportElement;
  }

  /**
   * @deprecated use {@link #multiResolve()} instead
   */
  @Deprecated
  public PsiElement resolve() {
    return multiResolve().stream().findFirst().map(res -> res.getElement()).orElse(null);
  }

  @NotNull
  public List<RatedResolveResult> multiResolve() {
    final List<RatedResolveResult> results;
    if (myImportElement != null) {
      results = ResolveImportUtil.multiResolveImportElement(myImportElement, myImportedPrefix);
    }
    else {
      final ResolveResultList resList = new ResolveResultList();
      ResolveImportUtil.multiResolveModuleInRoots(myImportedPrefix, myContainingFile)
                       .forEach(res -> resList.poke(res, RatedResolveResult.RATE_NORMAL));
      results = resList;
    }
    return ContainerUtil.map(results, this::tryReplaceDirWithPackage);
  }

  private RatedResolveResult tryReplaceDirWithPackage(RatedResolveResult el) {
    final PsiElement element = el.getElement();
    return element instanceof PsiDirectory ? el.replace(PyUtil.getPackageElement((PsiDirectory)element, this)) : el;
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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final PyImportedModule module = (PyImportedModule)o;
    return Objects.equals(myImportElement, module.myImportElement) &&
           Objects.equals(myContainingFile, module.myContainingFile) &&
           Objects.equals(myImportedPrefix, module.myImportedPrefix);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myImportElement, myContainingFile, myImportedPrefix);
  }
}
