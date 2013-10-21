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

import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;

import javax.swing.*;

/**
 * @author yole
 */
public abstract class PyPresentableElementImpl<T extends StubElement> extends PyBaseElementImpl<T> implements PsiNamedElement {
  public PyPresentableElementImpl(ASTNode astNode) {
    super(astNode);
  }

  protected PyPresentableElementImpl(final T stub, final IStubElementType nodeType) {
    super(stub, nodeType);
  }

  public ItemPresentation getPresentation() {
    return new ItemPresentation() {
      public String getPresentableText() {
        final String name = getName();
        return name != null ? name : "<none>";
      }

      public String getLocationString() {
        return getElementLocation();
      }

      public Icon getIcon(final boolean open) {
        return PyPresentableElementImpl.this.getIcon(0);
      }
    };
  }

  protected String getElementLocation() {
    return "(" + getPackageForFile(getContainingFile()) + ")";
  }

  public static String getPackageForFile(final PsiFile containingFile) {
    final VirtualFile vFile = containingFile.getVirtualFile();

    if (vFile != null) {
      final String importableName = QualifiedNameFinder.findShortestImportableName(containingFile, vFile);
      if (importableName != null) {
        return importableName;
      }
    }
    return "";
  }
}
