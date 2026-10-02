// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.quickfix;

import com.intellij.lang.ASTNode;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyStringLiteralUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import static com.jetbrains.python.psi.PyUtil.as;

@ApiStatus.Internal
public class RemovePrefixQuickFix extends PsiUpdateModCommandQuickFix {
  private final String myPrefix;

  public RemovePrefixQuickFix(String prefix) {
    myPrefix = prefix;
  }

  @Override
  public @NotNull String getName() {
    return PyPsiBundle.message("QFIX.remove.string.prefix", myPrefix);
  }

  @Override
  public @NotNull String getFamilyName() {
    return PyPsiBundle.message("QFIX.NAME.remove.string.prefix");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    final PyStringLiteralExpression pyString = as(element, PyStringLiteralExpression.class);
    if (pyString != null) {
      final PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
      for (ASTNode node : pyString.getStringNodes()) {
        final String nodeText = node.getText();
        final int prefixLength = PyStringLiteralUtil.getPrefixLength(nodeText);
        if (nodeText.substring(0, prefixLength).equalsIgnoreCase(myPrefix)) {
          final PyStringLiteralExpression replacement =
            elementGenerator.createStringLiteralAlreadyEscaped(nodeText.substring(prefixLength));
          node.getPsi().replace(replacement.getFirstChild());
        }
      }
    }
  }
}
