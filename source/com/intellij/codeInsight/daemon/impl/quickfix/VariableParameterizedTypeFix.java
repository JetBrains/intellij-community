package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.psi.*;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.codeInsight.daemon.impl.analysis.GenericsHighlightUtil;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;

import java.util.HashMap;

public class VariableParameterizedTypeFix {
  public static void registerIntentions(HighlightInfo highlightInfo, PsiVariable variable, PsiJavaCodeReferenceElement referenceElement) {
    final PsiType type = variable.getType();
    if (!(type instanceof PsiClassType)) return;

    final String shortName = ((PsiClassType)type).getClassName();
    final PsiManager manager = referenceElement.getManager();
    final PsiShortNamesCache shortNamesCache = manager.getShortNamesCache();
    final PsiClass[] classes = shortNamesCache.getClassesByName(shortName, GlobalSearchScope.allScope(manager.getProject()));
    final PsiElementFactory factory = manager.getElementFactory();
    for (int i = 0; i < classes.length; i++) {
      PsiClass aClass = classes[i];
      if (GenericsHighlightUtil.checkReferenceTypeParametersList(aClass, referenceElement, PsiSubstitutor.EMPTY, false) == null) {
        final PsiType[] actualTypeParameters = referenceElement.getTypeParameters();
        final PsiTypeParameter[] classTypeParameters = aClass.getTypeParameters();
        final HashMap<PsiTypeParameter, PsiType> map = new HashMap<PsiTypeParameter, PsiType>();
        for (int j = 0; j < classTypeParameters.length; j++) {
          PsiTypeParameter classTypeParameter = classTypeParameters[j];
          PsiType actualTypeParameter = actualTypeParameters[j];
          map.put(classTypeParameter, actualTypeParameter);
        }
        final PsiSubstitutor substitutor = factory.createSubstitutor(map);
        final PsiType suggestedType = factory.createType(aClass,substitutor);
        QuickFixAction.registerQuickFixAction(highlightInfo, new VariableTypeFix(variable, suggestedType));
      }
    }
  }
}
