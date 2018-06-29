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

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.roots.ui.util.CompositeAppearance;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.NotNull;

public class PyHierarchyNodeDescriptor extends HierarchyNodeDescriptor {
  public PyHierarchyNodeDescriptor(final NodeDescriptor parentDescriptor, @NotNull final PsiElement element, final boolean isBase) {
    super(element.getProject(), parentDescriptor, element, isBase);
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
      myHighlightedText.getEnding().addText(" " + presentation.getLocationString(), HierarchyNodeDescriptor.getPackageNameAttributes());
    }
    myName = myHighlightedText.getText();

    if (!Comparing.equal(myHighlightedText, oldText)) {
      changes = true;
    }
    return changes;
  }
}
