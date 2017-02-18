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
package com.jetbrains.rest.structureView;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.rest.psi.RestElement;
import com.jetbrains.rest.psi.RestTitle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Handles nodes in ReST Structure View.
 * User : catherine
 */
public class RestStructureViewElement implements StructureViewTreeElement {

  private NavigatablePsiElement myElement;

  public RestStructureViewElement(NavigatablePsiElement element) {
    myElement = element;
  }

  public NavigatablePsiElement getValue() {
    return myElement;
  }

  public void navigate(boolean requestFocus) {
    myElement.navigate(requestFocus);
  }

  public boolean canNavigate() {
    return myElement.canNavigate();
  }

  public boolean canNavigateToSource() {
    return myElement.canNavigateToSource();
  }

  @NotNull
  public StructureViewTreeElement[] getChildren() {
    final Set<RestElement> childrenElements = new LinkedHashSet<>();
    myElement.acceptChildren(new PsiElementVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        if (element instanceof RestTitle && ((RestTitle)element).getName() != null)
          childrenElements.add((RestElement)element);
        else
          element.acceptChildren(this);
      }
    });
    StructureViewTreeElement[] children = new StructureViewTreeElement[childrenElements.size()];
    int i = 0;
    for (RestElement element : childrenElements) {
      children[i] = new RestStructureViewElement(element);
      i += 1;
    }

    return children;
  }

  @NotNull
  public ItemPresentation getPresentation() {
    return new ItemPresentation() {
      public String getPresentableText() {
        return myElement.getName();
      }

      @Nullable
      public String getLocationString() {
        return null;
      }

      public Icon getIcon(boolean open) {
        return null;
      }
    };
  }
}
