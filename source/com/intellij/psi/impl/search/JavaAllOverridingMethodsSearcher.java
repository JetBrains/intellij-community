package com.intellij.psi.impl.search;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.AllOverridingMethodsSearch;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * @author ven
 */
public class JavaAllOverridingMethodsSearcher implements QueryExecutor<Pair<PsiMethod, PsiMethod>, AllOverridingMethodsSearch.SearchParameters> {
  public boolean execute(final AllOverridingMethodsSearch.SearchParameters p, final Processor<Pair<PsiMethod, PsiMethod>> consumer) {
    final PsiClass psiClass = p.getPsiClass();
    final List<PsiMethod> methods = new ArrayList<PsiMethod>(Arrays.asList(psiClass.getMethods()));
    for (Iterator<PsiMethod> it = methods.iterator(); it.hasNext();) {
      PsiMethod method = it.next();
      if (!PsiUtil.canBeOverriden(method)) it.remove();
    }

    final SearchScope scope = p.getScope();

    Processor<PsiClass> inheritorsProcessor = new Processor<PsiClass>() {
      public boolean process(PsiClass inheritor) {
        //could be null if not java inheritor, TODO only JavaClassInheritors are needed
        PsiSubstitutor substitutor = TypeConversionUtil.getClassSubstitutor(psiClass, inheritor,
                                                                            PsiSubstitutor.EMPTY);
        if (substitutor == null) return true;

        for (PsiMethod method : methods) {
          if (method.hasModifierProperty(PsiModifier.PACKAGE_LOCAL) &&
              !inheritor.getManager().arePackagesTheSame(psiClass, inheritor)) continue;
          
          MethodSignature signature = method.getSignature(substitutor);
          PsiMethod inInheritor = MethodSignatureUtil.findMethodBySuperSignature(inheritor, signature, false);
          if (inInheritor == null || inInheritor.hasModifierProperty(PsiModifier.STATIC)) {
            continue;
          }
          if (!consumer.process(new Pair<PsiMethod, PsiMethod>(method, inInheritor))) return false;
        }

        return true;
      }
    };

    return ClassInheritorsSearch.search(psiClass, scope, true).forEach(inheritorsProcessor);
  }
}
