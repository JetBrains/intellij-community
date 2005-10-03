package com.intellij.compiler.make;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;

import java.util.Map;

/**
 * @author Eugene Zhuravlev
 *         Date: Oct 15
 * @author 2003
 */
public class CachingSearcher {
  private final Project myProject;
  private final Map<Pair<PsiElement, Boolean>, PsiReference[]> myElementToReferencersMap = new com.intellij.util.containers.HashMap<Pair<PsiElement, Boolean>, PsiReference[]>();
  private final PsiSearchHelper mySearchHelper;

  public CachingSearcher(Project project) {
    myProject = project;
    mySearchHelper = PsiManager.getInstance(myProject).getSearchHelper();
  }

  public PsiReference[] findReferences(PsiElement element, final boolean ignoreAccessScope) {
    final Pair<PsiElement, Boolean> key = new Pair<PsiElement, Boolean>(element, ignoreAccessScope? Boolean.TRUE : Boolean.FALSE);
    PsiReference[] psiReferences = myElementToReferencersMap.get(key);
    if (psiReferences == null) {
      psiReferences = mySearchHelper.findReferences(element, GlobalSearchScope.projectScope(myProject), ignoreAccessScope);
      myElementToReferencersMap.put(key, psiReferences);
    }
    return psiReferences;
  }

}
