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
package com.jetbrains.python.psi.impl;

import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.ResolveImportUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author yole
 */
public class PyImportedModule extends LightElement implements NameDefiner {
  @Nullable private PyImportElement myImportElement;
  @NotNull private final PyFile myContainingFile;
  @NotNull private final QualifiedName myImportedPrefix;

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

  @NotNull
  public Iterable<PyElement> iterateNames() {
    throw new UnsupportedOperationException();
  }

  public PsiElement getElementNamed(String the_name) {
    QualifiedName prefix = myImportedPrefix.append(the_name);
    if (myImportElement != null) {
      final QualifiedName qName = myImportElement.getImportedQName();
      if (qName != null && qName.getComponentCount() == prefix.getComponentCount()) {
        return resolve(myImportElement, prefix);
      }
      return new PyImportedModule(myImportElement, myContainingFile, prefix);
    }
    final PyImportElement fromImportElement = findMatchingFromImport(myImportedPrefix, the_name);
    if (fromImportElement != null) {
      return fromImportElement.resolve();
    }

    return null;
  }

  @Nullable
  private PyImportElement findMatchingFromImport(QualifiedName prefix, String name) {
    final List<PyFromImportStatement> fromImports = getContainingFile().getFromImports();
    for (PyFromImportStatement fromImport : fromImports) {
      final QualifiedName qName = fromImport.getImportSourceQName();
      if (prefix.equals(qName)) {
        final PyImportElement[] importElements = fromImport.getImportElements();
        for (PyImportElement importElement : importElements) {
          final QualifiedName importedName = importElement.getImportedQName();
          if (importedName != null && importedName.matches(name)) {
            return importElement;
          }
        }
      }
    }
    return null;
  }

  public boolean mustResolveOutside() {
    return true;
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
      element = ResolveImportUtil.resolveModuleInRoots(getImportedPrefix(), getContainingFile());
    }
    if (element instanceof PsiDirectory) {
      return PyUtil.getPackageElement((PsiDirectory)element);
    }
    return element;
  }

  @Nullable
  private static PsiElement resolve(PyImportElement importElement, @NotNull final QualifiedName prefix) {
    final PsiElement resolved = ResolveImportUtil.resolveImportElement(importElement, prefix);
    final PsiElement packageInit = PyUtil.turnDirIntoInit(resolved);
    return packageInit != null ? packageInit : resolved;
  }

  public boolean isAncestorOf(PyImportedModule other) {
    return PsiTreeUtil.isAncestor(myContainingFile, other.myContainingFile, true);
  }
}
