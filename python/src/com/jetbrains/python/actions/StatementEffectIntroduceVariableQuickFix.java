package com.jetbrains.python.actions;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * User: catherine
 *
 * Quickfix to introduce variable if statement seems to have no effect
 */
public class StatementEffectIntroduceVariableQuickFix implements LocalQuickFix {
  PyExpression myExpression;
  public StatementEffectIntroduceVariableQuickFix(PyExpression expression) {
    myExpression = expression;
  }

  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.introduce.variable");
  }

  @NonNls
  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INSP.GROUP.python");
  }

  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    String name = "var";
    if (myExpression != null) {
      Application application = ApplicationManager.getApplication();
      if (application != null && !application.isUnitTestMode()) {
        name = Messages.showInputDialog(project, "Enter new variable name",
                                        "New variable name", Messages.getQuestionIcon());
        if (name == null) return;
      }
      if (name.isEmpty()) return;
      PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
      myExpression.replace(elementGenerator.createFromText(LanguageLevel.forElement(myExpression), PyAssignmentStatement.class,
                                                         name + " = " + myExpression.getText()));
    }
  }
}
