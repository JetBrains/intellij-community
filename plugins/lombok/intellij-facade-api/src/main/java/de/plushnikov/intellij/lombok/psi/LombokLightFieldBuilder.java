package de.plushnikov.intellij.lombok.psi;

import com.intellij.psi.Modifier;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Plushnikov Michail
 */
public interface LombokLightFieldBuilder extends PsiField {
  LombokLightFieldBuilder setContainingClass(PsiClass psiClass);

  LombokLightFieldBuilder addModifier(@Modifier @NotNull @NonNls String modifier);

  LombokLightFieldBuilder withNavigationElement(PsiElement navigationElement);
}
