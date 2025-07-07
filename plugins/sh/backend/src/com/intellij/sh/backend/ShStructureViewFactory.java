// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.backend;

import com.intellij.icons.AllIcons;
import com.intellij.ide.structureView.*;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.lang.PsiStructureViewFactory;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.editor.Editor;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.sh.ShBundle;
import com.intellij.sh.psi.ShFile;
import com.intellij.sh.psi.ShFunctionDefinition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

final class ShStructureViewFactory implements PsiStructureViewFactory {
  @Override
  public @Nullable StructureViewBuilder getStructureViewBuilder(@NotNull PsiFile psiFile) {
    if (!(psiFile instanceof ShFile)) return null;
    return new TreeBasedStructureViewBuilder() {
      @Override
      public @NotNull StructureViewModel createStructureViewModel(@Nullable Editor editor) {
        return new Model(psiFile);
      }
    };
  }

  private static final class Model extends StructureViewModelBase implements StructureViewModel.ElementInfoProvider {
    private Model(@NotNull PsiFile psiFile) {
      super(psiFile, new Element(psiFile));
      withSuitableClasses(ShFile.class, ShFunctionDefinition.class);
    }

    @Override
    public boolean isAlwaysShowsPlus(StructureViewTreeElement structureViewTreeElement) {
      return false;
    }

    @Override
    public boolean isAlwaysLeaf(StructureViewTreeElement structureViewTreeElement) {
      return false;
    }
  }

  private static final class Element implements StructureViewTreeElement, ItemPresentation, NavigationItem {
    private final PsiElement myElement;

    private Element(PsiElement element) {
      myElement = element;
    }

    @Override
    public Object getValue() {
      return myElement;
    }

    @Override
    public void navigate(boolean requestFocus) {
      ((Navigatable) myElement).navigate(requestFocus);
    }

    @Override
    public boolean canNavigate() {
      return ((Navigatable) myElement).canNavigate();
    }

    @Override
    public boolean canNavigateToSource() {
      return ((Navigatable) myElement).canNavigateToSource();
    }

    @Override
    public @Nullable String getName() {
      return myElement instanceof ShFunctionDefinition ? getFunctionName((ShFunctionDefinition) myElement) : null;
    }

    private static String getFunctionName(ShFunctionDefinition myElement) {
      String name = myElement.getName();
      return name == null ? ShBundle.message("sh.unnamed.element.presentable.name") : name;
    }

    @Override
    public @NotNull ItemPresentation getPresentation() {
      return this;
    }

    @Override
    public TreeElement @NotNull [] getChildren() {
      return SyntaxTraverser.psiTraverser(myElement)
          .children(myElement).flatMap(
              ch -> SyntaxTraverser.psiTraverser(ch).expand(psiElement -> !(psiElement instanceof ShFunctionDefinition))
          )
          .filter(ShFunctionDefinition.class)
          .map(Element::new)
          .toArray(new Element[]{});
    }

    @Override
    public String getPresentableText() {
      if (myElement instanceof ShFunctionDefinition) {
        return getFunctionName((ShFunctionDefinition) myElement);
      }
      else if (myElement instanceof ShFile) {
        return ((ShFile) myElement).getName();
      }
      else if (myElement instanceof PsiNamedElement) return ((PsiNamedElement) myElement).getName();

      throw new AssertionError(myElement.getClass().getName());
    }

    @Override
    public Icon getIcon(boolean open) {
      if (!myElement.isValid()) return null;
      if (myElement instanceof ShFunctionDefinition) {
        return AllIcons.Nodes.Lambda;
      }
      return myElement.getIcon(0);
    }
  }
}
