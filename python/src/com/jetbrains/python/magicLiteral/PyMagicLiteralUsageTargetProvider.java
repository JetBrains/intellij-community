// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.magicLiteral;

import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageTargetProvider;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Supports usage info for magic literals.
 * <p/>
 * <strong>Install it</strong> as "usageTargetProvider" !
 *
 * @author Ilya.Kazakevich
 */
final class PyMagicLiteralUsageTargetProvider implements UsageTargetProvider {
  @Override
  public UsageTarget @Nullable [] getTargets(@NotNull final Editor editor, @NotNull final PsiFile file) {
    final PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    if (element != null) {
      final PyStringLiteralExpression literal = PsiTreeUtil.getParentOfType(element, PyStringLiteralExpression.class);
      if ((literal != null)) {
        return new UsageTarget[]{new PsiElement2UsageTargetAdapter(literal)};
      }
    }
    return UsageTarget.EMPTY_ARRAY;
  }
}
