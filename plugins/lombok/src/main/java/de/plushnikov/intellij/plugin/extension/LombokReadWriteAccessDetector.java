package de.plushnikov.intellij.plugin.extension;

import com.intellij.codeInsight.highlighting.JavaReadWriteAccessDetector;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceExpression;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import org.jetbrains.annotations.NotNull;

public final class LombokReadWriteAccessDetector extends JavaReadWriteAccessDetector {
  @Override
  public @NotNull Access getExpressionAccess(final @NotNull PsiElement expression) {
    if (expression instanceof PsiReferenceExpression psiRefExpression) {
      final PsiElement actualReferee = psiRefExpression.resolve();
      if (actualReferee instanceof LombokLightMethodBuilder lombokMethodBuilder) {
        return lombokMethodBuilder.hasWriteAccess() ? Access.Write : Access.Read;
      }
    }
    return super.getExpressionAccess(expression);
  }
}
