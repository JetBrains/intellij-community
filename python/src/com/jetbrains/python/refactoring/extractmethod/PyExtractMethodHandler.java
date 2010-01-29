package com.jetbrains.python.refactoring.extractmethod;

import com.intellij.codeInsight.codeFragment.CannotCreateCodeFragmentException;
import com.intellij.codeInsight.codeFragment.CodeFragment;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.codeInsight.codeFragment.PyCodeFragmentUtil;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.refactoring.PyRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author oleg
 */
public class PyExtractMethodHandler implements RefactoringActionHandler {

  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file, final DataContext dataContext) {
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    // select editor text fragment
    if (!editor.getSelectionModel().hasSelection()) {
      editor.getSelectionModel().selectLineAtCaret();
    }
    invokeOnEditor(project, editor, file);
  }


  public void invoke(@NotNull final Project project, @NotNull final PsiElement[] elements, final DataContext dataContext) {
    // ignore this
  }

  private void invokeOnEditor(final Project project, final Editor editor, final PsiFile file) {
    CommonRefactoringUtil.checkReadOnlyStatus(project, file);
    PsiElement element1 = null;
    PsiElement element2 = null;
    final SelectionModel selectionModel = editor.getSelectionModel();
    if (selectionModel.hasSelection()) {
      element1 = file.findElementAt(selectionModel.getSelectionStart());
      element2 = file.findElementAt(selectionModel.getSelectionEnd() - 1);
    }
    else {
      final CaretModel caretModel = editor.getCaretModel();
      final Document document = editor.getDocument();
      int lineNumber = document.getLineNumber(caretModel.getOffset());
      if ((lineNumber >= 0) && (lineNumber < document.getLineCount())) {
        element1 = file.findElementAt(document.getLineStartOffset(lineNumber));
        element2 = file.findElementAt(document.getLineEndOffset(lineNumber) - 1);
      }
    }
    // Pass comments and whitespaces
    while (element1 != null && StringUtil.isEmptyOrSpaces(element1.getText()) || element1 instanceof PsiComment){
      element1 = PsiTreeUtil.nextLeaf(element1);
    }
    while (element2 != null && StringUtil.isEmptyOrSpaces(element2.getText()) || element2 instanceof PsiComment){
      element2 = PsiTreeUtil.prevLeaf(element2);
    }
    if (element1 == null || element2 == null) {
      CommonRefactoringUtil.showErrorHint(project, editor,
                                          PyBundle.message("refactoring.extract.method.error.cannot.perform.refactoring.using.selected.elements"),
                                          RefactoringBundle.message("extract.method.title"), "refactoring.extractMethod");
      return;
    }

    final PsiElement[] statements = getStatementsRange(element1, element2);
    if (statements != null) {
      final ScopeOwner owner = PsiTreeUtil.getParentOfType(statements[0], ScopeOwner.class);
      if (owner == null) {
        return;
      }
      final CodeFragment fragment;
      try {
        fragment = PyCodeFragmentUtil.createCodeFragment(owner, element1, element2);
      }
      catch (CannotCreateCodeFragmentException e) {
        CommonRefactoringUtil.showErrorHint(project, editor, e.getMessage(),
                                            RefactoringBundle.message("extract.method.title"), "refactoring.extractMethod");
        return;
      }
      PyExtractMethodUtil.extractFromStatements(project, editor, fragment, statements[0], statements[1]);
      return;
    }

    final PsiElement expression = PyRefactoringUtil.getSelectedExpression(project, file, element1, element2);
    if (expression != null) {
      final ScopeOwner owner = PsiTreeUtil.getParentOfType(expression, ScopeOwner.class);
      if (owner == null) {
        return;
      }
      final CodeFragment fragment;
      try {
        fragment = PyCodeFragmentUtil.createCodeFragment(owner, element1, element2);
      }
      catch (CannotCreateCodeFragmentException e) {
        CommonRefactoringUtil.showErrorHint(project, editor, e.getMessage(),
                                            RefactoringBundle.message("extract.method.title"), "refactoring.extractMethod");
        return;
      }
      PyExtractMethodUtil.extractFromExpression(project, editor, fragment, expression);
    }

    CommonRefactoringUtil.showErrorHint(project, editor,
                                        PyBundle.message("refactoring.extract.method.error.cannot.perform.refactoring.using.selected.elements"),
                                        RefactoringBundle.message("extract.method.title"), "refactoring.extractMethod");
  }

  @Nullable
  private PsiElement[] getStatementsRange(final PsiElement element1, final PsiElement element2) {
    final PsiElement parent = PsiTreeUtil.findCommonParent(element1, element2);
    if (parent == null) {
      return null;
    }

    final PyElement compStatement = PyPsiUtils.getCompoundStatement(parent);
    if (compStatement == null) {
      return null;
    }

    final PsiElement statement1 = PyPsiUtils.getStatement(compStatement, element1);
    final PsiElement statement2 = PyPsiUtils.getStatement(compStatement, element2);
    if (statement1 == null || statement2 == null){
      return null;
    }

    // return elements if they are really first and last elements of statements
    if (element1 == PsiTreeUtil.getDeepestFirst(statement1) && element2 == PsiTreeUtil.getDeepestLast(statement2)) {
      return new PsiElement[]{statement1, statement2};
    }
    return null;
  }

}
