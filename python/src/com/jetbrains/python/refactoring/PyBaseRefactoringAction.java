/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.python.refactoring;

import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.actions.BaseRefactoringAction;
import com.jetbrains.python.PythonLanguage;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public abstract class PyBaseRefactoringAction extends BaseRefactoringAction {

  protected abstract boolean isEnabledOnElementInsideEditor(@NotNull PsiElement element,
                                                            @NotNull Editor editor,
                                                            @NotNull PsiFile file,
                                                            @NotNull DataContext context);

  @Override
  protected final boolean isAvailableOnElementInEditorAndFile(@NotNull PsiElement element,
                                                              @NotNull Editor editor,
                                                              @NotNull PsiFile file,
                                                              @NotNull DataContext context) {
    return isEnabledOnElementInsideEditor(element, editor, file, context);
  }

  protected abstract boolean isEnabledOnElementsOutsideEditor(@NotNull PsiElement[] elements);

  @Override
  protected final boolean isEnabledOnElements(@NotNull PsiElement[] elements) {
    return isEnabledOnElementsOutsideEditor(elements);
  }

  @Override
  protected final boolean isAvailableForLanguage(Language language) {
    return language.isKindOf(PythonLanguage.getInstance());
  }

  @Override
  protected boolean isAvailableForFile(PsiFile file) {
    return isAvailableForLanguage(file.getLanguage()) && !isLibraryFile(file);
  }

  private static boolean isLibraryFile(@NotNull PsiFile file) {
    final VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile != null && ProjectRootManager.getInstance(file.getProject()).getFileIndex().isInLibraryClasses(virtualFile)) {
      return true;
    }
    return false;
  }
}
