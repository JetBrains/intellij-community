package com.intellij.refactoring.rename.naming;

import com.intellij.psi.PsiClass;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.refactoring.RefactoringBundle;

/**
 * @author dsl
 */
public class InheritorRenamer extends AutomaticRenamer {
  public InheritorRenamer(PsiClass aClass, String newClassName) {
    for (final PsiClass inheritor : ClassInheritorsSearch.search(aClass, aClass.getUseScope(), true).findAll()) {
      if (inheritor.getName() != null) {
        myElements.add(inheritor);
      }
    }

    suggestAllNames(aClass.getName(), newClassName);
  }

  public String getDialogTitle() {
    return RefactoringBundle.message("rename.inheritors.title");
  }

  public String getDialogDescription() {
    return RefactoringBundle.message("rename.inheritors.with.the.following.names.to");
  }

  public String entityName() {
    return RefactoringBundle.message("entity.name.inheritor");
  }
}
