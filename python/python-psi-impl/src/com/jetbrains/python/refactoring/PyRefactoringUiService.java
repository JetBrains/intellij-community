package com.jetbrains.python.refactoring;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.refactoring.introduce.IntroduceOperation;
import com.jetbrains.python.refactoring.introduce.IntroduceValidator;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.function.Consumer;

@ApiStatus.Experimental
public class PyRefactoringUiService {
  public void performIntroduceWithDialog(IntroduceOperation operation,
                                         @NlsContexts.DialogTitle String dialogTitle,
                                         IntroduceValidator validator, String id, Consumer<IntroduceOperation> performRefactoringCallback) {

  }

  public void performInplaceIntroduceVariable(IntroduceOperation operation, PsiElement statement) {

  }

  public void showIntroduceTargetChooser(IntroduceOperation operation,
                                         Editor editor,
                                         List<PyExpression> expressions,
                                         Consumer<IntroduceOperation> callback) {
  }

  public void showOccurrencesChooser(IntroduceOperation operation, Editor editor, Consumer<IntroduceOperation> callback) {
    operation.setReplaceAll(true);
    callback.accept(operation);
  }

  public static PyRefactoringUiService getInstance() {
    return ApplicationManager.getApplication().getService(PyRefactoringUiService.class);
  }
}
