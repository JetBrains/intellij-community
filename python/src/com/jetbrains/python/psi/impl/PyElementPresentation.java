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
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;
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
    String packageForFile = getPackageForFile(myElement.getContainingFile());
    return packageForFile != null ? String.format("(%s)", packageForFile) : null;
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
