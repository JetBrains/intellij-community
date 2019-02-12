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

  @Nullable
  @Override
  public PsiExpressionList getArgumentList() {
    return null;
  }

  @Nullable
  @Override
  public PsiEnumConstantInitializer getInitializingClass() {
    return null;
  }

  @NotNull
  @Override
  public PsiEnumConstantInitializer getOrCreateInitializingClass() {
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(getProject());
    return factory.createEnumConstantFromText("foo{}", null).getInitializingClass();
  }

  @Nullable
  @Override
  public PsiMethod resolveMethod() {
    return null;
  }

  @NotNull
  @Override
  public JavaResolveResult resolveMethodGenerics() {
    return JavaResolveResult.EMPTY;
  }

  @Nullable
  @Override
  public PsiMethod resolveConstructor() {
    return null;
  }
}
