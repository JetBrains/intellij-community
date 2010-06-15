package com.jetbrains.python.codeInsight.controlflow;

import com.intellij.psi.PsiElement;
import com.intellij.util.containers.CollectionFactory;
import com.jetbrains.python.psi.*;

import java.util.List;
import java.util.Stack;

/**
 * @author traff
 */
public class PyAssertionEvaluator extends PyRecursiveElementVisitor {

  private Stack<Assertion> myStack = CollectionFactory.stack();

  public PyAssertionEvaluator() {
  }


  @Override
  public void visitPyBinaryExpression(PyBinaryExpression node) {
    PsiElement child = node.getFirstChild();
    while (child != null) {
      if (child instanceof PyExpression) {
        child.accept(this);
      }
      child = child.getNextSibling();
    }
  }

  @Override
  public void visitPyCallExpression(PyCallExpression node) {
    if ("isinstance".equals(node.getCallee().getText())) {
      PyExpression[] args = node.getArguments();
      if (args.length == 2 && args[0] instanceof PyReferenceExpression) {
        PyReferenceExpression target = (PyReferenceExpression)args[0];
        Assertion o = new Assertion(target.getName(), args[1]);
        myStack.push(o);
      }
    }
  }

  class Assertion {
    private final String name;
    private final PyElement element;

    Assertion(String name, PyElement element) {
      this.name = name;
      this.element = element;
    }

    public String getName() {
      return name;
    }

    public PyElement getElement() {
      return element;
    }
  }

  public List<Assertion> getDefinitions() {
    return myStack;
  }
}
