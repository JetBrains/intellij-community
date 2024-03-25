// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.hierarchy;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.roots.ui.util.CompositeAppearance;
import com.intellij.openapi.util.Comparing;
import com.intellij.pom.Navigatable;
import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
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

  public PyHierarchyNodeDescriptor(final NodeDescriptor parentDescriptor, final @NotNull PsiElement element, final boolean isBase) {
    this(parentDescriptor, element, Collections.emptySet(), isBase);
  }

  public PyHierarchyNodeDescriptor(final NodeDescriptor parentDescriptor,
                                   final @NotNull PsiElement element,
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
