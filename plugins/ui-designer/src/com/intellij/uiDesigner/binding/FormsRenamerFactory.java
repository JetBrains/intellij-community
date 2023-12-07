// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.binding;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.rename.naming.AutomaticRenamer;
import com.intellij.refactoring.rename.naming.AutomaticRenamerFactory;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public class FormsRenamerFactory implements AutomaticRenamerFactory {
  @Override
  public boolean isApplicable(final @NotNull PsiElement element) {
    if (!(element instanceof PsiClass)) return false;
    List<PsiFile> forms = FormClassIndex.findFormsBoundToClass(element.getProject(), (PsiClass)element);
    return forms.size() > 0;
  }

  @Override
  public String getOptionName() {
    return RefactoringBundle.message("rename.bound.forms");
  }

  @Override
  public boolean isEnabled() {
    return true;
  }

  @Override
  public void setEnabled(final boolean enabled) {
  }

  @Override
  public @NotNull AutomaticRenamer createRenamer(final PsiElement element, final String newName, final Collection<UsageInfo> usages) {
    return new FormsRenamer((PsiClass) element, newName);
  }
}
