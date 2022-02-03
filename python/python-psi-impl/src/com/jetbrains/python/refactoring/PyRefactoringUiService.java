package com.jetbrains.python.refactoring;

import com.intellij.codeInsight.codeFragment.CodeFragment;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.extractMethod.ExtractMethodDecorator;
import com.intellij.refactoring.extractMethod.ExtractMethodSettings;
import com.intellij.refactoring.extractMethod.ExtractMethodValidator;
import com.intellij.refactoring.util.AbstractVariableData;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.refactoring.introduce.IntroduceOperation;
import com.jetbrains.python.refactoring.introduce.IntroduceValidator;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  @Nullable
  public <T> ExtractMethodSettings<T> showExtractMethodDialog(final Project project,
                                                          final String defaultName,
                                                          final CodeFragment fragment,
                                                          final T[] visibilityVariants,
                                                          final ExtractMethodValidator validator,
                                                          final ExtractMethodDecorator<T> decorator,
                                                          final FileType type, String helpId) {
    return new ExtractMethodSettings<T>() {
      @Override
      public @NotNull String getMethodName() {
        return defaultName;
      }

      @Override
      public AbstractVariableData @NotNull [] getAbstractVariableData() {
        return new AbstractVariableData[0];
      }

      @Override
      public @Nullable T getVisibility() {
        return null;
      }
    };
  }

  public static PyRefactoringUiService getInstance() {
    return ApplicationManager.getApplication().getService(PyRefactoringUiService.class);
  }
}
