/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.psi.impl.search;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.java.stubs.index.JavaAnnotationIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.AnnotatedMembersSearch;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import com.intellij.util.indexing.FileBasedIndex;

import java.util.Collection;

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

    final SearchScope useScope = p.getScope();

    final GlobalSearchScope scope = useScope instanceof GlobalSearchScope ? (GlobalSearchScope)useScope : null;
    final Collection<PsiAnnotation> annotations = JavaAnnotationIndex.getInstance().get(annClass.getName(), annClass.getProject(), scope);
    for (PsiElement elt : annotations) {
      if (!notAnnotation(elt)) continue;

      PsiAnnotation ann = (PsiAnnotation)elt;
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

  private static boolean notAnnotation(final PsiElement found) {
    if (found instanceof PsiAnnotation) return false;

    VirtualFile faultyContainer = PsiUtil.getVirtualFile(found);
    LOG.error("Non annotation in annotations list: " + faultyContainer);
    if (faultyContainer != null && faultyContainer.isValid()) {
      FileBasedIndex.getInstance().requestReindex(faultyContainer);
    }

    return true;
  }
}
