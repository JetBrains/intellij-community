package com.intellij.psi.impl.source.tree.java;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteral;
import com.intellij.psi.PsiAnnotationSupport;
import org.jetbrains.annotations.NotNull;

public class JavaAnnotationSupport implements PsiAnnotationSupport {
  @NotNull
  public PsiLiteral createLiteralValue(@NotNull String value, @NotNull PsiElement context) {
    return (PsiLiteral)JavaPsiFacade.getInstance(context.getProject()).getElementFactory().createExpressionFromText("\"" + StringUtil.escapeStringCharacters(value) + "\"", null);
  }
}
