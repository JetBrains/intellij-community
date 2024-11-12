// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.controlflow;

import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Stack;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyEvaluator;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class PyTypeAssertionEvaluator extends PyRecursiveElementVisitor {
  private final Stack<Assertion> myStack = new Stack<>();
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
  public void visitPyPrefixExpression(@NotNull PyPrefixExpression node) {
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
  public void visitPyCallExpression(@NotNull PyCallExpression node) {
    if (node.isCalleeText(PyNames.ISINSTANCE, PyNames.ASSERT_IS_INSTANCE)) {
      final PyExpression[] args = node.getArguments();
      if (args.length == 2 && args[0] instanceof PyReferenceExpression target) {
        final PyExpression typeElement = args[1];

        pushAssertion(target, myPositive, false, context -> context.getType(typeElement), typeElement);
      }
    }
    else if (node.isCalleeText(PyNames.CALLABLE_BUILTIN)) {
      final PyExpression[] args = node.getArguments();
      if (args.length == 1 && args[0] instanceof PyReferenceExpression target) {

        pushAssertion(target, myPositive, false, context -> PyTypingTypeProvider.createTypingCallableType(node), null);
      }
    }
    else if (node.isCalleeText(PyNames.ISSUBCLASS)) {
      final PyExpression[] args = node.getArguments();
      if (args.length == 2 && args[0] instanceof PyReferenceExpression target) {
        final PyExpression typeElement = args[1];

        pushAssertion(target, myPositive, true, context -> context.getType(typeElement), typeElement);
      }
    }
  }

  @Override
  public void visitPyReferenceExpression(final @NotNull PyReferenceExpression node) {
    if (myPositive && (isIfReferenceStatement(node) || isIfReferenceConditionalStatement(node) || isIfNotReferenceStatement(node))) {
      // we could not suggest `None` because it could be a reference to an empty collection
      // so we could push only non-`None` assertions
      pushAssertion(node, !myPositive, false, context -> PyNoneType.INSTANCE, null);
      return;
    }

    super.visitPyReferenceExpression(node);
  }

  @Override
  public void visitPyBinaryExpression(@NotNull PyBinaryExpression node) {
    final PyExpression lhs = node.getLeftExpression();
    final PyExpression rhs = node.getRightExpression();

    if (lhs instanceof PyReferenceExpression && rhs instanceof PyReferenceExpression ||
        lhs instanceof PyReferenceExpression && rhs instanceof PyNoneLiteralExpression ||
        lhs instanceof PyNoneLiteralExpression && rhs instanceof PyReferenceExpression) {
      final boolean leftIsNone = lhs instanceof PyNoneLiteralExpression || PyNames.NONE.equals(lhs.getName());
      final boolean rightIsNone = rhs instanceof PyNoneLiteralExpression || PyNames.NONE.equals(rhs.getName());

      if (leftIsNone ^ rightIsNone) {
        final PyReferenceExpression target = (PyReferenceExpression)(rightIsNone ? lhs : rhs);

        if (node.isOperator(PyNames.IS)) {
          pushAssertion(target, myPositive, false, context -> PyNoneType.INSTANCE, null);
          return;
        }

        if (node.isOperator("isnot")) {
          pushAssertion(target, !myPositive, false, context -> PyNoneType.INSTANCE, null);
          return;
        }
      }
    }

    final Object leftValue = PyEvaluator.evaluateNoResolve(lhs, Object.class);
    final Object rightValue = PyEvaluator.evaluateNoResolve(rhs, Object.class);

    if (leftValue instanceof Boolean && rightValue instanceof Boolean) {
      return;
    }

    if (node.isOperator(PyNames.IS) && (leftValue == Boolean.FALSE || rightValue == Boolean.FALSE) ||
        node.isOperator("isnot") && (leftValue == Boolean.TRUE || rightValue == Boolean.TRUE)) {
      myPositive = !myPositive;
      super.visitPyBinaryExpression(node);
      myPositive = !myPositive;
      return;
    }

    super.visitPyBinaryExpression(node);
  }

  @ApiStatus.Internal
  public static @Nullable Ref<PyType> createAssertionType(@Nullable PyType initial,
                                                          @Nullable PyType suggested,
                                                          boolean positive,
                                                          @NotNull TypeEvalContext context) {
    if (positive) {
      if (!(initial instanceof PyUnionType) && match(suggested, initial, context)) {
        return Ref.create(initial);
      }
      if (initial instanceof PyUnionType unionType) {
        if (!unionType.isWeak()) {
          var matched = ContainerUtil.filter(unionType.getMembers(), (member) -> match(suggested, member, context));
          if (!matched.isEmpty()) {
            return Ref.create(PyUnionType.union(matched));
          }
        }
      }
      return Ref.create(suggested);
    }
    else if (initial instanceof PyUnionType unionType) {
      return Ref.create(unionType.exclude(suggested, context));
    }
    else if (match(suggested, initial, context)) {
      return null;
    }
    return Ref.create(initial);
  }

  private static boolean match(@Nullable PyType expected, @Nullable PyType actual, @NotNull TypeEvalContext context) {
    return !(actual instanceof PyStructuralType) &&
           !PyTypeChecker.isUnknown(actual, context) &&
           PyTypeChecker.match(expected, actual, context);
  }

  @Nullable
  private static PyType transformTypeFromAssertion(@Nullable PyType type, boolean transformToDefinition, @NotNull TypeEvalContext context,
                                                   @Nullable PyExpression typeElement) {
    /*
     * We need to distinguish:
     *   if isinstance(x, (int, str)):
     * And:
     *   if isinstance(x, (1, "")):
     */
    if (type instanceof PyTupleType tupleType) {
      final List<PyType> members = new ArrayList<>();
      final int count = tupleType.getElementCount();

      final PyTupleExpression tupleExpression = PyUtil
        .as(PyPsiUtils.flattenParens(PyUtil.as(typeElement, PyParenthesizedExpression.class)), PyTupleExpression.class);
      if (tupleExpression != null && tupleExpression.getElements().length == count) {
        final PyExpression[] elements = tupleExpression.getElements();
        for (int i = 0; i < count; i++) {
          members.add(transformTypeFromAssertion(tupleType.getElementType(i), transformToDefinition, context, elements[i]));
        }
      }
      else {
        for (int i = 0; i < count; i++) {
          members.add(transformTypeFromAssertion(tupleType.getElementType(i), transformToDefinition, context, null));
        }
      }

      return PyUnionType.union(members);
    }
    else if (type instanceof PyUnionType) {
      return ((PyUnionType)type).map(member -> transformTypeFromAssertion(member, transformToDefinition, context, null));
    }
    else if (type instanceof PyClassType && "types.UnionType".equals(((PyClassType)type).getClassQName()) && typeElement != null) {
      final Ref<PyType> typeFromTypingProvider = PyTypingTypeProvider.getType(typeElement, context);
      if (typeFromTypingProvider != null) {
        return transformTypeFromAssertion(typeFromTypingProvider.get(), transformToDefinition, context, null);
      }
    }
    else if (type instanceof PyInstantiableType instantiableType) {
      return transformToDefinition ? instantiableType.toClass() : instantiableType.toInstance();
    }
    return type;
  }

  /**
   * @param transformToDefinition if true the result type will be Type[T], not T itself.
   */
  private void pushAssertion(@NotNull PyReferenceExpression target,
                             boolean positive,
                             boolean transformToDefinition,
                             @NotNull Function<TypeEvalContext, PyType> suggestedType,
                             @Nullable PyExpression typeElement) {
    final InstructionTypeCallback typeCallback = new InstructionTypeCallback() {
      @Override
      public Ref<PyType> getType(TypeEvalContext context, @Nullable PsiElement anchor) {
        return createAssertionType(context.getType(target),
                                   transformTypeFromAssertion(suggestedType.apply(context), transformToDefinition, context, typeElement),
                                   positive,
                                   context);
      }
    };

    myStack.push(new Assertion(target, typeCallback));
  }

  private static boolean isIfReferenceStatement(@NotNull PyReferenceExpression node) {
    return node.getParent() instanceof PyIfPart;
  }

  private static boolean isIfReferenceConditionalStatement(@NotNull PyReferenceExpression node) {
    final PsiElement parent = node.getParent();
    return parent instanceof PyConditionalExpression &&
           node == ((PyConditionalExpression)parent).getCondition();
  }

  private static boolean isIfNotReferenceStatement(@NotNull PyReferenceExpression node) {
    final PsiElement parent = node.getParent();
    return parent instanceof PyPrefixExpression &&
           ((PyPrefixExpression)parent).getOperator() == PyTokenTypes.NOT_KEYWORD &&
           parent.getParent() instanceof PyIfPart;
  }

  static class Assertion {
    private final PyReferenceExpression element;
    private final InstructionTypeCallback myFunction;

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
