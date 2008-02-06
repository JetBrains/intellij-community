package com.intellij.uiDesigner.binding;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.rename.naming.AutomaticRenamer;
import com.intellij.refactoring.rename.naming.AutomaticRenamerFactory;
import com.intellij.usageView.UsageInfo;

import java.util.Collection;

public class FormsRenamerFactory implements AutomaticRenamerFactory {
  public boolean isApplicable(final PsiElement element) {
    if (!(element instanceof PsiClass)) return false;
    String qName = ((PsiClass)element).getQualifiedName();
    PsiFile[] forms = JavaPsiFacade.getInstance(element.getProject()).findFormsBoundToClass(qName);
    return forms.length > 0;
  }

  public String getOptionName() {
    return RefactoringBundle.message("rename.bound.forms");
  }

  public boolean isEnabled() {
    return true;
  }

  public void setEnabled(final boolean enabled) {
  }

  public AutomaticRenamer createRenamer(final PsiElement element, final String newName, final Collection<UsageInfo> usages) {
    return new FormsRenamer((PsiClass) element, newName);
  }
}
