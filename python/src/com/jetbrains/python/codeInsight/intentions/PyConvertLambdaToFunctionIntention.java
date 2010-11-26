package com.jetbrains.python.codeInsight.intentions;

import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * User: catherine
 * Intention to convert lambda to function
 */
public class PyConvertLambdaToFunctionIntention extends BaseIntentionAction {

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INTN.convert.lambda.to.function");
  }

  @NotNull
  public String getText() {
    return PyBundle.message("INTN.convert.lambda.to.function");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    PyLambdaExpression lambdaExpression = PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyLambdaExpression.class);
    if (lambdaExpression != null) {
      if (lambdaExpression.getBody() != null)
        return true;
    }
    return false;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PyLambdaExpression lambdaExpression = PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyLambdaExpression.class);
    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    if (lambdaExpression != null) {
      String name = "function";
      PsiElement parent = lambdaExpression.getParent();
      if (parent instanceof PyAssignmentStatement) {
        name = ((PyAssignmentStatement)parent).getLeftHandSideExpression().getText();
      }
      else {
        Application application = ApplicationManager.getApplication();
        if (application != null && !application.isUnitTestMode()) {
          name = Messages.showInputDialog(project, "Enter new function name",
                                        "New function name", Messages.getQuestionIcon());
          if (name == null) return;
        }
      }
      if (name.isEmpty()) return;
      PyExpression body = lambdaExpression.getBody();
      PyParameter[] parameters = lambdaExpression.getParameterList().getParameters();
      StringBuilder stringBuilder = new StringBuilder();
      stringBuilder.append("def ");
      stringBuilder.append(name);
      stringBuilder.append("(");
      int size = parameters.length;
      for (int i = 0; i != size; ++i) {
        PyParameter parameter = parameters[i];
        stringBuilder.append(parameter.getName());
        if ( i != size - 1)
          stringBuilder.append(",");
      }
      stringBuilder.append("):\n  return ");
      stringBuilder.append(body.getText());

      PyFunction function = elementGenerator.createFromText(LanguageLevel.forElement(lambdaExpression),
                                                            PyFunction.class, stringBuilder.toString());

      PyFunction parentFunction = PsiTreeUtil.getParentOfType(lambdaExpression, PyFunction.class);
      if (parentFunction != null) {
        PyStatementList statements = parentFunction.getStatementList();
        statements.addBefore(function, statements.getStatements()[0]);
      }
      else {
        PyStatement statement = PsiTreeUtil.getParentOfType(lambdaExpression, PyStatement.class);
        file.addBefore(function, statement);
      }
      if (parent instanceof PyAssignmentStatement) {
        parent.delete();
      }
      else {
        lambdaExpression.replace(elementGenerator.createFromText(LanguageLevel.forElement(lambdaExpression), PyExpression.class,
                                                               name));
      }
    }
  }
}
