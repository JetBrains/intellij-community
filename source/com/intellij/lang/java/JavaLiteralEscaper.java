package com.intellij.lang.java;

import com.intellij.lang.LiteralEscaper;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.JavaTokenType;
import com.intellij.openapi.util.text.StringUtil;

/**
 * @author yole
 */
public class JavaLiteralEscaper implements LiteralEscaper {
  public String getEscapedText(final PsiElement context, final String originalText) {
    if (context instanceof PsiJavaToken && ((PsiJavaToken)context).getTokenType() == JavaTokenType.STRING_LITERAL) {
      return StringUtil.escapeStringCharacters(originalText);
    }
    return originalText;
  }
}
