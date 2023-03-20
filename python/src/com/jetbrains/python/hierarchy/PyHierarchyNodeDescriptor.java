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
package com.jetbrains.python.hierarchy;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.roots.ui.util.CompositeAppearance;
import com.intellij.openapi.util.Comparing;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.jetbrains.python.psi.PyUtil.as;

public class PyHierarchyNodeDescriptor extends HierarchyNodeDescriptor implements Navigatable {

  private final List<SmartPsiElementPointer<PsiElement>> myUsages;

  public PyHierarchyNodeDescriptor(final NodeDescriptor parentDescriptor, @NotNull final PsiElement element, final boolean isBase) {
    this(parentDescriptor, element, Collections.emptySet(), isBase);
  }

  public PyHierarchyNodeDescriptor(final NodeDescriptor parentDescriptor,
                                   @NotNull final PsiElement element,
                                   @NotNull Collection<? extends PsiElement> usages,
                                   final boolean isBase) {
    super(element.getProject(), parentDescriptor, element, isBase);
    final var pointerManager = SmartPointerManager.getInstance(myProject);
    myUsages = ContainerUtil.map(usages, pointerManager::createSmartPsiElementPointer);
  }

  @Override
  public boolean update() {
    boolean changes = super.update();
    final CompositeAppearance oldText = myHighlightedText;

    myHighlightedText = new CompositeAppearance();

    NavigatablePsiElement element = (NavigatablePsiElement)getPsiElement();
    if (element == null) {
      return invalidElement();
    }

    final ItemPresentation presentation = element.getPresentation();
    if (presentation != null) {
      if (element instanceof PyFunction) {
        final PyClass cls = ((PyFunction)element).getContainingClass();
        if (cls != null) {
          myHighlightedText.getEnding().addText(cls.getName() + ".");
        }
      }
      myHighlightedText.getEnding().addText(presentation.getPresentableText());
      int count = myUsages.size();
      if (count > 1) {
        String text = " " + IdeBundle.message("node.call.hierarchy.N.usages", count);
        myHighlightedText.getEnding().addText(text, HierarchyNodeDescriptor.getUsageCountPrefixAttributes());
      }
      String locationString = presentation.getLocationString();
      if (locationString != null) {
        myHighlightedText.getEnding().addText(" " + locationString, HierarchyNodeDescriptor.getPackageNameAttributes());
      }
    }
    myName = myHighlightedText.getText();

    if (!Comparing.equal(myHighlightedText, oldText)) {
      changes = true;
    }
    return changes;
  }

  @Override
  public void navigate(boolean requestFocus) {
    Navigatable element = getNavigationTarget();
    if (element != null && element.canNavigate()) {
      element.navigate(requestFocus);
    }
  }

  @Override
  public boolean canNavigate() {
    Navigatable element = getNavigationTarget();
    return element != null && element.canNavigate();
  }

  @Override
  public boolean canNavigateToSource() {
    return canNavigate();
  }

  private @Nullable Navigatable getNavigationTarget() {
    if (!myUsages.isEmpty()) {
      Navigatable firstUsage = as(myUsages.get(0).getElement(), Navigatable.class);
      if (firstUsage != null) {
        return firstUsage;
      }
    }
    return as(getPsiElement(), Navigatable.class);
  }
}
