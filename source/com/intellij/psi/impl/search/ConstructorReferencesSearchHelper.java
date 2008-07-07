/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.psi.impl.search;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadActionProcessor;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightMemberReference;
import com.intellij.psi.search.PsiSearchScopeUtil;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
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
    final Ref<Boolean> result = new Ref<Boolean>();
    PsiClass aClass = ApplicationManager.getApplication().runReadAction(new Computable<PsiClass>() {
      public PsiClass compute() {
        PsiClass aClass = constructor.getContainingClass();
        if (aClass == null) {
          result.set(true);
          return null;
        }

        if (aClass.isEnum()) {
          PsiField[] fields = aClass.getFields();
          for (PsiField field : fields) {
            if (field instanceof PsiEnumConstant) {
              PsiReference reference = field.getReference();
              if (reference != null && reference.isReferenceTo(constructor)) {
                if (!processor.process(reference)) {
                  result.set(false);
                  return null;
                }
              }
            }
          }
        }
        return aClass;
      }
    });
    if (!result.isNull()) return result.get();

    // search usages like "new XXX(..)"
    Processor<PsiReference> processor1 = new ReadActionProcessor<PsiReference>() {
      public boolean processInReadAction(final PsiReference reference) {
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

    if (!ReferencesSearch.search(aClass, searchScope, ignoreAccessScope).forEach(processor1)) return false;

    final boolean constructorCanBeCalledImplicitly = constructor.getParameterList().getParametersCount() == 0;
    // search usages like "this(..)"
    if (!processSuperOrThis(processor, aClass, constructor, constructorCanBeCalledImplicitly, searchScope, isStrictSignatureSearch,
                                                    PsiKeyword.THIS)) {
      return false;
    }

    // search usages like "super(..)"
    Processor<PsiClass> processor2 = new Processor<PsiClass>() {
      public boolean process(PsiClass inheritor) {
        return processSuperOrThis(processor, (PsiClass)inheritor.getNavigationElement(), constructor, constructorCanBeCalledImplicitly, searchScope, isStrictSignatureSearch,
                                                          PsiKeyword.SUPER);
      }
    };

    return ClassInheritorsSearch.search(aClass, searchScope, false).forEach(processor2);
  }

  private boolean processSuperOrThis(final Processor<PsiReference> processor,
                                                             final PsiClass inheritor,
                                                             final PsiMethod constructor, final boolean constructorCanBeCalledImplicitly, final SearchScope searchScope,
                                                             final boolean isStrictSignatureSearch,
                                                             final String superOrThisKeyword) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      public Boolean compute() {
        return processSuperOrThisInReadAction(inheritor, searchScope, superOrThisKeyword, isStrictSignatureSearch, constructor,
                                   constructorCanBeCalledImplicitly, processor);
      }
    });
  }

  private boolean processSuperOrThisInReadAction(final PsiClass inheritor,
                                      final SearchScope searchScope,
                                      final String superOrThisKeyword,
                                      final boolean isStrictSignatureSearch,
                                      final PsiMethod constructor,
                                      final boolean constructorCanBeCalledImplicitly,
                                      final Processor<PsiReference> processor) {
    PsiMethod[] constructors = inheritor.getConstructors();
    if (constructors.length == 0 && constructorCanBeCalledImplicitly) {
      processImplicitConstructorCall(inheritor, processor, constructor, inheritor);
    }
    for (PsiMethod method : constructors) {
      PsiCodeBlock body = method.getBody();
      if (body == null) {
        continue;
      }
      PsiStatement[] statements = body.getStatements();
      if (statements.length != 0) {
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
                  boolean match = isStrictSignatureSearch
                                  ? myManager.areElementsEquivalent(constructor1, constructor)
                                  : myManager.areElementsEquivalent(constructor.getContainingClass(), constructor1.getContainingClass());
                  if (match && !processor.process(refExpr)) return false;
                }
                //as long as we've encountered super/this keyword, no implicit ctr calls are possible here
                continue;
              }
            }
          }
        }
      }
      if (constructorCanBeCalledImplicitly) {
        processImplicitConstructorCall(method, processor, constructor, inheritor);
      }
    }

    return true;
  }

  private void processImplicitConstructorCall(final PsiMember usage,
                                              final Processor<PsiReference> processor,
                                              final PsiMethod constructor,
                                              final PsiClass containingClass) {
    if (containingClass instanceof PsiAnonymousClass) return;
    PsiClass superClass = containingClass.getSuperClass();
    if (myManager.areElementsEquivalent(constructor.getContainingClass(), superClass)) {
      processor.process(new LightMemberReference(myManager, usage, PsiSubstitutor.EMPTY) {
        public PsiElement getElement() {
          return usage;
        }

        public TextRange getRangeInElement() {
          if (usage instanceof PsiClass) {
            PsiIdentifier identifier = ((PsiClass)usage).getNameIdentifier();
            if (identifier != null) return TextRange.from(identifier.getStartOffsetInParent(), identifier.getTextLength());
          }
          else if (usage instanceof PsiField) {
            PsiIdentifier identifier = ((PsiField)usage).getNameIdentifier();
            return TextRange.from(identifier.getStartOffsetInParent(), identifier.getTextLength());
          }
          else if (usage instanceof PsiMethod) {
            PsiIdentifier identifier = ((PsiMethod)usage).getNameIdentifier();
            if (identifier != null) return TextRange.from(identifier.getStartOffsetInParent(), identifier.getTextLength());
          }
          return super.getRangeInElement();
        }
      });
    }
  }
}
