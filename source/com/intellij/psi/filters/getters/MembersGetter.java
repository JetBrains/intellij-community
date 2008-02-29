package com.intellij.psi.filters.getters;

import com.intellij.codeInsight.completion.CompletionContext;
import com.intellij.psi.*;
import com.intellij.psi.filters.*;
import com.intellij.psi.filters.types.AssignableFromFilter;
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
    final PsiAnnotation annotation = PsiTreeUtil.getParentOfType(context, PsiAnnotation.class);
    final List<PsiElement> results = new ArrayList<PsiElement>();
    for (final Object element : elements) {
      final PsiClass psiClass;
      final ElementFilter filter;
      if (element instanceof PsiClass) {
        psiClass = (PsiClass)context;
        psiClass.processDeclarations(processor, ResolveState.initial(), null, context);
        filter = new AssignableFromFilter(JavaPsiFacade.getInstance(psiClass.getProject()).getElementFactory().createType(psiClass, PsiSubstitutor.EMPTY));
      }
      else if (element instanceof PsiType) {
        final PsiType type = (PsiType)element;
        psiClass = PsiUtil.resolveClassInType(type);
        if (psiClass != null) {
          psiClass.processDeclarations(processor, ResolveState.initial(), null, context);
        }
        filter = new AssignableFromFilter(type);
      }
      else {
        ((PsiElement)element).processDeclarations(processor, ResolveState.initial(), null, context);
        filter = TrueFilter.INSTANCE;
      }

      for (final PsiElement result : processor.getResults()) {
        if (result instanceof PsiMember) {
          final PsiMember member = (PsiMember)result;
          if (member.hasModifierProperty(PsiModifier.STATIC) && !PsiTreeUtil.isAncestor(member.getContainingClass(), context, false)) {
            if (result instanceof PsiField && !member.hasModifierProperty(PsiModifier.FINAL)) continue;
            if (result instanceof PsiMethod && annotation != null) continue;
            if (filter.isAcceptable(result, context)) {
              results.add(result);
            }
          }
        }
      }
      processor.getResults().clear();
    }


    return results.toArray(new PsiElement[results.size()]);
  }
}
