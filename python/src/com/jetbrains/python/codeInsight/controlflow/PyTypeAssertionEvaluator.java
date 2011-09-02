package com.jetbrains.python.codeInsight.controlflow;

import com.intellij.util.Function;
import com.intellij.util.containers.CollectionFactory;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * @author traff
 */
public class PyTypeAssertionEvaluator extends PyRecursiveElementVisitor {
  private Stack<Assertion> myStack = CollectionFactory.stack();
  private boolean myPositive = true;

  public List<Assertion> getDefinitions() {
    return myStack;
  }

  @Override
  public void visitPyPrefixExpression(PyPrefixExpression node) {
    if (myPositive && node.getOperator() == PyTokenTypes.NOT_KEYWORD) {
      myPositive = !myPositive;
      super.visitPyPrefixExpression(node);
      myPositive = !myPositive;
    }
    else {
      super.visitPyPrefixExpression(node);
    }
  }

  @Override
  public void visitPyCallExpression(PyCallExpression node) {
    if (node.isCalleeText(PyNames.ISINSTANCE) || node.isCalleeText(PyNames.ASSERT_IS_INSTANCE)) {
      final PyExpression[] args = node.getArguments();
      if (args.length == 2 && args[0] instanceof PyReferenceExpression) {
        final PyReferenceExpression target = (PyReferenceExpression)args[0];
        final PyExpression typeElement = args[1];
        final boolean positive = myPositive;
        if (!processTuple(target, typeElement)) {
          pushAssertion(target, new Function<TypeEvalContext, PyType>() {
            @Override
            public PyType fun(TypeEvalContext context) {
              final List<PyType> types = new ArrayList<PyType>();
              types.add(context.getType(typeElement));
              return createAssertionType(context.getType(target), types, positive, context);
            }
          });
        }
      }
    }
    else if (node.isCalleeText(PyNames.CALLABLE_BUILTIN)) {
      final PyExpression[] args = node.getArguments();
      if (args.length == 1 && args[0] instanceof PyReferenceExpression) {
        final PyReferenceExpression target = (PyReferenceExpression)args[0];
        final boolean positive = myPositive;
        pushAssertion(target, new Function<TypeEvalContext, PyType>() {
          @Override
          public PyType fun(TypeEvalContext context) {
            final List<PyType> types = new ArrayList<PyType>();
            types.add(PyTypeParser.getTypeByName(target, PyNames.CALLABLE));
            return createAssertionType(context.getType(target), types, positive, context);
          }
        });
      }
    }
  }

  @Override
  public void visitPyReferenceExpression(final PyReferenceExpression node) {
    if (node.getParent() instanceof PyIfPart) {
      pushAssertion(node, new Function<TypeEvalContext, PyType>() {
        @Override
        public PyType fun(TypeEvalContext context) {
          final List<PyType> types = new ArrayList<PyType>();
          types.add(PyNoneType.INSTANCE);
          return createAssertionType(context.getType(node), types, false, context);
        }
      });
      return;
    }
    super.visitPyReferenceExpression(node);
  }

  @Override
  public void visitPyBinaryExpression(PyBinaryExpression node) {
    if (node.isOperator("isnot")) {
      final PyExpression lhs = node.getLeftExpression();
      final PyExpression rhs = node.getRightExpression();
      if (lhs instanceof PyReferenceExpression && rhs instanceof PyReferenceExpression) {
        final PyReferenceExpression target = (PyReferenceExpression)lhs;
        if (PyNames.NONE.equals(rhs.getName())) {
          pushAssertion(target, new Function<TypeEvalContext, PyType>() {
            @Override
            public PyType fun(TypeEvalContext context) {
              final List<PyType> types = new ArrayList<PyType>();
              types.add(PyNoneType.INSTANCE);
              return createAssertionType(context.getType(target), types, false, context);
            }
          });
          return;
        }
      }
    }
    super.visitPyBinaryExpression(node);
  }

  private boolean processTuple(final PyReferenceExpression target, PyExpression typeElement) {
    boolean pushed = false;
    if (typeElement instanceof PyParenthesizedExpression) {
      final PyExpression contained = ((PyParenthesizedExpression)typeElement).getContainedExpression();
      if (contained instanceof PyTupleExpression) {
        final PyTupleExpression tuple = (PyTupleExpression)contained;
        final boolean positive = myPositive;
        pushAssertion(target, new Function<TypeEvalContext, PyType>() {
          @Override
          public PyType fun(TypeEvalContext context) {
            final List<PyType> types = new ArrayList<PyType>();
            for (PyExpression e : tuple.getElements()) {
              types.add(context.getType(e));
            }
            return createAssertionType(context.getType(target), types, positive, context);
          }
        });
        pushed = true;
      }
    }
    return pushed;
  }

  private static PyType createAssertionType(PyType initial, List<PyType> types, boolean positive, TypeEvalContext context) {
    final List<PyType> members = new ArrayList<PyType>();
    for (PyType t : types) {
      members.add(t instanceof PyClassType ? ((PyClassType)t).toInstance() : t);
    }
    final PyType union = PyUnionType.union(members);
    if (positive) {
      return union;
    }
    else if (initial instanceof PyUnionType) {
      return ((PyUnionType)initial).exclude(union, context);
    }
    else if (PyTypeChecker.match(union, initial, context)) {
      return null;
    }
    return initial;
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
