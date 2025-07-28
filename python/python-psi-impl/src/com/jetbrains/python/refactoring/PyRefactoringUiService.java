package com.jetbrains.python.refactoring;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.refactoring.extractMethod.ExtractMethodDecorator;
import com.intellij.refactoring.extractMethod.ExtractMethodValidator;
import com.jetbrains.python.codeInsight.codeFragment.PyCodeFragment;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.refactoring.extractmethod.PyExtractMethodSettings;
import com.jetbrains.python.refactoring.extractmethod.PyExtractMethodUtil;
import com.jetbrains.python.refactoring.extractmethod.PyVariableData;
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
                                         IntroduceValidator validator, String id, Consumer<? super IntroduceOperation> performRefactoringCallback) {

  }

  public void performInplaceIntroduceVariable(IntroduceOperation operation, PsiElement statement) {

  }

  public void showIntroduceTargetChooser(IntroduceOperation operation,
                                         Editor editor,
                                         List<PyExpression> expressions,
                                         Consumer<? super IntroduceOperation> callback) {
  }

  public void showOccurrencesChooser(IntroduceOperation operation, Editor editor, Consumer<? super IntroduceOperation> callback) {
    operation.setReplaceAll(true);
    callback.accept(operation);
  }

  public @Nullable PyExtractMethodSettings showExtractMethodDialog(final Project project,
                                                                   final String defaultName,
                                                                   final PyCodeFragment fragment,
                                                                   final Object[] visibilityVariants,
                                                                   final ExtractMethodValidator validator,
                                                                   final ExtractMethodDecorator<Object> decorator,
                                                                   final FileType type, String helpId) {
    return new PyExtractMethodSettings(defaultName, new PyVariableData[0], fragment.getOutputType(),
                                       PyExtractMethodUtil.getAddTypeAnnotations(project));
  }

  public void showPyInlineFunctionDialog(@NotNull Project project,
                                         @NotNull Editor editor,
                                         @NotNull PyFunction function, @Nullable PsiReference reference) {
  }

  public static PyRefactoringUiService getInstance() {
    return ApplicationManager.getApplication().getService(PyRefactoringUiService.class);
  }
}