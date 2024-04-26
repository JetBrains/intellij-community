// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.reStructuredText;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandlerBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.python.reStructuredText.psi.RestReference;
import org.jetbrains.annotations.Nullable;

/**
 * @author catherine
 */
public class RestGotoProvider extends GotoDeclarationHandlerBase {
  @Override
  public PsiElement getGotoDeclarationTarget(@Nullable PsiElement source, Editor editor) {
    if (source != null && source.getLanguage() instanceof RestLanguage) {
      RestReference ref = PsiTreeUtil.getParentOfType(source, RestReference.class);
      if (ref != null) {
        return ref.resolve();
      }
    }

    return null;
  }
}
