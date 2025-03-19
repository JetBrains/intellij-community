// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.highlighting;

import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase;
import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerFactoryBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyReturnStatement;
import org.jetbrains.annotations.NotNull;

public final class PyHighlightExitPointsHandlerFactory extends HighlightUsagesHandlerFactoryBase implements DumbAware {
  @Override
  public HighlightUsagesHandlerBase createHighlightUsagesHandler(@NotNull Editor editor, @NotNull PsiFile file, @NotNull PsiElement target) {
    final PyReturnStatement returnStatement = PsiTreeUtil.getParentOfType(target, PyReturnStatement.class);
    if (returnStatement != null) {
      final PyExpression returnExpr = returnStatement.getExpression();
      if (returnExpr == null || !PsiTreeUtil.isAncestor(returnExpr, target, false)) {
        return new PyHighlightExitPointsHandler(editor, file, target);
      }
    }
    return null;
  }
}
