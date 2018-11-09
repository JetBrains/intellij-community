// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.hierarchy;

import com.intellij.ide.hierarchy.HierarchyBrowser;
import com.intellij.ide.hierarchy.HierarchyProvider;
import com.intellij.ide.hierarchy.TypeHierarchyBrowserBase;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PyTypeHierachyProvider implements HierarchyProvider {
  @Override
  @Nullable
  public PsiElement getTarget(@NotNull DataContext dataContext) {
    PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
    if (element == null) {
      final Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
      final PsiFile file = CommonDataKeys.PSI_FILE.getData(dataContext);
      if (editor != null && file != null) {
        element = file.findElementAt(editor.getCaretModel().getOffset());
      }
    }
    if (!(element instanceof PyClass)) {
      element = PsiTreeUtil.getParentOfType(element, PyClass.class);
    }
    return element;
  }

  @Override
  @NotNull
  public HierarchyBrowser createHierarchyBrowser(@NotNull PsiElement target) {
    return new PyTypeHierarchyBrowser((PyClass)target);
  }

  @Override
  public void browserActivated(@NotNull HierarchyBrowser hierarchyBrowser) {
    ((PyTypeHierarchyBrowser)hierarchyBrowser).changeView(TypeHierarchyBrowserBase.TYPE_HIERARCHY_TYPE);
  }
}
