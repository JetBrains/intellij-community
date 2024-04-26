// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.reStructuredText.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.python.reStructuredText.validation.RestElementVisitor;
import org.jetbrains.annotations.NotNull;

/**
 * User : catherine
 */
public class RestReferenceTarget extends RestElement {
  public RestReferenceTarget(@NotNull final ASTNode node) {
    super(node);
  }

  @Override
  public String toString() {
    return "RestReferenceTarget:" + getNode().getElementType().toString();
  }

  public String getReferenceName(boolean quoted) {
    String text = getNode().getText();
    if ("__".equals(text))
      return text;

    text = text.replaceAll("\\\\([^\\\\]+)", "$1");
    if (text.startsWith("_`") && !quoted)
      return text.substring(2, text.length()-2);

    if (text.startsWith("_"))
      return text.substring(1, text.length()-1);

    if (text.startsWith("[#") && !quoted && text.length()>3)
      return text.substring(2, text.length()-1);

    if (text.startsWith("[") && !quoted)
      return text.substring(1, text.length()-1);

    return text;
  }
  public String getReferenceName() {
    return getReferenceName(true);
  }

  public boolean hasReference() {
    String text = getNode().getText();
    PsiFile file = getContainingFile();
    if ("__".equals(text)) {
      RestReference[] references = PsiTreeUtil.getChildrenOfType(file, RestReference.class);
      if (references != null) {
        for (RestReference ref : references) {
          if (ref.resolve() == this)
            return true;
        }
      }
      return false;
    }
    return true;
  }

  @Override
  protected void acceptRestVisitor(RestElementVisitor visitor) {
    visitor.visitReferenceTarget(this);
  }

}
