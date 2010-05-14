package com.jetbrains.python.refactoring.introduce;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashSet;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.refactoring.NameSuggestorUtil;
import com.jetbrains.python.refactoring.PyRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Alexey.Ivanov
 */
abstract public class IntroduceHandler implements RefactoringActionHandler {
  private static void replaceExpression(PyExpression newExpression, Project project, PsiElement expression) {
    PyExpressionStatement statement = PsiTreeUtil.getParentOfType(expression, PyExpressionStatement.class);
    if (statement != null) {
      if (statement.getExpression() == expression) {
        statement.delete();
        return;
      }
    }
    PyPsiUtils.replaceExpression(project, expression, newExpression);
  }

  private final IntroduceValidator myValidator;
  private final String myDialogTitle;

  protected IntroduceHandler(@NotNull final IntroduceValidator validator, @NotNull final String dialogTitle) {
    myValidator = validator;
    myDialogTitle = dialogTitle;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    performAction(project, editor, file, null, false, false);
  }

  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
  }

  protected String[] getSuggestedNames(@NotNull final PyExpression expression) {
    Collection<String> res = new HashSet<String>();
    String text = expression.getText();
    if (text != null) {
      for (String name : NameSuggestorUtil.generateNames(text)) {
        if (myValidator.checkPossibleName(name, expression)) {
          res.add(name);
        }
      }
    }
    PyType type = expression.getType(TypeEvalContext.fast());
    if (type != null) {
      final String typeName = type.getName();
      if (typeName != null) {
        for (String name : NameSuggestorUtil.generateNamesByType(typeName)) {
          if (myValidator.checkPossibleName(name, expression)) {
            res.add(name);
          }
        }
      }
    }
    return ArrayUtil.toStringArray(res);
  }

  public void performAction(@NotNull final Project project, Editor editor, PsiFile file, String name, boolean replaceAll, boolean hasConstructor) {
    if (!CommonRefactoringUtil.checkReadOnlyStatus(file)) {
      return;
    }

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
    if (element1 == null || element2 == null) {
      CommonRefactoringUtil.showErrorHint(project, editor, PyBundle.message("refactoring.introduce.selection.error"), myDialogTitle,
                                          "refactoring.extractMethod");
      return;
    }

    element1 = PyRefactoringUtil.getSelectedExpression(project, file, element1, element2);
    if (!(element1 instanceof PyExpression) || (PsiTreeUtil.getParentOfType(element1, PyParameterList.class)) != null) {
      CommonRefactoringUtil.showErrorHint(project, editor, PyBundle.message("refactoring.introduce.selection.error"), myDialogTitle,
                                          "refactoring.extractMethod");
      return;
    }

    if (!checkEnabled(project, editor, element1, myDialogTitle)) {
      return;
    }

    final PyExpression expression = (PyExpression)element1;

    final List<PsiElement> occurrences;
    if (expression.getUserData(PyPsiUtils.SELECTION_BREAKS_AST_NODE) == null && !(expression instanceof PyCallExpression)) {
      occurrences = getOccurrences(expression);
    }
    else {
      occurrences = Collections.emptyList();
    }
    String[] possibleNames = getSuggestedNames(expression);
    boolean initInConstructor = false;
    if (name == null) {
      PyIntroduceDialog dialog = new PyIntroduceDialog(project, expression, myDialogTitle, myValidator, occurrences.size(), possibleNames, getHelpId(), hasConstructor);
      dialog.show();
      if (!dialog.isOK()) {
        return;
      }
      name = dialog.getName();
      replaceAll = dialog.doReplaceAllOccurrences();
      initInConstructor = dialog.initInConstructor();
    }
    String assignmentText = name + " = " + expression.getText();
    PyAssignmentStatement declaration = createDeclaration(project, assignmentText);

    assert name != null;
    declaration = performReplace(project, declaration, expression, occurrences, name, replaceAll, initInConstructor);
    declaration = CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(declaration);
    editor.getCaretModel().moveToOffset(declaration.getTextRange().getEndOffset());
    editor.getSelectionModel().removeSelection();
  }

  protected abstract String getHelpId();

  protected PyAssignmentStatement createDeclaration(Project project, String assignmentText) {
    return PyElementGenerator.getInstance(project).createFromText(PyAssignmentStatement.class, assignmentText);
  }

  protected boolean checkEnabled(Project project, Editor editor, PsiElement element1, String dialogTitle) {
    return true;
  }

  private static List<PsiElement> getOccurrences(@NotNull final PyExpression expression) {
    PsiElement context = PsiTreeUtil.getParentOfType(expression, PyFunction.class);
    if (context == null) {
      context = PsiTreeUtil.getParentOfType(expression, PyClass.class);
    }
    if (context == null) {
      context = expression.getContainingFile();
    }
    return PyRefactoringUtil.getOccurences(expression, context);
  }

  private PyAssignmentStatement performReplace(@NotNull final Project project,
                                               @NotNull final PyAssignmentStatement declaration,
                                               @NotNull final PsiElement expression,
                                               @NotNull final List<PsiElement> occurrences,
                                               @NotNull final String name,
                                               final boolean replaceAll,
                                               final boolean initInConstructor) {
    return new WriteCommandAction<PyAssignmentStatement>(project, expression.getContainingFile()) {
      protected void run(final Result<PyAssignmentStatement> result) throws Throwable {
        final Pair<PsiElement, TextRange> data = expression.getUserData(PyPsiUtils.SELECTION_BREAKS_AST_NODE);
        if (data == null) {
          result.setResult((PyAssignmentStatement)addDeclaration(expression, declaration, occurrences, replaceAll, initInConstructor));
        }
        else {
          result.setResult((PyAssignmentStatement)addDeclaration(data.first, declaration, occurrences, replaceAll, initInConstructor));
        }

        PyExpression newExpression = createExpression(project, name, declaration);

        if (replaceAll) {
          for (PsiElement occurrence : occurrences) {
            replaceExpression(newExpression, project, occurrence);
          }
        }
        else {
          replaceExpression(newExpression, project, expression);
        }
      }
    }.execute().getResultObject();
  }

  protected PyExpression createExpression(Project project, String name, PyAssignmentStatement declaration) {
    return PyElementGenerator.getInstance(project).createExpressionFromText(name);
  }

  @Nullable
  protected abstract PsiElement addDeclaration(@NotNull final PsiElement expression,
                                               @NotNull final PsiElement declaration,
                                               @NotNull final List<PsiElement> occurrences,
                                               final boolean replaceAll,
                                               boolean initInConstructor);
}
