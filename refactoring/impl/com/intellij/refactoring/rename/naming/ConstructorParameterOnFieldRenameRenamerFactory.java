package com.intellij.refactoring.rename.naming;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.refactoring.rename.naming.AutomaticRenamer;
import com.intellij.refactoring.rename.naming.AutomaticRenamerFactory;
import com.intellij.refactoring.rename.naming.ConstructorParameterOnFieldRenameRenamer;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class ConstructorParameterOnFieldRenameRenamerFactory implements AutomaticRenamerFactory {
  public boolean isApplicable(final PsiElement element) {
    return element instanceof PsiField;
  }

  @Nullable
  public String getOptionName() {
    return null;
  }

  public boolean isEnabled() {
    return false;
  }

  public void setEnabled(final boolean enabled) {
  }

  public AutomaticRenamer createRenamer(final PsiElement element, final String newName, final Collection<UsageInfo> usages) {
    return new ConstructorParameterOnFieldRenameRenamer((PsiField) element, newName);
  }
}
