/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
class PyMagicLiteralUsageTargetProvider implements UsageTargetProvider {
  @Nullable
  @Override
  public UsageTarget[] getTargets(@NotNull final Editor editor, @NotNull final PsiFile file) {
    final PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    if (element != null) {
      final PyStringLiteralExpression literal = PsiTreeUtil.getParentOfType(element, PyStringLiteralExpression.class);
      if ((literal != null) && PyMagicLiteralTools.isMagicLiteral(literal)) {
        return new UsageTarget[]{new PsiElement2UsageTargetAdapter(literal)};
      }
    }
    return UsageTarget.EMPTY_ARRAY;
  }

  @Nullable
  @Override
  public UsageTarget[] getTargets(@NotNull final PsiElement psiElement) {
    return UsageTarget.EMPTY_ARRAY;
  }
}
