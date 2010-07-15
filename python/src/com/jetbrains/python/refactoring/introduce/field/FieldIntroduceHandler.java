package com.jetbrains.python.refactoring.introduce.field;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.Function;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.actions.AddFieldQuickFix;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.refactoring.PyRefactoringUtil;
import com.jetbrains.python.refactoring.introduce.IntroduceHandler;
import com.jetbrains.python.refactoring.introduce.variable.VariableIntroduceHandler;
import com.jetbrains.python.testing.PythonUnitTestUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Dennis.Ushakov
 */
public class FieldIntroduceHandler extends IntroduceHandler {

  public FieldIntroduceHandler() {
    super(new IntroduceFieldValidator(), RefactoringBundle.message("introduce.field.title"));
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    performAction(project, editor, file, null, false, true, isTestClass(file, editor));
  }

  private static boolean isTestClass(PsiFile file, Editor editor) {
    PsiElement element1 = null;
    final SelectionModel selectionModel = editor.getSelectionModel();
    if (selectionModel.hasSelection()) {
      element1 = file.findElementAt(selectionModel.getSelectionStart());
    }
    else {
      final CaretModel caretModel = editor.getCaretModel();
      final Document document = editor.getDocument();
      int lineNumber = document.getLineNumber(caretModel.getOffset());
      if ((lineNumber >= 0) && (lineNumber < document.getLineCount())) {
        element1 = file.findElementAt(document.getLineStartOffset(lineNumber));
      }
    }
    if (element1 != null) {
      final PyClass clazz = PyUtil.getContainingClassOrSelf(element1);
      if (PythonUnitTestUtil.isTestCaseClass(clazz)) return true;
    }
    return false;
  }

  @Override
  protected boolean checkEnabled(Project project, Editor editor, PsiElement element1, String dialogTitle) {
    if (PyUtil.getContainingClassOrSelf(element1) == null) {
      CommonRefactoringUtil.showErrorHint(project, editor, "Cannot introduce field: not in class", dialogTitle,
                                          "refactoring.extractMethod");
      return false;
    }
    return true;
  }

  @Nullable
  @Override
  protected PsiElement addDeclaration(@NotNull PsiElement expression, @NotNull PsiElement declaration, @NotNull List<PsiElement> occurrences,
                                      boolean replaceAll, InitPlace initInConstructor) {
    final PsiElement expr = expression instanceof PyClass ? expression : expression.getParent();    
    PsiElement anchor = PyUtil.getContainingClassOrSelf(expr);
    assert anchor instanceof PyClass;
    if (initInConstructor == InitPlace.CONSTRUCTOR) {
      final Project project = anchor.getProject();
      final PyClass clazz = (PyClass)anchor;
      AddFieldQuickFix.addFieldToInit(project, clazz, "", new AddFieldDeclaration(declaration));
      final PyFunction init = clazz.findMethodByName(PyNames.INIT, false);
      final PyStatementList statements = init != null ? init.getStatementList() : null;
      return statements != null ? statements.getLastChild() :  null; 
    }
    return VariableIntroduceHandler.doIntroduceVariable(expression, declaration, occurrences, replaceAll);
  }

  @Override
  protected PyExpression createExpression(Project project, String name, PyAssignmentStatement declaration) {
    final String text = declaration.getText();
    final String self_name = text.substring(0, text.indexOf('.'));
    return PyElementGenerator.getInstance(project).createExpressionFromText(self_name + "." + name);
  }

  @Override
  protected PyAssignmentStatement createDeclaration(Project project, String assignmentText) {
    return PyElementGenerator.getInstance(project).createFromText(PyAssignmentStatement.class, PyNames.CANONICAL_SELF + "." + assignmentText);
  }

  @Override
  protected String getHelpId() {
    return "refactoring.introduceField";
  }

  private static class AddFieldDeclaration implements Function<String, PyStatement> {
    private final PsiElement myDeclaration;

    private AddFieldDeclaration(PsiElement declaration) {
      myDeclaration = declaration;
    }

    public PyStatement fun(String self_name) {
      if (PyNames.CANONICAL_SELF.equals(self_name)) {
        return (PyStatement)myDeclaration;
      }
      final String text = myDeclaration.getText();
      final Project project = myDeclaration.getProject();
      return PyElementGenerator.getInstance(project).createFromText(PyStatement.class,
                                                                    text.replaceFirst(PyNames.CANONICAL_SELF + "\\.", self_name + "."));
    }
  }
}
