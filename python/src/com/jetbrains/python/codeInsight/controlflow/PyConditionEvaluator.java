package com.jetbrains.python.codeInsight.controlflow;

import com.intellij.psi.PsiElement;
import com.intellij.util.containers.CollectionFactory;
import com.jetbrains.python.psi.*;

import java.util.List;
import java.util.Stack;

/**
 * @author traff
 */
public class PyConditionEvaluator extends PyRecursiveElementVisitor {

  private Stack<Definition> myStack = CollectionFactory.stack();

  public PyConditionEvaluator() {
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
        Definition o = new Definition(target.getName(), args[1]);
        myStack.push(o);
      }
    }
  }

  class Definition {
    private final String name;
    private final PyElement element;

    Definition(String name, PyElement element) {
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

  public List<Definition> getDefinitions() {
    return myStack;
  }
}
