// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.refactoring.introduce;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.introduce.inplace.InplaceVariableIntroducer;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyTargetExpression;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Consumer;

import static com.jetbrains.python.refactoring.introduce.IntroduceHandler.findOccurrenceUnderCaret;

public class PyIntroduceHandlerUi {

  public static void performInplaceIntroduceVariable(IntroduceOperation operation, PsiElement statement) {
    if (statement instanceof PyAssignmentStatement) {
      PyTargetExpression target = (PyTargetExpression) ((PyAssignmentStatement)statement).getTargets() [0];
      final List<PsiElement> occurrences = operation.getOccurrences();
      final PsiElement occurrence = findOccurrenceUnderCaret(occurrences, operation.getEditor());
      PsiElement elementForCaret = occurrence != null ? occurrence : target;
      operation.getEditor().getCaretModel().moveToOffset(elementForCaret.getTextRange().getStartOffset());
      final InplaceVariableIntroducer<PsiElement> introducer =
        new PyInplaceVariableIntroducer(target, operation, occurrences);
      introducer.performInplaceRefactoring(new LinkedHashSet<>(operation.getSuggestedNames()));
    }
  }

  public static void performIntroduceWithDialog(IntroduceOperation operation,
                                                @NlsContexts.DialogTitle String dialogTitle,
                                                IntroduceValidator validator, String id, Consumer<IntroduceOperation> performRefactoringCallback) {
    final Project project = operation.getProject();
    if (operation.getName() == null) {
      PyIntroduceDialog dialog = new PyIntroduceDialog(project, dialogTitle, validator, id, operation);
      if (!dialog.showAndGet()) {
        return;
      }
      operation.setName(dialog.getName());
      operation.setReplaceAll(dialog.doReplaceAllOccurrences());
      operation.setInitPlace(dialog.getInitPlace());
    }

    performRefactoringCallback.accept(operation);
  }

  private static class PyInplaceVariableIntroducer extends InplaceVariableIntroducer<PsiElement> {
    private final PyTargetExpression myTarget;

    PyInplaceVariableIntroducer(PyTargetExpression target,
                                IntroduceOperation operation,
                                List<PsiElement> occurrences) {
      super(target, operation.getEditor(), operation.getProject(), PyBundle.message("python.introduce.variable.refactoring.name"),
            occurrences.toArray(PsiElement.EMPTY_ARRAY), null);
      myTarget = target;
    }

    @Override
    protected PsiElement checkLocalScope() {
      return myTarget.getContainingFile();
    }
  }
}
