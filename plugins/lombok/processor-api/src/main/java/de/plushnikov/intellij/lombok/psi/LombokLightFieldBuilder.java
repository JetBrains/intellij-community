package de.plushnikov.intellij.lombok.psi;

import com.intellij.lang.StdLanguages;
import com.intellij.psi.Modifier;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiType;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Plushnikov Michail
 */
public class LombokLightFieldBuilder extends LombokLightVariableBuilder implements PsiField {
  private PsiClass myContainingClass = null;
  private PsiExpression myInitializer = null;
  private PsiDocComment myDocComment = null;
  private boolean myIsDeprecated = false;

  public LombokLightFieldBuilder(@NotNull PsiManager manager, @NotNull String name, @NotNull PsiType type) {
    super(manager, name, type, StdLanguages.JAVA);
  }

  public LombokLightFieldBuilder setContainingClass(PsiClass psiClass) {
    myContainingClass = psiClass;
    return this;
  }

  public LombokLightFieldBuilder addModifier(@Modifier @NotNull @NonNls String modifier) {
    super.addModifier(modifier);
    return this;
  }

  public LombokLightFieldBuilder setDocComment(PsiDocComment docComment) {
    myDocComment = docComment;
    return this;
  }

  public LombokLightFieldBuilder setIsDeprecated(boolean isDeprecated) {
    myIsDeprecated = isDeprecated;
    return this;
  }

  @Override
  public PsiElement copy() {
    return null;
  }

  public String toString() {
    return "LombokLightField: " + getName();
  }

  @Override
  public void setInitializer(@Nullable PsiExpression initializer) throws IncorrectOperationException {
    myInitializer = initializer;
  }

  @Override
  public PsiExpression getInitializer() {
    return myInitializer;
  }

  @Override
  public PsiDocComment getDocComment() {
    return myDocComment;
  }

  @Override
  public boolean isDeprecated() {
    return myIsDeprecated;
  }

  @Override
  public PsiClass getContainingClass() {
    return myContainingClass;
  }
}
