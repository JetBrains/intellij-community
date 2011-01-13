package com.jetbrains.python.codeInsight.intentions;

import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.Stack;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

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
  private PyFunction myFunction;
  private String myKeywordContainerName;
  private boolean canRemove = true;
  private PyParameter myKeywordContainer;
  private List <PySubscriptionExpression> myElements;
  private List <PyCallExpression> myCallElements;

  @NotNull
  public String getText() {
    return PyBundle.message("INTN.convert.variadic.param");
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INTN.convert.variadic.param");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    myFunction =
      PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyFunction.class);
    if (myFunction != null) {
      PyParameter[] parameterList = myFunction.getParameterList().getParameters();
      for (PyParameter parameter : parameterList) {
        if (parameter instanceof PyNamedParameter) {
          if (((PyNamedParameter)parameter).isKeywordContainer()) {
            myKeywordContainerName = parameter.getName();
            myKeywordContainer = parameter;
            fillSubscriptions();
            if ((myElements.size() + myCallElements.size()) != 0 && canRemove)
              return true;
          }
        }
      }
    }
    canRemove = true;
    return false;
  }

  /**
   * finds subscriptions of keyword container, adds them to mySubscriptions
   */
  private void fillSubscriptions() {
    PyStatementList statementList = myFunction.getStatementList();
    myElements = new ArrayList<PySubscriptionExpression>();
    myCallElements = new ArrayList<PyCallExpression>();
    Stack<PsiElement> stack = new Stack<PsiElement>();
    for (PyStatement st : statementList.getStatements()) {
      stack.push(st);
      while (!stack.isEmpty()) {
        PsiElement e = stack.pop();
        if (e instanceof PySubscriptionExpression) {
          if (((PySubscriptionExpression)e).getOperand().getText().equals(myKeywordContainerName)) {
            myElements.add((PySubscriptionExpression)e);
          }
        }
        else {
          if (!(e instanceof PyCallExpression)) {
            if (e.getText().equals(myKeywordContainerName))
              canRemove = false;
            for (PsiElement psiElement : e.getChildren()) {
              stack.push(psiElement);
            }
          }
          else {
            PyExpression callee = ((PyCallExpression)e).getCallee();
            if (callee instanceof PyQualifiedExpression) {
              PyExpression qualifier = ((PyQualifiedExpression)callee).getQualifier();
              if (qualifier != null && qualifier.getText().equals(myKeywordContainerName)
                                      && ("get".equals(((PyQualifiedExpression)callee).getReferencedName())
                                          || "__getitem__".equals(((PyQualifiedExpression)callee).getReferencedName()) )) {
                myCallElements.add((PyCallExpression)e);
              }
              else {
                if (e.getText().equals(myKeywordContainerName))
                  canRemove = false;
                for (PsiElement psiElement : e.getChildren()) {
                  stack.push(psiElement);
                }
              }
            }
          }
        }
      }
    }
  }

  private void replaceSubscriptions(Project project) {
    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    int size = myElements.size();
    for (int i = 0; i != size; ++i) {
      PySubscriptionExpression subscriptionExpression = myElements.get(i);
      PyExpression indexExpression = subscriptionExpression.getIndexExpression();
      if (indexExpression instanceof PyStringLiteralExpression) {
        PyExpression p = elementGenerator.createExpressionFromText(((PyStringLiteralExpression)indexExpression).getStringValue());
        ASTNode comma = elementGenerator.createComma();
        PyClass containingClass = myFunction.getContainingClass();

        if (containingClass == null) {
          myFunction.getParameterList().addBefore(p, myFunction.getParameterList().getParameters()[0]);
          myFunction.getParameterList().addBefore((PsiElement)comma, myFunction.getParameterList().getParameters()[0]);
        }
        else {
          myFunction.getParameterList().addBefore(p, myFunction.getParameterList().getParameters()[1]);
          myFunction.getParameterList().addBefore((PsiElement)comma, myFunction.getParameterList().getParameters()[1]);
        }
        subscriptionExpression.replace(p);
      }
    }
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    replaceSubscriptions(project);
    replaceCallElements(project);
    /*if (canRemove) {
      removeKwarg();
    }*/
    canRemove = true;
  }

  private void removeKwarg() {
    PsiElement prev = myKeywordContainer.getPrevSibling();
    if (prev instanceof PsiWhiteSpace) {
      PsiElement comma = prev.getPrevSibling();
      if (comma != null && comma.getNode().getElementType() == PyTokenTypes.COMMA) {
        comma.delete();
      }
    }
    else {
      if (prev != null && prev.getNode().getElementType() == PyTokenTypes.COMMA)
        prev.delete();
    }
    myKeywordContainer.delete();
  }


  private void replaceCallElements(Project project) {
    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    int size = myCallElements.size();
    for (int i = 0; i != size; ++i) {
      PyCallExpression callExpression = myCallElements.get(i);
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

        if (defaultValue != null)
          myFunction.getParameterList().addBefore(defaultValue, myKeywordContainer);
        else
          myFunction.getParameterList().addBefore(p, myKeywordContainer);

        myFunction.getParameterList().addBefore((PsiElement)comma, myKeywordContainer);

        callExpression.replace(p);
      }
    }
  }
}
