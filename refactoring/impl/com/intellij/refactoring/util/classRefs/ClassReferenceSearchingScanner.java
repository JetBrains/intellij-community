package com.intellij.refactoring.util.classRefs;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;

/**
 * @author dsl
 */
public class ClassReferenceSearchingScanner extends ClassReferenceScanner {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.util.classRefs.ClassReferenceSearchingScanner");
  private PsiSearchHelper mySearchHelper;

  public ClassReferenceSearchingScanner(PsiClass aClass) {
    super(aClass);
    mySearchHelper = myClass.getManager().getSearchHelper();
  }

  public PsiReference[] findReferences() {
    GlobalSearchScope projectScope = GlobalSearchScope.projectScope(myClass.getProject());
    return mySearchHelper.findReferences(myClass, projectScope, false);
  }

}
