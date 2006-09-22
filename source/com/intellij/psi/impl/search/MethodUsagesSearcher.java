/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.psi.impl.search;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.*;
import com.intellij.psi.search.*;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;

/**
 * @author max
 */
public class MethodUsagesSearcher implements QueryExecutor<PsiReference, MethodReferencesSearch.SearchParameters> {
  public boolean execute(final MethodReferencesSearch.SearchParameters p, final Processor<PsiReference> consumer) {
    PsiMethod method = p.getMethod();
    SearchScope searchScope = p.getScope();
    PsiManager psiManager = PsiManager.getInstance(method.getProject());
    final boolean isStrictSignatureSearch = p.isStrictSignatureSearch();

    if (method.isConstructor()) {
      final ConstructorReferencesSearchHelper helper = new ConstructorReferencesSearchHelper(psiManager);
      if (!helper. processConstructorReferences(consumer, method, searchScope, !isStrictSignatureSearch, isStrictSignatureSearch)) {
        return false;
      }
    }

    PsiClass parentClass = method.getContainingClass();
    if (isStrictSignatureSearch && (parentClass == null
                                    || parentClass instanceof PsiAnonymousClass
                                    || parentClass.hasModifierProperty(PsiModifier.FINAL)
                                    || method.hasModifierProperty(PsiModifier.STATIC)
                                    || method.hasModifierProperty(PsiModifier.FINAL)
                                    || method.hasModifierProperty(PsiModifier.PRIVATE))
      ) {
      return ReferencesSearch.search(method, searchScope, false).forEach(consumer);
    }

    final String text = method.getName();
    final PsiMethod[] methods = isStrictSignatureSearch ? new PsiMethod[]{method} : getOverloads(method);
    
    SearchScope accessScope = methods[0].getUseScope();
    for (int i = 1; i < methods.length; i++) {
      PsiMethod method1 = methods[i];
      SearchScope someScope = PsiSearchScopeUtil.scopesUnion(accessScope, method1.getUseScope());
      accessScope = someScope == null ? accessScope : someScope;
    }

    final PsiClass aClass = method.getContainingClass();

    final TextOccurenceProcessor processor1 = new TextOccurenceProcessor() {
      public boolean execute(PsiElement element, int offsetInElement) {
        final PsiReference[] refs = element.getReferences();
        for (PsiReference ref : refs) {
          if (ref.getRangeInElement().contains(offsetInElement)) {
            for (PsiMethod method : methods) {
              if (ref.isReferenceTo(method)) {
                return consumer.process(ref);
              }
              PsiElement refElement = ref.resolve();

              if (refElement instanceof PsiMethod) {
                PsiMethod refMethod = (PsiMethod)refElement;
                PsiClass refMethodClass = refMethod.getContainingClass();
                if (refMethodClass == null) return true;

                if (!refMethod.hasModifierProperty(PsiModifier.STATIC)) {
                  PsiSubstitutor substitutor = TypeConversionUtil.getClassSubstitutor(aClass, refMethodClass, PsiSubstitutor.EMPTY);
                  if (substitutor != null) {
                    if (refMethod.getSignature(PsiSubstitutor.EMPTY).equals(method.getSignature(substitutor))) {
                      if (!consumer.process(ref)) return false;
                    }
                  }
                }

                if (!isStrictSignatureSearch) {
                  PsiManager manager = method.getManager();
                  if (manager.areElementsEquivalent(refMethodClass, aClass)) {
                    if (!consumer.process(ref)) return false;
                  }
                }
              }
            }
          }
        }

        return true;
      }
    };

    searchScope = searchScope.intersectWith(accessScope);

    short searchContext = UsageSearchContext.IN_CODE | UsageSearchContext.IN_COMMENTS | UsageSearchContext.IN_FOREIGN_LANGUAGES;
    boolean toContinue = psiManager.getSearchHelper().processElementsWithWord(processor1,
                                                                              searchScope,
                                                                              text,
                                                                              searchContext, true);
    if (!toContinue) return false;

    if (PropertyUtil.isSimplePropertyAccessor(method)) {
      final String propertyName = PropertyUtil.getPropertyName(method);

      //if (psiManager.getNameHelper().isIdentifier(propertyName)) {
      if (searchScope instanceof GlobalSearchScope) {
        searchScope = GlobalSearchScope.getScopeRestrictedByFileTypes(
          (GlobalSearchScope)searchScope,
          StdFileTypes.JSP,
          StdFileTypes.JSPX,
          StdFileTypes.XML
        );
      }
      toContinue = psiManager.getSearchHelper().processElementsWithWord(processor1,
                                                                        searchScope,
                                                                        propertyName,
                                                                        UsageSearchContext.IN_FOREIGN_LANGUAGES, true);
      if (!toContinue) return false;
      //}
    }

    return true;
  }

  private static PsiMethod[] getOverloads(PsiMethod method) {
    PsiClass aClass = method.getContainingClass();
    if (aClass == null) return new PsiMethod[]{method};
    return aClass.findMethodsByName(method.getName(), false);
  }
}
