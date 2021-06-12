// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.palette;

import com.intellij.ide.palette.PaletteItemProvider;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.ClassUtil;
import com.intellij.refactoring.listeners.RefactoringElementAdapter;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.listeners.RefactoringElementListenerProvider;
import org.jetbrains.annotations.NotNull;

final class PaletteRefactoringListenerProvider implements RefactoringElementListenerProvider {
  @Override
  public RefactoringElementListener getListener(PsiElement element) {
    if (element instanceof PsiClass) {
      PsiClass psiClass = (PsiClass) element;
      final String oldName = ClassUtil.getJVMClassName(psiClass);
      if (oldName != null) {
        final ComponentItem item = Palette.getInstance(element.getProject()).getItem(oldName);
        if (item != null) {
          return new MyRefactoringElementListener(item);
        }
      }
    }
    return null;
  }

  private static class MyRefactoringElementListener extends RefactoringElementAdapter{
    private final ComponentItem myItem;

    MyRefactoringElementListener(final ComponentItem item) {
      myItem = item;
    }

    @Override
    public void elementRenamedOrMoved(@NotNull PsiElement newElement) {
      PsiClass psiClass = (PsiClass) newElement;
      final String qName = ClassUtil.getJVMClassName(psiClass);
      if (qName != null) {
        myItem.setClassName(qName);
        PaletteItemProvider.EP_NAME.findExtensionOrFail(UIDesignerPaletteProvider.class).fireGroupsChanged();
      }
    }

    @Override
    public void undoElementMovedOrRenamed(@NotNull PsiElement newElement, @NotNull String oldQualifiedName) {
      myItem.setClassName(oldQualifiedName);
      PaletteItemProvider.EP_NAME.findExtensionOrFail(UIDesignerPaletteProvider.class).fireGroupsChanged();
    }
  }
}
