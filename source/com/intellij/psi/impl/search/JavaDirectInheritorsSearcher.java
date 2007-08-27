/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.psi.impl.search;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.RepositoryElementsManager;
import com.intellij.psi.impl.RepositoryPsiElement;
import com.intellij.psi.impl.cache.ClassView;
import com.intellij.psi.impl.cache.RepositoryIndex;
import com.intellij.psi.impl.cache.RepositoryManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiElementProcessor;
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

    final SearchScope useScope = ApplicationManager.getApplication().runReadAction(new Computable<SearchScope>() {
      public SearchScope compute() {
        return aClass.getUseScope();
      }
    });

    String qualifiedName = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      public String compute() {
        return aClass.getQualifiedName();
      }
    });
    if ("java.lang.Object".equals(qualifiedName)) {
      return psiManager.getSearchHelper().processAllClasses(
        new PsiElementProcessor<PsiClass>() {
          public boolean execute(final PsiClass psiClass) {
            return consumer.process(psiClass);
          }
        },
        useScope.intersectWith(
          GlobalSearchScope.notScope(
            GlobalSearchScope.getScopeRestrictedByFileTypes(
              GlobalSearchScope.allScope(psiManager.getProject()),
              StdFileTypes.JSP,
              StdFileTypes.JSPX
            )
          )
        )
      );
    }
    else {
      final RepositoryManager repositoryManager = psiManager.getRepositoryManager();
      final RepositoryElementsManager repositoryElementsManager = psiManager.getRepositoryElementsManager();

      long[] candidateIds = ApplicationManager.getApplication().runReadAction(new Computable<long[]>() {
        public long[] compute() {
          RepositoryIndex repositoryIndex = repositoryManager.getIndex();
          final VirtualFileFilter rootFilter;
          if (useScope instanceof GlobalSearchScope) {
            rootFilter = repositoryIndex.rootFilterBySearchScope((GlobalSearchScope)useScope);
          }
          else {
            rootFilter = null;
          }
          return repositoryIndex.getNameOccurrencesInExtendsLists(aClass.getName(), rootFilter);
        }
      });

      final boolean includeAnonymous = p.includeAnonymous();
      final ClassView classView = repositoryManager.getClassView();

      for (final long candidateId : candidateIds) {
        PsiClass candidate = ApplicationManager.getApplication().runReadAction(new Computable<PsiClass>() {
          public PsiClass compute() {
            if (!includeAnonymous && classView.isAnonymous(candidateId)) return null;

            final RepositoryPsiElement candidate = repositoryElementsManager.findOrCreatePsiElementById(candidateId);
            LOG.assertTrue(candidate.isValid());
            return (PsiClass)candidate;
          }
        });

        if (candidate != null && !consumer.process(candidate)) {
          return false;
        }
      }
    }

    return true;
  }
}
