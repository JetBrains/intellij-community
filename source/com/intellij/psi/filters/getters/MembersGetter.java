package com.intellij.psi.filters.getters;

import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.JavaAwareCompletionData;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupItemUtil;
import com.intellij.psi.*;
import com.intellij.psi.filters.ClassFilter;
import com.intellij.psi.filters.OrFilter;
import com.intellij.psi.filters.element.ModifierFilter;
import com.intellij.psi.scope.processor.FilterScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 15.04.2003
 * Time: 17:07:09
 * To change this template use Options | File Templates.
 */
public class MembersGetter {

  public static void addMembers(PsiElement context, PsiType expectedType, CompletionResultSet results) {
    final FilterScopeProcessor<PsiElement> processor = new FilterScopeProcessor<PsiElement>(new OrFilter(
      new ClassFilter(PsiCompiledElement.class, false),
      new ModifierFilter(PsiModifier.PRIVATE, false)
    ));
    final PsiAnnotation annotation = PsiTreeUtil.getParentOfType(context, PsiAnnotation.class);
    final PsiClass psiClass = PsiUtil.resolveClassInType(expectedType);
    if (psiClass != null) {
      psiClass.processDeclarations(processor, ResolveState.initial(), null, context);
    }

    for (final PsiElement result : processor.getResults()) {
      if (result instanceof PsiMember && !(result instanceof PsiClass)) {
        final PsiMember member = (PsiMember)result;
        if (member.hasModifierProperty(PsiModifier.STATIC) && !PsiTreeUtil.isAncestor(member.getContainingClass(), context, false)) {
          if (result instanceof PsiField && !member.hasModifierProperty(PsiModifier.FINAL)) continue;
          if (result instanceof PsiMethod && annotation != null) continue;
          final LookupItem item = LookupItemUtil.objectToLookupItem(result);
          JavaAwareCompletionData.qualify(item);
          results.addElement(item);
        }
      }
    }
  }
}
