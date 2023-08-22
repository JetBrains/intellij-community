// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.lang.xpath.validation;

import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.intellij.lang.xpath.psi.XPathExpression;
import org.intellij.lang.xpath.psi.impl.XPathChangeUtil;
import org.intellij.plugins.xpathView.XPathBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class ExpressionReplacementFix implements IntentionAction {
  private final String myReplacement;
  private final String myDisplay;
  private final XPathExpression myExpr;

  ExpressionReplacementFix(String replacement, XPathExpression expr) {
    this(replacement, replacement, expr);
  }

  ExpressionReplacementFix(String replacement, String display, XPathExpression expression) {
    myReplacement = replacement;
    myDisplay = display;
    myExpr = expression;
  }

  @NotNull
  @Override
  public String getText() {
    return XPathBundle.message("intention.name.replace.with.x", myDisplay);
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return XPathBundle.message("intention.family.name.replace.with.valid.xpath.expression");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myExpr.isValid();
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    myExpr.replace(XPathChangeUtil.createExpression(myExpr, myReplacement));
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  @Override
  public @Nullable FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
    return new ExpressionReplacementFix(myReplacement, myDisplay, PsiTreeUtil.findSameElementInCopy(myExpr, target));
  }
}