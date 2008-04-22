/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.psi.impl.search;

import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.java.stubs.index.JavaAnnotationIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.AnnotatedMembersSearch;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;

import java.util.Collection;

/**
 * @author max
 */
public class AnnotatedMembersSearcher implements QueryExecutor<PsiMember, AnnotatedMembersSearch.Parameters> {
  public boolean execute(final AnnotatedMembersSearch.Parameters p, final Processor<PsiMember> consumer) {
    final PsiClass annClass = p.getAnnotationClass();
    assert annClass.isAnnotationType() : "Annotation type should be passed to annotated members search";

    final String annotationFQN = annClass.getQualifiedName();
    assert annotationFQN != null;

    PsiManagerImpl psiManager = (PsiManagerImpl)PsiManager.getInstance(annClass.getProject());

    final SearchScope useScope = p.getScope();

    final GlobalSearchScope scope = useScope instanceof GlobalSearchScope ? (GlobalSearchScope)useScope : null;
    final Collection<PsiAnnotation> annotations = JavaAnnotationIndex.getInstance().get(annClass.getName(), annClass.getProject(), scope);
    for (PsiAnnotation ann : annotations) {
      final PsiJavaCodeReferenceElement ref = ann.getNameReferenceElement();
      if (ref == null) continue;

      PsiModifierList modlist = (PsiModifierList)ann.getParent();
      final PsiElement owner = modlist.getParent();
      if (!(owner instanceof PsiMember)) continue;

      PsiMember candidate = (PsiMember)owner;

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
