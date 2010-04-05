/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.uiDesigner.palette;

import com.intellij.refactoring.listeners.RefactoringElementListenerProvider;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiClass;
import com.intellij.psi.util.ClassUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
*/
public class PaletteRefactoringListenerProvider implements RefactoringElementListenerProvider {
  private final UIDesignerPaletteProvider myUiDesignerPaletteProvider;
  private final Palette myPalette;

  public PaletteRefactoringListenerProvider(UIDesignerPaletteProvider uiDesignerPaletteProvider, Palette palette) {
    myUiDesignerPaletteProvider = uiDesignerPaletteProvider;
    myPalette = palette;
  }

  public RefactoringElementListener getListener(PsiElement element) {
    if (element instanceof PsiClass) {
      PsiClass psiClass = (PsiClass) element;
      final String oldName = ClassUtil.getJVMClassName(psiClass);
      if (oldName != null) {
        final ComponentItem item = myPalette.getItem(oldName);
        if (item != null) {
          return new MyRefactoringElementListener(item);
        }
      }
    }
    return null;
  }

  private class MyRefactoringElementListener implements RefactoringElementListener {
    private final ComponentItem myItem;

    public MyRefactoringElementListener(final ComponentItem item) {
      myItem = item;
    }

    public void elementMoved(@NotNull PsiElement newElement) {
      elementRenamed(newElement);
    }

    public void elementRenamed(@NotNull PsiElement newElement) {
      PsiClass psiClass = (PsiClass) newElement;
      final String qName = ClassUtil.getJVMClassName(psiClass);
      if (qName != null) {
        myItem.setClassName(qName);
        myUiDesignerPaletteProvider.fireGroupsChanged();
      }
    }
  }
}
