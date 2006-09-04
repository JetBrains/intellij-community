/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.psi.impl.search;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.RepositoryElementsManager;
import com.intellij.psi.impl.cache.RepositoryIndex;
import com.intellij.psi.impl.cache.RepositoryManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;

/**
 * @author max
 */
public class JavaDirectInheritorsSearcher implements QueryExecutor<PsiClass, DirectClassInheritorsSearch.SearchParameters> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.search.JavaDirectInheritorsSearcher");

  public boolean execute(final DirectClassInheritorsSearch.SearchParameters p, final Processor<PsiClass> consumer) {
    final PsiClass aClass = p.getClassToProcess();
    PsiManagerImpl psiManager = (PsiManagerImpl)PsiManager.getInstance(aClass.getProject());

    RepositoryManager repositoryManager = psiManager.getRepositoryManager();
    RepositoryElementsManager repositoryElementsManager = psiManager.getRepositoryElementsManager();

    RepositoryIndex repositoryIndex = repositoryManager.getIndex();
    final SearchScope useScope = aClass.getUseScope();

    final VirtualFileFilter rootFilter;
    if (useScope instanceof GlobalSearchScope) {
      rootFilter = repositoryIndex.rootFilterBySearchScope((GlobalSearchScope)useScope);
    }
    else {
      rootFilter = null;
    }

    if ("java.lang.Object".equals(aClass.getQualifiedName())) {
      for (PsiClass psiClass : psiManager.getSearchHelper().findAllClasses(useScope)) {
        PsiReferenceList extendsList = psiClass.getExtendsList();
        if (extendsList != null && extendsList.getReferencedTypes().length == 0) {
          consumer.process(psiClass);
        }
      }
    }
    else {
      long[] candidateIds = repositoryIndex.getNameOccurrencesInExtendsLists(aClass.getName(), rootFilter);
      for (long candidateId : candidateIds) {
        PsiClass candidate = (PsiClass)repositoryElementsManager.findOrCreatePsiElementById(candidateId);
        LOG.assertTrue(candidate.isValid());
        if (!consumer.process(candidate)) {
          return false;
        }
      }
    }

    return true;
  }
}
