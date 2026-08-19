// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.projectView;

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewModelBase;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.lang.PsiStructureViewFactory;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtilCore;
import org.intellij.plugins.markdown.lang.MarkdownTokenTypeSets;
import org.intellij.plugins.markdown.util.MarkdownPsiStructureUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MarkdownStructureViewFactory implements PsiStructureViewFactory {
  @Override
  public @Nullable StructureViewBuilder getStructureViewBuilder(final @NotNull PsiFile psiFile) {
    return new TreeBasedStructureViewBuilder() {
      @Override
      public @NotNull StructureViewModel createStructureViewModel(@Nullable Editor editor) {
        return new MarkdownStructureViewModel(psiFile, editor);
      }

      @Override
      public boolean isRootNodeShown() {
        return false;
      }
    };
  }

  private static class MarkdownStructureViewModel extends StructureViewModelBase implements StructureViewModel.ElementInfoProvider {
    MarkdownStructureViewModel(@NotNull PsiFile psiFile, @Nullable Editor editor) {
      super(psiFile, editor, new MarkdownStructureElement(psiFile));
    }

    @Override
    protected @Nullable Object findAcceptableElement(PsiElement element) {
      // walk up the psi-tree until we find an element from the structure view
      while (element != null && !(element instanceof PsiFile) && !MarkdownPsiStructureUtil.PRESENTABLE_TYPES.contains(PsiUtilCore.getElementType(element))) {
        IElementType parentType = PsiUtilCore.getElementType(element.getParent());

        final PsiElement previous = element.getPrevSibling();
        if (previous == null || !MarkdownPsiStructureUtil.TRANSPARENT_CONTAINERS.contains(parentType)) {
          element = element.getParent();
        }
        else {
          element = previous;
        }
      }

      return element;
    }

    @Override
    public boolean isAlwaysShowsPlus(StructureViewTreeElement element) {
      return false;
    }

    @Override
    public boolean isAlwaysLeaf(StructureViewTreeElement element) {
      return MarkdownTokenTypeSets.HEADER_LEVEL_6_SET.contains(PsiUtilCore.getElementType((PsiElement)element.getValue()));
    }
  }
}
