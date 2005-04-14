package com.intellij.refactoring.rename.naming;

import com.intellij.psi.PsiClass;

/**
 * @author dsl
 */
public class InheritorRenamer extends AutomaticRenamer {
  public InheritorRenamer(PsiClass aClass, String newClassName) {
    final PsiClass[] inheritors = aClass.getManager().getSearchHelper().findInheritors(aClass, aClass.getUseScope(), true);
    for (int i = 0; i < inheritors.length; i++) {
      final PsiClass inheritor = inheritors[i];
      if (inheritor.getName() != null) {
        myElements.add(inheritor);
      }
    }
    
    suggestAllNames(aClass.getName(), newClassName);
  }

  public String getDialogTitle() {
    return "Rename Inheritors";
  }

  public String getDialogDescription() {
    return "Rename inheritors with the following names to:";
  }

  public String entityName() {
    return "Inheritor";
  }
}
