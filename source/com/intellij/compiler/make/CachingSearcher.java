package com.intellij.compiler.make;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
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
  private final Map<PsiElement, PsiReference[]> myElementToReferencersMap = new com.intellij.util.containers.HashMap<PsiElement, PsiReference[]>();
  private final PsiSearchHelper mySearchHelper;

  public CachingSearcher(Project project) {
    myProject = project;
    mySearchHelper = PsiManager.getInstance(myProject).getSearchHelper();
  }

  public PsiReference[] findReferences(PsiElement element) {
    PsiReference[] psiReferences = myElementToReferencersMap.get(element);
    if (psiReferences == null) {
      psiReferences = doFindReferences(element);
      myElementToReferencersMap.put(element, psiReferences);
    }
    return psiReferences;
  }

  private PsiReference[] doFindReferences(final PsiElement psiElement) {
    final ProgressManager progressManager = ProgressManager.getInstance();
    final ProgressIndicator currentProgress = progressManager.getProgressIndicator();
    final PsiReference[][] references = new PsiReference[][] {null};

    currentProgress.startNonCancelableSection();
    try {
      references[0] = mySearchHelper.findReferences(psiElement, GlobalSearchScope.projectScope(myProject), false);
    }
    finally {
      currentProgress.finishNonCancelableSection();
    }

    return references[0];
  }


}
