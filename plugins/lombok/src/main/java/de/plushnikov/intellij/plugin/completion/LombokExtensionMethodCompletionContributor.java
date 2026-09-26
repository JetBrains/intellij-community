// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package de.plushnikov.intellij.plugin.completion;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.JavaCompletionContributor;
import com.intellij.codeInsight.completion.JavaCompletionSorting;
import com.intellij.codeInsight.completion.JavaSmartCompletionContributor;
import com.intellij.codeInsight.completion.JavaMethodCallElement;
import com.intellij.codeInsight.completion.ReferenceExpressionCompletionContributor;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.openapi.project.DumbService;
import com.intellij.patterns.PsiJavaPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodReferenceExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.augment.PsiExtensionMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import de.plushnikov.intellij.plugin.processor.method.ExtensionMethodsHelper;
import de.plushnikov.intellij.plugin.util.LombokLibraryUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

import static com.intellij.codeInsight.completion.CompletionType.BASIC;
import static com.intellij.codeInsight.completion.CompletionType.SMART;

/// Completion contributor providing Lombok extension methods.
/// [Lombok's Extension methods documentation](https://projectlombok.org/features/experimental/ExtensionMethod)
public final class LombokExtensionMethodCompletionContributor extends CompletionContributor {
  public LombokExtensionMethodCompletionContributor() {
    extend(null,
           PsiJavaPatterns.psiElement().inside(PsiReferenceExpression.class),
           new CompletionProvider<>() {
             @Override
             protected void addCompletions(@NotNull CompletionParameters parameters,
                                           @NotNull ProcessingContext context,
                                           @NotNull CompletionResultSet resultSet) {
               PsiElement position = parameters.getPosition();
               var psiReferenceExpression = PsiTreeUtil.getParentOfType(position, PsiReferenceExpression.class, false);
               if (psiReferenceExpression == null ||
                   psiReferenceExpression instanceof PsiMethodReferenceExpression ||
                   psiReferenceExpression.getQualifierExpression() == null ||
                   DumbService.isDumb(position.getProject()) ||
                   !LombokLibraryUtil.hasLombokLibrary(position.getProject())) {
                 return;
               }

               CompletionResultSet sortedResult = JavaCompletionSorting.addJavaSorting(parameters, resultSet);
               List<PsiExtensionMethod> methods = ExtensionMethodsHelper.getExtensionMethodsForCompletion(
                 psiReferenceExpression,
                 sortedResult.getPrefixMatcher()::prefixMatches);

               if (!methods.isEmpty()) {
                 if (parameters.getCompletionType() == SMART) {
                   Set<ExpectedTypeInfo> expectedTypeInfos =
                     ContainerUtil.newHashSet(JavaSmartCompletionContributor.getExpectedTypes(parameters));
                   for (PsiMethod psiMethod : methods) {
                     JavaMethodCallElement lookupElement = new JavaMethodCallElement(psiMethod);
                     JavaCompletionContributor.prepareMethodCallForExpectedTypes(lookupElement, position, expectedTypeInfos);
                     if (ReferenceExpressionCompletionContributor.matchesExpectedType(lookupElement, expectedTypeInfos)) {
                       sortedResult.addElement(lookupElement);
                     }
                   }
                 }
                 else {
                   for (PsiMethod psiMethod : methods) {
                     sortedResult.addElement(new JavaMethodCallElement(psiMethod));
                   }
                 }
               }
             }
           });
  }
}
