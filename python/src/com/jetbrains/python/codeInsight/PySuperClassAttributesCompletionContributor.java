package com.jetbrains.python.codeInsight;

import com.google.common.collect.Lists;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author traff
 */
public class PySuperClassAttributesCompletionContributor extends CompletionContributor {
  public PySuperClassAttributesCompletionContributor() {
    extend(CompletionType.BASIC,
           PlatformPatterns.psiElement()
             .withParents(PyReferenceExpression.class, PyExpressionStatement.class, PyStatementList.class, PyClass.class),
           new CompletionProvider<CompletionParameters>() {
             @Override
             protected void addCompletions(@NotNull CompletionParameters parameters,
                                           ProcessingContext context,
                                           @NotNull CompletionResultSet result) {
               PsiElement position = parameters.getOriginalPosition();
               PyClass containingClass = PsiTreeUtil.getParentOfType(position, PyClass.class);

               if (containingClass == null) {
                 return;
               }
               for (PyTargetExpression expr : getSuperClassAttributes(containingClass)) {
                 result.addElement(LookupElementBuilder.create(expr, expr.getName() + " = "));
               }
             }
           }
    );
  }

  public static List<PyTargetExpression> getSuperClassAttributes(@NotNull PyClass cls) {
    List<PyTargetExpression> attrs = Lists.newArrayList();
    List<String> seenNames = Lists.newArrayList();
    for (PyTargetExpression expr : cls.getClassAttributes()) {
      seenNames.add(expr.getName());
    }
    for (PyClass ancestor : cls.iterateAncestorClasses()) {
      for (PyTargetExpression expr : ancestor.getClassAttributes()) {
        if (!seenNames.contains(expr.getName())) {
          seenNames.add(expr.getName());
          attrs.add(expr);
        }
      }
    }
    return attrs;
  }
}
