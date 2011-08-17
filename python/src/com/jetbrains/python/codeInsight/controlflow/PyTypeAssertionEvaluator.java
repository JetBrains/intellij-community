package com.jetbrains.python.codeInsight.controlflow;

import com.intellij.psi.PsiElement;
import com.intellij.util.Function;
import com.intellij.util.containers.CollectionFactory;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyUnionType;
import com.jetbrains.python.psi.types.TypeEvalContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * @author traff
 */
public class PyTypeAssertionEvaluator extends PyRecursiveElementVisitor {
  private Stack<Assertion> myStack = CollectionFactory.stack();

  public List<Assertion> getDefinitions() {
    return myStack;
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
      final PyExpression[] args = node.getArguments();
      if (args.length == 2 && args[0] instanceof PyReferenceExpression) {
        final PyReferenceExpression target = (PyReferenceExpression)args[0];
        final PyExpression typeElement = args[1];
        if (!processTuple(target, typeElement)) {
          pushAssertion(target, new Function<TypeEvalContext, PyType>() {
            @Override
            public PyType fun(TypeEvalContext context) {
              final PyType t = context.getType(typeElement);
              return t instanceof PyClassType ? ((PyClassType)t).toInstance() : t;
            }
          });
        }
      }
    }
  }

  private boolean processTuple(PyReferenceExpression target, PyExpression typeElement) {
    boolean pushed = false;
    if (typeElement instanceof PyParenthesizedExpression) {
      final PyExpression contained = ((PyParenthesizedExpression)typeElement).getContainedExpression();
      if (contained instanceof PyTupleExpression) {
        final PyTupleExpression tuple = (PyTupleExpression)contained;
        pushAssertion(target, new Function<TypeEvalContext, PyType>() {
          @Override
          public PyType fun(TypeEvalContext context) {
            final List<PyType> types = new ArrayList<PyType>();
            for (PyExpression e : tuple.getElements()) {
              final PyType t = context.getType(e);
              types.add(t instanceof PyClassType ? ((PyClassType)t).toInstance() : t);
            }
            return PyUnionType.union(types);
          }
        });
        pushed = true;
      }
    }
    return pushed;
  }

  private void pushAssertion(PyReferenceExpression element, Function<TypeEvalContext, PyType> getType) {
    myStack.push(new Assertion(element, getType));
  }

  static class Assertion {
    private final PyReferenceExpression element;
    private Function<TypeEvalContext, PyType> myFunction;

    Assertion(PyReferenceExpression element, Function<TypeEvalContext, PyType> getType) {
      this.element = element;
      this.myFunction = getType;
    }

    public PyReferenceExpression getElement() {
      return element;
    }

    public Function<TypeEvalContext, PyType> getTypeEvalFunction() {
      return myFunction;
    }
  }
}
