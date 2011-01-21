package com.jetbrains.python.codeInsight;

import com.google.common.collect.Lists;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author traff
 */
public class PySuperAttributesCompletionContributor extends CompletionContributor {
  public PySuperAttributesCompletionContributor() {
    extend(CompletionType.BASIC,
           PlatformPatterns.psiElement().withParent(PyReferenceExpression.class),
           new CompletionProvider<CompletionParameters>() {
             @Override
             protected void addCompletions(@NotNull CompletionParameters parameters,
                                           ProcessingContext context,
                                           @NotNull CompletionResultSet result) {
               PsiElement position = parameters.getOriginalPosition();
               PyClass containingClass = PsiTreeUtil.getParentOfType(position, PyClass.class);
               if (containingClass == null && position instanceof PsiWhiteSpace) {
                 position = PsiTreeUtil.prevLeaf(position);
                 containingClass = PsiTreeUtil.getParentOfType(position, PyClass.class);
               }
               if (containingClass == null) {
                 return;
               }
               PyFunction func = PsiTreeUtil.getParentOfType(position, PyFunction.class);
               if (func != null) {
                 return;
               }
               List<String> seenNames = Lists.newArrayList();
               for (PyTargetExpression expr : containingClass.getClassAttributes()) {
                 seenNames.add(expr.getName());
               }
               for (PyClass ancestor : containingClass.iterateAncestors()) {
                 for (PyTargetExpression expr : ancestor.getClassAttributes()) {
                   if (!seenNames.contains(expr.getName())) {
                     result.addElement(LookupElementBuilder.create(expr, expr.getName() + " = "));
                     seenNames.add(expr.getName());
                   }
                 }
               }
             }
           });
  }
}
