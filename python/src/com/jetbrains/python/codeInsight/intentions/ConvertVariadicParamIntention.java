package com.jetbrains.python.codeInsight.intentions;

import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.Stack;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * User: catherine
 *
 * Intention to convert variadic parameter(s) to normal
 * For instance,
 *
 * from:
 * def foo(**kwargs):
 *   doSomething(kwargs['foo'])
 *
 * to:
 * def foo(foo, **kwargs):
 *   doSomething(foo)
 *
 */
public class ConvertVariadicParamIntention extends BaseIntentionAction {
  @NotNull
  public String getText() {
    return PyBundle.message("INTN.convert.variadic.param");
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INTN.convert.variadic.param");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    PyFunction function =
      PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyFunction.class);
    if (function != null) {
      PyParameter[] parameterList = function.getParameterList().getParameters();
      for (PyParameter parameter : parameterList) {
        if (parameter instanceof PyNamedParameter) {
          if (((PyNamedParameter)parameter).isKeywordContainer()) {
            List <PySubscriptionExpression> subscriptions = fillSubscriptions(function);
            List <PyCallExpression> callElements = fillCallExpressions(function);
            if ((subscriptions.size() + callElements.size()) != 0)
              return true;
          }
        }
      }
    }
    return false;
  }

  @Nullable
  private static PyParameter getKeywordContainer(PyFunction function) {
    if (function != null) {
      PyParameter[] parameterList = function.getParameterList().getParameters();
      for (PyParameter parameter : parameterList) {
        if (parameter instanceof PyNamedParameter) {
          if (((PyNamedParameter)parameter).isKeywordContainer()) {
            return parameter;
          }
        }
      }
    }
    return null;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PyFunction function =
      PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyFunction.class);
    replaceSubscriptions(function, project);
    replaceCallElements(function, project);
  }

  /**
   * finds subscriptions of keyword container, adds them to mySubscriptions
   * @param function
   * @param elements
   */
  private static List<PySubscriptionExpression> fillSubscriptions(PyFunction function) {
    List<PySubscriptionExpression> subscriptions = new ArrayList<PySubscriptionExpression>();
    PyStatementList statementList = function.getStatementList();
    Stack<PsiElement> stack = new Stack<PsiElement>();
    PyParameter keywordContainer = getKeywordContainer(function);
    if (keywordContainer != null && statementList != null) {
      String keywordContainerName = keywordContainer.getName();
      for (PyStatement st : statementList.getStatements()) {
        stack.push(st);
        while (!stack.isEmpty()) {
          PsiElement e = stack.pop();
          if (e instanceof PySubscriptionExpression) {
            if (((PySubscriptionExpression)e).getOperand().getText().equals(keywordContainerName)) {
              subscriptions.add((PySubscriptionExpression)e);
            }
          }
          else {
            for (PsiElement psiElement : e.getChildren()) {
              stack.push(psiElement);
            }
          }
        }
      }
    }
    return subscriptions;
  }

  private static boolean isCallElement(PyExpression callee, String keywordContainerName) {
    PyExpression qualifier = ((PyQualifiedExpression)callee).getQualifier();
    return (qualifier != null && qualifier.getText().equals(keywordContainerName)
                                      && ("get".equals(((PyQualifiedExpression)callee).getReferencedName())
                                          || "__getitem__".equals(((PyQualifiedExpression)callee).getReferencedName()) ));
  }

  private static List<PyCallExpression> fillCallExpressions(PyFunction function) {
    List<PyCallExpression> callElements = new ArrayList<PyCallExpression>();
    PyStatementList statementList = function.getStatementList();
    Stack<PsiElement> stack = new Stack<PsiElement>();
    PyParameter keywordContainer = getKeywordContainer(function);
    if (keywordContainer != null && statementList != null) {
      String keywordContainerName = keywordContainer.getName();
      for (PyStatement st : statementList.getStatements()) {
        stack.push(st);
        while (!stack.isEmpty()) {
          PsiElement e = stack.pop();
          if (!(e instanceof PySubscriptionExpression)) {
            if (e instanceof PyCallExpression && ((PyCallExpression)e).getCallee() instanceof PyQualifiedExpression
                    && isCallElement(((PyCallExpression)e).getCallee(), keywordContainerName)) {
              callElements.add((PyCallExpression)e);
            }
            else {
              for (PsiElement psiElement : e.getChildren()) {
                stack.push(psiElement);
              }
            }
          }
        }
      }
    }
    return callElements;
  }

  private static void replaceSubscriptions(PyFunction function, Project project) {
    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    List <PySubscriptionExpression> subscriptions = fillSubscriptions(function);
    int size = subscriptions.size();
    for (int i = 0; i != size; ++i) {
      PySubscriptionExpression subscriptionExpression = subscriptions.get(i);
      PyExpression indexExpression = subscriptionExpression.getIndexExpression();
      if (indexExpression instanceof PyStringLiteralExpression) {
        PyExpression p = elementGenerator.createExpressionFromText(((PyStringLiteralExpression)indexExpression).getStringValue());
        ASTNode comma = elementGenerator.createComma();
        PyClass containingClass = function.getContainingClass();

        if (containingClass == null) {
          function.getParameterList().addBefore(p, function.getParameterList().getParameters()[0]);
          function.getParameterList().addBefore((PsiElement)comma, function.getParameterList().getParameters()[0]);
        }
        else {
          function.getParameterList().addBefore(p, function.getParameterList().getParameters()[1]);
          function.getParameterList().addBefore((PsiElement)comma, function.getParameterList().getParameters()[1]);
        }
        subscriptionExpression.replace(p);
      }
    }
  }

  private static void replaceCallElements(PyFunction function, Project project) {
    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    List <PyCallExpression> callElements = fillCallExpressions(function);

    int size = callElements.size();
    for (int i = 0; i != size; ++i) {
      PyCallExpression callExpression = callElements.get(i);
      PyExpression indexExpression = callExpression.getArguments()[0];

      if (indexExpression instanceof PyStringLiteralExpression) {
        PyNamedParameter defaultValue = null;
        if (callExpression.getArguments().length > 1) {
          defaultValue = elementGenerator.createParameter(
              ((PyStringLiteralExpression)indexExpression).getStringValue()
                                                         + "=" + callExpression.getArguments()[1].getText());
        }
        if (defaultValue == null) {
          PyExpression callee = callExpression.getCallee();
          if (callee instanceof PyQualifiedExpression && "get".equals(((PyQualifiedExpression)callee).getReferencedName())) {
            defaultValue = elementGenerator.createParameter(((PyStringLiteralExpression)indexExpression).getStringValue() + "=None");
          }
        }
        PyExpression p = elementGenerator.createExpressionFromText(((PyStringLiteralExpression)indexExpression).getStringValue());
        ASTNode comma = elementGenerator.createComma();

        PyParameter keywordContainer = getKeywordContainer(function);

        if (defaultValue != null)
          function.getParameterList().addBefore(defaultValue, keywordContainer);
        else
          function.getParameterList().addBefore(p, keywordContainer);

        function.getParameterList().addBefore((PsiElement)comma, keywordContainer);

        callExpression.replace(p);
      }
    }
  }
}
