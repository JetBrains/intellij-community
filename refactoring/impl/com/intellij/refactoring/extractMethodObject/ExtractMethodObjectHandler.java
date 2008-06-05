/*
 * User: anna
 * Date: 06-May-2008
 */
package com.intellij.refactoring.extractMethodObject;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.extractMethod.ExtractMethodHandler;
import com.intellij.refactoring.extractMethod.PrepareFailedException;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.duplicates.DuplicatesImpl;
import org.jetbrains.annotations.NotNull;

public class ExtractMethodObjectHandler implements RefactoringActionHandler {
  private static final Logger LOG = Logger.getInstance("#" + ExtractMethodObjectHandler.class.getName());

  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file, final DataContext dataContext) {
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    if (!editor.getSelectionModel().hasSelection()) {
      editor.getSelectionModel().selectLineAtCaret();
    }
    int startOffset = editor.getSelectionModel().getSelectionStart();
    int endOffset = editor.getSelectionModel().getSelectionEnd();

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    PsiElement[] elements;
    PsiExpression expr = CodeInsightUtil.findExpressionInRange(file, startOffset, endOffset);
    if (expr != null) {
      elements = new PsiElement[]{expr};
    }
    else {
      elements = CodeInsightUtil.findStatementsInRange(file, startOffset, endOffset);
    }

    if (elements.length == 0) {
        String message = RefactoringBundle
          .getCannotRefactorMessage(RefactoringBundle.message("selected.block.should.represent.a.set.of.statements.or.an.expression"));
        CommonRefactoringUtil.showErrorMessage(ExtractMethodObjectProcessor.REFACTORING_NAME, message, HelpID.EXTRACT_METHOD_OBJECT, project);
      return;
    }

    final ExtractMethodObjectProcessor processor = new ExtractMethodObjectProcessor(project, editor, elements, "");
    final ExtractMethodObjectProcessor.MyExtractMethodProcessor extractProcessor = processor.getExtractProcessor();
    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, extractProcessor.getTargetClass().getContainingFile())) return;
    try {
      if (!extractProcessor.prepare()) return;
    }
    catch (PrepareFailedException e) {
      CommonRefactoringUtil
          .showErrorMessage(ExtractMethodObjectProcessor.REFACTORING_NAME, e.getMessage(), HelpID.EXTRACT_METHOD_OBJECT, project);
      ExtractMethodHandler.highlightPrepareError(e, file, editor, project);
      return;
    }


    if (extractProcessor.showDialog()) {
      new WriteCommandAction(project, ExtractMethodObjectProcessor.REFACTORING_NAME, ExtractMethodObjectProcessor.REFACTORING_NAME) {
        protected void run(final Result result) throws Throwable {
          extractProcessor.doRefactoring();
          processor.run();
        }
      }.execute();

      if (processor.isCreateInnerClass()) {
        CommandProcessor.getInstance().executeCommand(project, new Runnable(){
          public void run() {
            DuplicatesImpl.processDuplicates(extractProcessor, project, editor);
          }
        }, ExtractMethodObjectProcessor.REFACTORING_NAME, ExtractMethodObjectProcessor.REFACTORING_NAME);
      }

      new WriteCommandAction(project, ExtractMethodObjectProcessor.REFACTORING_NAME, ExtractMethodObjectProcessor.REFACTORING_NAME) {
        protected void run(final Result result) throws Throwable {
          processor.getMethod().delete();
        }
      }.execute();
    }
  }

  public void invoke(@NotNull final Project project, @NotNull final PsiElement[] elements, final DataContext dataContext) {
    throw new UnsupportedOperationException();
  }
}