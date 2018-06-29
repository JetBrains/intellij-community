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

import com.intellij.ide.hierarchy.HierarchyBrowser;
import com.intellij.ide.hierarchy.HierarchyProvider;
import com.intellij.ide.hierarchy.TypeHierarchyBrowserBase;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PyTypeHierachyProvider implements HierarchyProvider {
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

  @NotNull
  public HierarchyBrowser createHierarchyBrowser(@NotNull PsiElement target) {
    return new PyTypeHierarchyBrowser((PyClass)target);
  }

  public void browserActivated(@NotNull HierarchyBrowser hierarchyBrowser) {
    ((PyTypeHierarchyBrowser)hierarchyBrowser).changeView(TypeHierarchyBrowserBase.TYPE_HIERARCHY_TYPE);
  }
}
