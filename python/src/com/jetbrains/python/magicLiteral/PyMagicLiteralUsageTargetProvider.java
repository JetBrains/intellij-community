package com.jetbrains.python.magicLiteral;

import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageTargetProvider;
import com.jetbrains.python.psi.PyStringLiteralExpression;
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
  public UsageTarget[] getTargets(final Editor editor, final PsiFile file) {
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
  public UsageTarget[] getTargets(final PsiElement psiElement) {
    return UsageTarget.EMPTY_ARRAY;
  }
}
