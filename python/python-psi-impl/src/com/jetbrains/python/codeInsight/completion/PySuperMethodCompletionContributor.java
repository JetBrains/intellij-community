// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.completion;

import com.intellij.codeInsight.TailTypes;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.TailTypeDecorator;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyAnnotation;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyParameterList;
import com.jetbrains.python.psi.PyRecursiveElementVisitor;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.refactoring.PyPsiRefactoringUtil;
import com.jetbrains.python.refactoring.classes.PyClassRefactoringUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

import static com.intellij.patterns.PlatformPatterns.psiElement;


@ApiStatus.Internal
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
                   if (superMethod.getAnnotation() != null && copyAnnotations && !superMethod.getName().equals("__init__")) {
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
                       addFunctionModifier(superMethod, insertedMethod);
                     });
                   })
                   .withIcon(superMethod.getIcon(0));
                 result.addElement(TailTypeDecorator.withTail(element, TailTypes.noneType()));
               }
             }
           });
  }

  private static void addFunctionModifier(PyFunction superMethod, PyFunction insertedMethod) {
    var newModifier = insertedMethod.getModifier();
    if (newModifier != null) return;

    var modifier = superMethod.getModifier();
    if (modifier == null) return;

    PyUtil.addDecorator(insertedMethod, "@" + modifier.getDecoratorName());
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
