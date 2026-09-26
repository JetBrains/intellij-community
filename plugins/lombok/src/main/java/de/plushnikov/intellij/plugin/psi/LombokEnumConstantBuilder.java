package de.plushnikov.intellij.plugin.psi;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.JavaResolveResult;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiEnumConstantInitializer;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LombokEnumConstantBuilder extends LombokLightFieldBuilder implements PsiEnumConstant {
  public LombokEnumConstantBuilder(@NotNull PsiManager manager, @NotNull String name, @NotNull PsiType type) {
    super(manager, name, type);
  }

  @Override
  public @Nullable PsiExpressionList getArgumentList() {
    return null;
  }

  @Override
  public @Nullable PsiEnumConstantInitializer getInitializingClass() {
    return null;
  }

  @Override
  public @NotNull PsiEnumConstantInitializer getOrCreateInitializingClass() {
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(getProject());
    return factory.createEnumConstantFromText("foo{}", null).getInitializingClass();
  }

  @Override
  public @Nullable PsiMethod resolveMethod() {
    return null;
  }

  @Override
  public @NotNull JavaResolveResult resolveMethodGenerics() {
    return JavaResolveResult.EMPTY;
  }

  @Override
  public @Nullable PsiMethod resolveConstructor() {
    return null;
  }
}
