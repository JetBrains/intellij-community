package com.intellij.refactoring.rename.naming;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.usageView.UsageInfo;

import java.util.Collection;

/**
 * @author yole
 */
public class AutomaticInheritorRenamerFactory implements AutomaticRenamerFactory {
  public boolean isApplicable(final PsiElement element) {
    return element instanceof PsiClass;
  }

  public String getOptionName() {
    return RefactoringBundle.message("rename.inheritors");
  }

  public boolean isEnabled() {
    return JavaRefactoringSettings.getInstance().isToRenameInheritors();
  }

  public void setEnabled(final boolean enabled) {
    JavaRefactoringSettings.getInstance().setRenameInheritors(enabled);
  }

  public AutomaticRenamer createRenamer(final PsiElement element, final String newName, final Collection<UsageInfo> usages) {
    return new InheritorRenamer((PsiClass)element, newName);
  }
}
