// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.refactoring;

import com.intellij.codeInsight.codeFragment.CodeFragment;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pass;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.IntroduceTargetChooser;
import com.intellij.refactoring.extractMethod.AbstractExtractMethodDialog;
import com.intellij.refactoring.extractMethod.ExtractMethodDecorator;
import com.intellij.refactoring.extractMethod.ExtractMethodSettings;
import com.intellij.refactoring.extractMethod.ExtractMethodValidator;
import com.intellij.refactoring.introduce.inplace.OccurrencesChooser;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.refactoring.introduce.IntroduceOperation;
import com.jetbrains.python.refactoring.introduce.IntroduceValidator;
import com.jetbrains.python.refactoring.introduce.PyIntroduceHandlerUi;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

public class PyRefactoringUiServiceImpl extends PyRefactoringUiService {
  @Override
  public void showIntroduceTargetChooser(IntroduceOperation operation,
                                         Editor editor,
                                         List<PyExpression> expressions,
                                         Consumer<IntroduceOperation> callback) {
    IntroduceTargetChooser.showChooser(editor, expressions, new Pass<>() {
      @Override
      public void pass(PyExpression pyExpression) {
        operation.setElement(pyExpression);
        callback.accept(operation);
      }
    }, pyExpression -> pyExpression.getText());
  }

  @Override
  public void showOccurrencesChooser(IntroduceOperation operation, Editor editor, Consumer<IntroduceOperation> callback) {
    OccurrencesChooser.simpleChooser(editor).showChooser(operation.getElement(), operation.getOccurrences(), new Pass<>() {
      @Override
      public void pass(OccurrencesChooser.ReplaceChoice replaceChoice) {
        operation.setReplaceAll(replaceChoice == OccurrencesChooser.ReplaceChoice.ALL);
        callback.accept(operation);
      }
    });
  }

  @Override
  public void performIntroduceWithDialog(IntroduceOperation operation,
                                         @NlsContexts.DialogTitle String dialogTitle,
                                         IntroduceValidator validator, String id, Consumer<IntroduceOperation> performRefactoringCallback) {
    PyIntroduceHandlerUi.performIntroduceWithDialog(operation, dialogTitle, validator, id, performRefactoringCallback);
  }

  @Override
  public void performInplaceIntroduceVariable(IntroduceOperation operation, PsiElement statement) {
    PyIntroduceHandlerUi.performInplaceIntroduceVariable(operation, statement);
  }

  @Override
  public @Nullable <T> ExtractMethodSettings<T> showExtractMethodDialog(Project project,
                                                                        String defaultName,
                                                                        CodeFragment fragment,
                                                                        T[] visibilityVariants,
                                                                        ExtractMethodValidator validator,
                                                                        ExtractMethodDecorator<T> decorator,
                                                                        FileType type,
                                                                        String helpId) {
    final AbstractExtractMethodDialog<T> dialog =
      new AbstractExtractMethodDialog<>(project, defaultName, fragment, visibilityVariants, validator, decorator, type) {
        @Override
        protected String getHelpId() {
          return helpId;
        }
      };
    dialog.show();

    //return if don`t want to extract method
    if (!dialog.isOK()) {
      return null;
    }
    else {
      return dialog;
    }
  }
}
