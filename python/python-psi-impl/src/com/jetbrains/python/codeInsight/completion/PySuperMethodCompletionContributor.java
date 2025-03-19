/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.codeInsight.completion;

import com.intellij.codeInsight.TailTypes;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.TailTypeDecorator;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.refactoring.PyPsiRefactoringUtil;
import com.jetbrains.python.refactoring.classes.PyClassRefactoringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

import static com.intellij.patterns.PlatformPatterns.psiElement;


public final class PySuperMethodCompletionContributor extends CompletionContributor implements DumbAware {
  public PySuperMethodCompletionContributor() {
    extend(CompletionType.BASIC,
           psiElement().afterLeafSkipping(psiElement().whitespace(), psiElement().withElementType(PyTokenTypes.DEF_KEYWORD)),
           new CompletionProvider<>() {
             @Override
             protected void addCompletions(@NotNull CompletionParameters parameters,
                                           @NotNull ProcessingContext context,
                                           @NotNull CompletionResultSet result) {
               PsiElement position = parameters.getOriginalPosition();
               PyClass containingClass = PsiTreeUtil.getParentOfType(position, PyClass.class);
               PsiElement nextElement = position != null ? position.getNextSibling() : null;
               if (containingClass == null && position instanceof PsiWhiteSpace) {
                 position = PsiTreeUtil.prevLeaf(position);
                 containingClass = PsiTreeUtil.getParentOfType(position, PyClass.class);
               }
               if (containingClass == null) {
                 return;
               }
               Set<String> seenNames = new HashSet<>();
               for (PyFunction function : containingClass.getMethods()) {
                 seenNames.add(function.getName());
               }
               LanguageLevel languageLevel = LanguageLevel.forElement(parameters.getOriginalFile());
               seenNames.addAll(PyNames.getBuiltinMethods(languageLevel).keySet());
               TypeEvalContext typeEvalContext = TypeEvalContext.codeCompletion(containingClass.getProject(),
                                                                                containingClass.getContainingFile());
               for (PyFunction superMethod : PyPsiRefactoringUtil.getAllSuperMethods(containingClass, typeEvalContext)) {
                 if (!seenNames.add(superMethod.getName())) {
                   continue;
                 }
                 StringBuilder builder = new StringBuilder();
                 builder.append(superMethod.getName());
                 if (!(nextElement instanceof PyParameterList)) {
                   PyParameterList parameterList;
                   boolean copyAnnotations = PyPsiRefactoringUtil.shouldCopyAnnotations(superMethod, parameters.getOriginalFile());
                   if (copyAnnotations) {
                     parameterList = superMethod.getParameterList();
                   }
                   else {
                     parameterList = stripAnnotations(superMethod.getParameterList());
                   }
                   builder.append(parameterList.getText());
                   if (superMethod.getAnnotation() != null && copyAnnotations) {
                     builder.append(" ")
                       .append(superMethod.getAnnotation().getText())
                       .append(":");
                   }
                   else if (superMethod.getTypeComment() != null) {
                     builder.append(":  ")
                       .append(superMethod.getTypeComment().getText());
                   }
                   else {
                     builder.append(":");
                   }
                 }
                 LookupElementBuilder element = LookupElementBuilder.create(builder.toString())
                   .withInsertHandler((insertionContext, item) -> {
                     PsiElement methodName = insertionContext.getFile().findElementAt(insertionContext.getStartOffset());
                     if (methodName == null || !(methodName.getParent() instanceof PyFunction insertedMethod)) return;
                     WriteCommandAction.writeCommandAction(insertionContext.getFile()).run(() -> {
                       PyClassRefactoringUtil.transplantImportsFromSignature(superMethod, insertedMethod);
                     });
                   });
                 result.addElement(TailTypeDecorator.withTail(element, TailTypes.noneType()));
               }
             }
           });
  }

  private static <T extends PsiElement> @NotNull T stripAnnotations(@NotNull T element) {
    @SuppressWarnings("unchecked") T result = (T)element.copy();
    result.accept(new PyRecursiveElementVisitor() {
      @Override
      public void visitPyAnnotation(@NotNull PyAnnotation node) {
        node.delete();
      }
    });
    return result;
  }
}
