package com.intellij.refactoring.rename.naming;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.usageView.UsageInfo;

import java.util.Collection;

/**
 * @author yole
 */
public class AutomaticVariableRenamerFactory implements AutomaticRenamerFactory {
  public boolean isApplicable(final PsiElement element) {
    return element instanceof PsiClass;
  }

  public String getOptionName() {
    return RefactoringBundle.message("rename.variables");
  }

  public boolean isEnabled() {
    return JavaRefactoringSettings.getInstance().isToRenameVariables();
  }

  public void setEnabled(final boolean enabled) {
    JavaRefactoringSettings.getInstance().setRenameVariables(enabled);
  }

  public AutomaticRenamer createRenamer(final PsiElement element, final String newName, final Collection<UsageInfo> usages) {
    return new AutomaticVariableRenamer((PsiClass)element, newName, usages);
  }
}
