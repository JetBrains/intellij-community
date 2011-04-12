package com.jetbrains.python.codeInsight.controlflow;

import com.intellij.psi.PsiElement;
import com.intellij.util.containers.CollectionFactory;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;

import java.util.List;
import java.util.Stack;

/**
 * @author traff
 */
public class PyTypeAssertionEvaluator extends PyRecursiveElementVisitor {

  private Stack<Assertion> myStack = CollectionFactory.stack();

  public PyTypeAssertionEvaluator() {
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
    if (node.isCalleeText(PyNames.ISINSTANCE)) {
      PyExpression[] args = node.getArguments();
      if (args.length == 2 && args[0] instanceof PyReferenceExpression) {
        PyReferenceExpression target = (PyReferenceExpression)args[0];
        PyExpression typeElement = args[1];

        if (!processTuple(target, typeElement)) {
          pushTypeElement(target, typeElement);
        }
      }
    }
  }

  private boolean processTuple(PyReferenceExpression target, PyExpression typeElement) {
    boolean pushed = false;
    if (typeElement instanceof PyParenthesizedExpression) {
      PyExpression contained = ((PyParenthesizedExpression)typeElement).getContainedExpression();
      if (contained instanceof PyTupleExpression) {
        for (PyExpression e : ((PyTupleExpression)contained).getElements()) {
          pushTypeElement(target, e);
          pushed = true;
        }
      }
    }
    return pushed;
  }

  private void pushTypeElement(PyReferenceExpression target, PyExpression typeElement) {
    Assertion o = new Assertion(target.getName(), typeElement);
    myStack.push(o);
  }

  static class Assertion {
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
