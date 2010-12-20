package com.jetbrains.python.codeInsight.intentions;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.template.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyFunctionBuilder;
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
    if (lambdaExpression != null) {
      String name = "function";
      PsiElement parent = lambdaExpression.getParent();
      if (parent instanceof PyAssignmentStatement) {
        name = ((PyAssignmentStatement)parent).getLeftHandSideExpression().getText();
      }

      if (name.isEmpty()) return;
      PyExpression body = lambdaExpression.getBody();
      PyFunctionBuilder functionBuilder = new PyFunctionBuilder(name);
      for (PyParameter param : lambdaExpression.getParameterList().getParameters()) {
        functionBuilder.parameter(param.getText());
      }
      functionBuilder.statement("return " + body.getText());
      PyFunction function = functionBuilder.buildFunction(project, LanguageLevel.getDefault());

      PyFunction parentFunction = PsiTreeUtil.getTopmostParentOfType(lambdaExpression, PyFunction.class);
      if (parentFunction != null ) {
        PyClass parentClass = PsiTreeUtil.getTopmostParentOfType(parentFunction, PyClass.class);
        if (parentClass != null) {
          PsiElement classParent = parentClass.getParent();
          function = (PyFunction)classParent.addBefore(function, parentClass);
        } else {
          PsiElement funcParent = parentFunction.getParent();
          function = (PyFunction)funcParent.addBefore(function, parentFunction);
        }
      } else {
        PyStatement statement = PsiTreeUtil.getTopmostParentOfType(lambdaExpression,
                                                                   PyStatement.class);
        if (statement != null) {
          PsiElement statementParent = statement.getParent();
          if (statementParent != null)
            function = (PyFunction)statementParent.addBefore(function, statement);
        }
      }
      function = CodeInsightUtilBase
        .forcePsiPostprocessAndRestoreElement(function);

      if (parent instanceof PyAssignmentStatement) {
        parent.delete();
      }
      else {
        PyElement parentScope = PsiTreeUtil.getParentOfType(lambdaExpression, PyClass.class, PyFile.class);
        final TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(parentScope);
        PsiElement functionName = function.getNameIdentifier();
        functionName = CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(functionName);
        lambdaExpression = CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(lambdaExpression);

        ReferenceNameExpression refExpr = new ReferenceNameExpression(name);

        ((TemplateBuilderImpl)builder).replaceElement(lambdaExpression, name, refExpr, true);
        ((TemplateBuilderImpl)builder).replaceElement(functionName, name, name, false);

        int textOffSet = functionName.getTextOffset();
        editor.getCaretModel().moveToOffset(parentScope.getTextRange().getStartOffset());

        Template template = ((TemplateBuilderImpl)builder).buildInlineTemplate();
        TemplateManager.getInstance(project).startTemplate(editor, template);
        editor.getCaretModel().moveToOffset(textOffSet);
      }
    }
  }
  private class ReferenceNameExpression extends Expression {
    ReferenceNameExpression(String oldReferenceName) {
      myOldReferenceName = oldReferenceName;
    }

    private final String myOldReferenceName;

    public Result calculateResult(ExpressionContext context) {
      return new TextResult(myOldReferenceName);
    }

    public Result calculateQuickResult(ExpressionContext context) {
      return null;
    }

    @Override
    public LookupElement[] calculateLookupItems(ExpressionContext context) {
      return null;
    }
  }
}
