package de.plushnikov.intellij.plugin.psi;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LombokExtensionMethod extends LombokDelegateMethod  {
  public LombokExtensionMethod(@NotNull PsiMethod staticMethod) {
    super(staticMethod);
  }

  @Override
  public @Nullable PsiParameter getTargetReceiverParameter() {
    return getTargetMethod().getParameterList().getParameter(0);
  }

  @Override
  public @Nullable PsiParameter getTargetParameter(int index) {
    return getTargetMethod().getParameterList().getParameter(index + 1);
  }
}
