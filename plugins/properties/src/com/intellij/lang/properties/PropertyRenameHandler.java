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
package com.intellij.lang.properties;

import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.refactoring.rename.PsiElementRenameHandler;
import com.intellij.lang.properties.references.PropertyReferenceBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public class PropertyRenameHandler extends PsiElementRenameHandler {

  public boolean isAvailableOnDataContext(final DataContext dataContext) {
    final Editor editor = LangDataKeys.EDITOR.getData(dataContext);
    if (editor != null) {
      if (getPsiElement(editor) != null) return true;
    }
    return false;
  }

  @Nullable
  private static PsiElement getPsiElement(final Editor editor) {
    final PsiReference reference = TargetElementUtilBase.findReference(editor);
    if (reference instanceof PropertyReferenceBase) {
      final ResolveResult[] resolveResults = ((PropertyReferenceBase)reference).multiResolve(false);
      return resolveResults.length > 0 ? resolveResults[0].getElement() : null;
    }
    return null;
  }

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file, final DataContext dataContext) {
    PsiElement element = getPsiElement(editor);
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    final PsiElement nameSuggestionContext = file.findElementAt(editor.getCaretModel().getOffset());
    invoke(element, project, nameSuggestionContext, editor);
  }
}
