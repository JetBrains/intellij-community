package com.jetbrains.rest;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandlerBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.rest.psi.RestReference;

/**
 * User : catherine
 */
public class RestGotoProvider extends GotoDeclarationHandlerBase {

  public PsiElement getGotoDeclarationTarget(PsiElement source, Editor editor) {
    if (source != null && source.getLanguage() instanceof RestLanguage) {
      RestReference ref = PsiTreeUtil.getParentOfType(source, RestReference.class);
      if (ref != null) {
        return ref.resolve();
      }
    }
    return null;
  }
}
