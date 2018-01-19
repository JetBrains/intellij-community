// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.intentions;

import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

import static com.jetbrains.python.psi.PyUtil.as;

/**
 * @author Mikhail Golubev
 */
public class PyTypeHintGenerationUtil {
  private PyTypeHintGenerationUtil() {}

  public static void insertVariableAnnotation(@NotNull PyTargetExpression target, @NotNull String annotation) {
    final LanguageLevel langLevel = LanguageLevel.forElement(target);
    if (langLevel.isOlderThan(LanguageLevel.PYTHON36)) {
      throw new IllegalArgumentException("Target '" + target.getText() + "' doesn't belong to Python 3.6+ project: " + langLevel);
    }

    if (canUseInlineAnnotation(target)) {
      PyUtil.updateDocumentUnblockedAndCommitted(target, document -> {
        document.insertString(target.getTextRange().getEndOffset(), ": " + annotation);
      });
    }
    else {
      final PyElementGenerator generator = PyElementGenerator.getInstance(target.getProject());
      final String declarationText = target.getText() + ": " + annotation;
      final PyTypeDeclarationStatement declaration = generator.createFromText(langLevel, PyTypeDeclarationStatement.class, declarationText);
      final PyStatement statement = PsiTreeUtil.getParentOfType(target, PyStatement.class);
      assert statement != null;
      statement.getParent().addBefore(declaration, statement);
    }
  }

  private static boolean canUseInlineAnnotation(@NotNull PyTargetExpression target) {
    final PyAssignmentStatement assignment = as(target.getParent(), PyAssignmentStatement.class);
    return assignment != null && assignment.getRawTargets().length == 1 && assignment.getLeftHandSideExpression() == target;
  }
}
