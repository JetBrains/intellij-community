package com.intellij.psi.filters.getters;

import com.intellij.codeInsight.completion.CompletionContext;
import com.intellij.psi.*;
import com.intellij.psi.filters.ClassFilter;
import com.intellij.psi.filters.ContextGetter;
import com.intellij.psi.filters.OrFilter;
import com.intellij.psi.filters.element.ModifierFilter;
import com.intellij.psi.scope.processor.FilterScopeProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.util.PsiUtil;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 15.04.2003
 * Time: 17:07:09
 * To change this template use Options | File Templates.
 */
public class MembersGetter implements ContextGetter{
  private ContextGetter myBaseGetter;

  public MembersGetter(ContextGetter baseGetter){
    myBaseGetter = baseGetter;
  }

  public Object[] get(PsiElement context, CompletionContext completionContext){
    final FilterScopeProcessor processor = new FilterScopeProcessor(new OrFilter(
      new ClassFilter(PsiCompiledElement.class, false),
      new ModifierFilter(PsiModifier.PRIVATE, false)
    ));
    final Object[] elements = myBaseGetter.get(context, completionContext);

    for (final Object element : elements) {
      final PsiClass psiClass;

      if (element instanceof PsiClass) {
        psiClass = (PsiClass)context;
        PsiScopesUtil.processScope(psiClass, processor, PsiSubstitutor.EMPTY, null, context);
      }
      else if (element instanceof PsiType) {
        psiClass = PsiUtil.resolveClassInType((PsiType)element);
        if (psiClass != null) {
          PsiScopesUtil.processScope(psiClass, processor, PsiSubstitutor.EMPTY, null, context);
        }
      }
      else {
        PsiScopesUtil.processScope((PsiElement)element, processor, PsiSubstitutor.EMPTY, null, context);
      }
    }

    final List<PsiElement> results = processor.getResults();
    return results.toArray(new PsiElement[results.size()]);
  }
}
