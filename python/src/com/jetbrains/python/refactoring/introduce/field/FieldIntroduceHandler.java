package com.jetbrains.python.refactoring.introduce.field;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.actions.AddFieldQuickFix;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.refactoring.introduce.IntroduceHandler;
import com.jetbrains.python.refactoring.introduce.variable.VariableIntroduceHandler;
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
    performAction(project, editor, file, null, false, true);
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
                                      boolean replaceAll, boolean initInConstructor) {
    final PsiElement expr = expression instanceof PyClass ? expression : expression.getParent();    
    PsiElement anchor = PyUtil.getContainingClassOrSelf(expr);
    assert anchor instanceof PyClass;
    if (initInConstructor) {
      final Project project = anchor.getProject();
      final PyClass clazz = (PyClass)anchor;
      AddFieldQuickFix.addFieldToInit(project, clazz, "", new AddFieldDeclaration(project, declaration));
      final PyFunction init = clazz.findMethodByName(PyNames.INIT, false);
      final PyStatementList statements = init != null ? init.getStatementList() : null;
      return statements != null ? statements.getLastChild() :  null; 
    }
    return VariableIntroduceHandler.doIntroduceVariable(expression, declaration, occurrences, replaceAll);
  }

  @Override
  protected PyExpressionStatement createExpression(Project project, String name, PyAssignmentStatement declaration) {
    final String text = declaration.getText();
    final String self_name = text.substring(0, text.indexOf('.'));
    return PythonLanguage.getInstance().getElementGenerator().createFromText(project, PyExpressionStatement.class, self_name + "." + name);
  }

  @Override
  protected PyAssignmentStatement createDeclaration(Project project, String assignmentText) {
    return PythonLanguage.getInstance().getElementGenerator().createFromText(project, PyAssignmentStatement.class, PyNames.CANONICAL_SELF + "." + assignmentText);
  }

  private static class AddFieldDeclaration extends AddFieldQuickFix.FieldCallback {
    private final Project myProject;
    private final PsiElement myDeclaration;

    private AddFieldDeclaration(Project project, PsiElement declaration) {
      myProject = project;
      myDeclaration = declaration;
    }

    public PyStatement fun(String self_name) {
      if (PyNames.CANONICAL_SELF.equals(self_name)) {
        return (PyStatement)myDeclaration;
      }
      final String text = myDeclaration.getText();
      return myGenerator.createFromText(myProject, PyStatement.class, text.replaceFirst(PyNames.CANONICAL_SELF + "\\.", self_name + "."));
    }
  }
}
