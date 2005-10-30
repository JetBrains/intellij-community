/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.psi.impl.search;

import com.intellij.psi.*;
import com.intellij.psi.search.PsiSearchScopeUtil;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.PsiReferenceSearch;
import com.intellij.util.Processor;

/**
 * @author max
 */
public class ConstructorReferencesSearchHelper {
  private PsiManager myManager;

  public ConstructorReferencesSearchHelper(final PsiManager manager) {
    myManager = manager;
  }

  public boolean processConstructorReferences(final Processor<PsiReference> processor,
                                              final PsiMethod constructor,
                                              final SearchScope searchScope,
                                              boolean ignoreAccessScope,
                                              final boolean isStrictSignatureSearch) {
    PsiClass aClass = constructor.getContainingClass();
    if (aClass == null) return true;

    if (aClass.isEnum()) {
      PsiField[] fields = aClass.getFields();
      for (PsiField field : fields) {
        if (field instanceof PsiEnumConstant) {
          PsiReference reference = field.getReference();
          if (reference != null && reference.isReferenceTo(constructor)) {
            if (!processor.process(reference)) return false;
          }
        }
      }
    }

    // search usages like "new XXX(..)"
    Processor<PsiReference> processor1 = new Processor<PsiReference>() {
      public boolean process(final PsiReference reference) {
        PsiElement parent = reference.getElement().getParent();
        if (parent instanceof PsiAnonymousClass) {
          parent = parent.getParent();
        }
        if (parent instanceof PsiNewExpression) {
          PsiMethod constructor1 = ((PsiNewExpression)parent).resolveConstructor();
          if (constructor1 != null) {
            if (isStrictSignatureSearch) {
              if (myManager.areElementsEquivalent(constructor, constructor1)) {
                return processor.process(reference);
              }
            }
            else {
              if (myManager.areElementsEquivalent(constructor.getContainingClass(), constructor1.getContainingClass())) {
                return processor.process(reference);
              }
            }
          }
        }
        return true;
      }
    };

    if (!PsiReferenceSearch.search(aClass, searchScope, ignoreAccessScope).forEach(processor1)) return false;

    // search usages like "this(..)"
    if (!processConstructorReferencesViaSuperOrThis(processor, aClass, constructor, searchScope, isStrictSignatureSearch, PsiKeyword.THIS)) {
      return false;
    }

    // search usages like "super(..)"
    Processor<PsiClass> processor2 = new Processor<PsiClass>() {
      public boolean process(PsiClass inheritor) {
        return processConstructorReferencesViaSuperOrThis(processor, inheritor, constructor, searchScope, isStrictSignatureSearch, PsiKeyword.SUPER);
      }
    };

    return ClassInheritorsSearch.search(aClass, searchScope, false).forEach(processor2);
  }

  private boolean processConstructorReferencesViaSuperOrThis(final Processor<PsiReference> processor,
                                                             final PsiClass inheritor,
                                                             final PsiMethod constructor,
                                                             final SearchScope searchScope,
                                                             final boolean isStrictSignatureSearch,
                                                             final String superOrThisKeyword) {
    PsiMethod[] methods = inheritor.getMethods();
    for (PsiMethod method : methods) {
      if (method.isConstructor()) {
        PsiCodeBlock body = method.getBody();
        if (body != null) {
          PsiStatement[] statements = body.getStatements();
          if (statements.length > 0) {
            PsiStatement statement = statements[0];
            if (statement instanceof PsiExpressionStatement) {
              PsiExpression expr = ((PsiExpressionStatement)statement).getExpression();
              if (expr instanceof PsiMethodCallExpression) {
                PsiReferenceExpression refExpr = ((PsiMethodCallExpression)expr).getMethodExpression();
                if (PsiSearchScopeUtil.isInScope(searchScope, refExpr)) {
                  if (refExpr.getText().equals(superOrThisKeyword)) {
                    PsiElement referencedElement = refExpr.resolve();
                    if (referencedElement instanceof PsiMethod) {
                      PsiMethod constructor1 = (PsiMethod)referencedElement;
                      if (isStrictSignatureSearch) {
                        if (myManager.areElementsEquivalent(constructor1, constructor)) {
                          if (!processor.process(refExpr)) return false;
                        }
                      }
                      else {
                        if (myManager.areElementsEquivalent(constructor.getContainingClass(),
                                                            constructor1.getContainingClass())) {
                          if (!processor.process(refExpr)) return false;
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
    return true;
  }

}
