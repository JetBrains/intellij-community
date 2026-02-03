package de.plushnikov.intellij.plugin.psi;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.augment.PsiExtensionMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LombokDelegateMethod extends LombokLightMethodBuilder implements PsiExtensionMethod {
  private final @NotNull PsiMethod myMethod;

  public LombokDelegateMethod(@NotNull PsiMethod method) {
    super(method.getManager(), method.getName());
    myMethod = method;
  }

  @Override
  public boolean isEquivalentTo(final PsiElement another) { return myMethod.isEquivalentTo(another); }

  @Override
  public @NotNull PsiMethod getTargetMethod() {
    return myMethod;
  }

  @Override
  public @Nullable PsiParameter getTargetReceiverParameter() {
    return null;
  }

  @Override
  public @Nullable PsiParameter getTargetParameter(int index) {
    return null;
  }
}
