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

import com.intellij.navigation.ColoredItemPresentation;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyPossibleClassMember;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.pyi.PyiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
* @author vlan
*/
public class PyElementPresentation implements ColoredItemPresentation {
  @NotNull private final PyElement myElement;

  public PyElementPresentation(@NotNull PyElement element) {
    myElement = element;
  }

  @Nullable
  @Override
  public TextAttributesKey getTextAttributesKey() {
    return null;
  }

  @Nullable
  @Override
  public String getPresentableText() {
    final String name = myElement.getName();
    return name != null ? name : PyNames.UNNAMED_ELEMENT;
  }

  @Nullable
  @Override
  public String getLocationString() {
    PsiFile containingFile = myElement.getContainingFile();

    String packageForFile = getPackageForFile(containingFile);
    if (packageForFile == null) return null;

    boolean isPyiFile = containingFile instanceof PyiFile;

    PyClass containingClass = myElement instanceof PyPossibleClassMember ? ((PyPossibleClassMember)myElement).getContainingClass() : null;
    if (containingClass != null) {
      if (isPyiFile) {
        return PyPsiBundle.message("element.presentation.location.string.in.class.stub", containingClass.getName(), packageForFile);
      }
      else {
        return PyPsiBundle.message("element.presentation.location.string.in.class", containingClass.getName(), packageForFile);
      }
    }

    if (isPyiFile) {
      return PyPsiBundle.message("element.presentation.location.string.module.stub", packageForFile);
    }
    else {
      return PyPsiBundle.message("element.presentation.location.string.module", packageForFile);
    }
  }

  @Nullable
  @Override
  public Icon getIcon(boolean unused) {
    return myElement.getIcon(0);
  }

  @Nullable
  public static String getPackageForFile(@NotNull PsiFile containingFile) {
    final VirtualFile vFile = containingFile.getVirtualFile();

    if (vFile != null) {
      return QualifiedNameFinder.findShortestImportableName(containingFile, vFile);
    }
    return null;
  }
}
