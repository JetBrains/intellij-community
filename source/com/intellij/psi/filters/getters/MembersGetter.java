package com.intellij.psi.filters.getters;

import com.intellij.codeInsight.completion.CompletionContext;
import com.intellij.psi.*;
import com.intellij.psi.filters.ClassFilter;
import com.intellij.psi.filters.ContextGetter;
import com.intellij.psi.filters.OrFilter;
import com.intellij.psi.filters.element.ModifierFilter;
import com.intellij.psi.scope.processor.FilterScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;

import java.util.ArrayList;
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
    final FilterScopeProcessor<PsiElement> processor = new FilterScopeProcessor<PsiElement>(new OrFilter(
      new ClassFilter(PsiCompiledElement.class, false),
      new ModifierFilter(PsiModifier.PRIVATE, false)
    ));
    final Object[] elements = myBaseGetter.get(context, completionContext);

    final List<PsiElement> results = new ArrayList<PsiElement>();
    for (final Object element : elements) {
      final PsiClass psiClass;

      if (element instanceof PsiClass) {
        psiClass = (PsiClass)context;
        psiClass.processDeclarations(processor, ResolveState.initial(), null, context);
      }
      else if (element instanceof PsiType) {
        psiClass = PsiUtil.resolveClassInType((PsiType)element);
        if (psiClass != null) {
          psiClass.processDeclarations(processor, ResolveState.initial(), null, context);
        }
      }
      else {
        ((PsiElement)element).processDeclarations(processor, ResolveState.initial(), null, context);
      }
      
      for (final PsiElement result : processor.getResults()) {
        if (result instanceof PsiMember && !PsiTreeUtil.isAncestor(((PsiMember)result).getContainingClass(), context, false)) {
          if (result instanceof PsiField && !((PsiField)result).hasModifierProperty(PsiModifier.FINAL)) continue;
          if (result instanceof PsiMethod && PsiTreeUtil.getParentOfType(context, PsiAnnotation.class) != null) continue;
          results.add(result);
        }
      }
      processor.getResults().clear();
    }


    return results.toArray(new PsiElement[results.size()]);
  }
}
