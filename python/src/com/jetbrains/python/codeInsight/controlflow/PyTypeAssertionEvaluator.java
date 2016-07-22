/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.codeInsight.controlflow;

import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * @author traff
 */
public class PyTypeAssertionEvaluator extends PyRecursiveElementVisitor {
  private Stack<Assertion> myStack = new Stack<>();
  private boolean myPositive;

  public PyTypeAssertionEvaluator() {
    this(true);
  }

  public PyTypeAssertionEvaluator(boolean positive) {
    myPositive = positive;
  }

  public List<Assertion> getDefinitions() {
    return myStack;
  }

  @Override
  public void visitPyPrefixExpression(PyPrefixExpression node) {
    if (node.getOperator() == PyTokenTypes.NOT_KEYWORD) {
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
        pushAssertion(target, new InstructionTypeCallback() {
          @Override
          public PyType getType(TypeEvalContext context, PsiElement anchor) {
            final List<PyType> types = new ArrayList<>();
            types.add(context.getType(typeElement));
            return createAssertionType(context.getType(target), types, positive, context);
          }
        });
      }
    }
    else if (node.isCalleeText(PyNames.CALLABLE_BUILTIN)) {
      final PyExpression[] args = node.getArguments();
      if (args.length == 1 && args[0] instanceof PyReferenceExpression) {
        final PyReferenceExpression target = (PyReferenceExpression)args[0];
        final boolean positive = myPositive;
        pushAssertion(target, new InstructionTypeCallback() {
          @Override
          public PyType getType(TypeEvalContext context, PsiElement anchor) {
            final List<PyType> types = new ArrayList<>();
            types.add(PyTypeParser.getTypeByName(target, "collections." + PyNames.CALLABLE));
            return createAssertionType(context.getType(target), types, positive, context);
          }
        });
      }
    }
  }

  @Override
  public void visitPyReferenceExpression(final PyReferenceExpression node) {
    if (node.getParent() instanceof PyIfPart) {
      final boolean positive = myPositive;
      pushAssertion(node, new InstructionTypeCallback() {
        @Override
        public PyType getType(TypeEvalContext context, PsiElement anchor) {
          final List<PyType> types = new ArrayList<>();
          types.add(PyNoneType.INSTANCE);
          return createAssertionType(context.getType(node), types, !positive, context);
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
          final boolean positive = myPositive;
          pushAssertion(target, new InstructionTypeCallback() {
            @Override
            public PyType getType(TypeEvalContext context, @Nullable PsiElement anchor) {
              final List<PyType> types = new ArrayList<>();
              types.add(PyNoneType.INSTANCE);
              return createAssertionType(context.getType(target), types, !positive, context);
            }
          });
          return;
        }
      }
    }
    super.visitPyBinaryExpression(node);
  }

  @Nullable
  private static PyType createAssertionType(PyType initial, List<PyType> types, boolean positive, TypeEvalContext context) {
    final List<PyType> members = new ArrayList<>();
    for (PyType t : types) {
      members.add(transformTypeFromAssertion(t));
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

  @Nullable
  private static PyType transformTypeFromAssertion(@Nullable PyType type) {
    if (type instanceof PyTupleType) {
      final List<PyType> members = new ArrayList<>();
      final PyTupleType tupleType = (PyTupleType)type;
      final int count = tupleType.getElementCount();
      for (int i = 0; i < count; i++) {
        members.add(transformTypeFromAssertion(tupleType.getElementType(i)));
      }
      return PyUnionType.union(members);
    }
    else if (type instanceof PyClassType) {
      return ((PyClassType)type).toInstance();
    }
    return type;
  }

  private void pushAssertion(PyReferenceExpression element, InstructionTypeCallback getType) {
    myStack.push(new Assertion(element, getType));
  }

  static class Assertion {
    private final PyReferenceExpression element;
    private InstructionTypeCallback myFunction;

    Assertion(PyReferenceExpression element, InstructionTypeCallback getType) {
      this.element = element;
      this.myFunction = getType;
    }

    public PyReferenceExpression getElement() {
      return element;
    }

    public InstructionTypeCallback getTypeEvalFunction() {
      return myFunction;
    }
  }
}
