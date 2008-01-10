/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.psi.impl.search;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.RepositoryElementsManager;
import com.intellij.psi.impl.cache.RepositoryIndex;
import com.intellij.psi.impl.cache.RepositoryManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.AnnotatedMembersSearch;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;

/**
 * @author max
 */
public class AnnotatedMembersSearcher implements QueryExecutor<PsiMember, AnnotatedMembersSearch.Parameters> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.search.AnnotatedMembersSearcher");

  public boolean execute(final AnnotatedMembersSearch.Parameters p, final Processor<PsiMember> consumer) {
    final PsiClass annClass = p.getAnnotationClass();
    assert annClass.isAnnotationType() : "Annotation type should be passed to annotated members search";

    final String annotationFQN = annClass.getQualifiedName();
    assert annotationFQN != null;

    PsiManagerImpl psiManager = (PsiManagerImpl)PsiManager.getInstance(annClass.getProject());

    RepositoryManager repositoryManager = psiManager.getRepositoryManager();
    RepositoryElementsManager repositoryElementsManager = psiManager.getRepositoryElementsManager();

    RepositoryIndex repositoryIndex = repositoryManager.getIndex();
    final SearchScope useScope = p.getScope();

    final VirtualFileFilter rootFilter;
    if (useScope instanceof GlobalSearchScope) {
      rootFilter = repositoryIndex.rootFilterBySearchScope((GlobalSearchScope)useScope);
    }
    else {
      rootFilter = null;
    }

    long[] candidateIds = repositoryIndex.getAnnotationNameOccurencesInMemberDecls(annClass.getName(), rootFilter);
    for (long candidateId : candidateIds) {
      PsiMember candidate = (PsiMember)repositoryElementsManager.findOrCreatePsiElementById(candidateId);
      LOG.assertTrue(candidate.isValid());

      final PsiAnnotation ann = candidate.getModifierList().findAnnotation(annotationFQN);
      if (ann == null) continue;

      final PsiJavaCodeReferenceElement ref = ann.getNameReferenceElement();
      if (ref == null) continue;

      if (!psiManager.areElementsEquivalent(ref.resolve(), annClass)) continue;
      if (useScope instanceof GlobalSearchScope &&
          !((GlobalSearchScope)useScope).contains(candidate.getContainingFile().getVirtualFile())) {
        continue;
      }
      if (!consumer.process(candidate)) {
        return false;
      }
    }

    return true;
  }
}
