// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.controlflow;

import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Stack;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.codeInsight.stdlib.PyStdlibTypeProvider;
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyEvaluator;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.types.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class PyTypeAssertionEvaluator extends PyRecursiveElementVisitor {
  private final Stack<Assertion> myStack = new Stack<>();
  private boolean myPositive;

  public PyTypeAssertionEvaluator(boolean positive) {
    myPositive = positive;
  }

  List<Assertion> getDefinitions() {
    return myStack;
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
    final PyExpression lhs = PyPsiUtils.flattenParens(node.getLeftExpression());
    final PyExpression rhs = PyPsiUtils.flattenParens(node.getRightExpression());
    if (lhs == null || rhs == null) return;

    PyElementType operator = node.getOperator();
    boolean isOrEqualsOperator = node.isOperator(PyNames.IS) || PyTokenTypes.EQEQ.equals(operator);
    if (isOrEqualsOperator || node.isOperator("isnot") || PyTokenTypes.NE.equals(operator) || PyTokenTypes.NE_OLD.equals(operator)) {
      setPositive(isOrEqualsOperator, () -> processIsOrEquals(lhs, rhs));
    }

    if (PyTokenTypes.IN_KEYWORD.equals(operator) || node.isOperator("notin")) {
      setPositive(PyTokenTypes.IN_KEYWORD.equals(operator), () -> processIn(lhs, rhs));
    }
  }

  private void processIsOrEquals(@NotNull PyExpression lhs, @NotNull PyExpression rhs) {
    final Boolean leftBoolean = PyEvaluator.evaluateNoResolve(lhs, Boolean.class);
    if (leftBoolean != null) {
      setPositive(leftBoolean, () -> rhs.accept(this));
      return;
    }

    final Boolean rightBoolean = PyEvaluator.evaluateNoResolve(rhs, Boolean.class);
    if (rightBoolean != null) {
      setPositive(rightBoolean, () -> lhs.accept(this));
      return;
    }

    if (PyLiteralType.isNone(lhs)) {
      if (rhs instanceof PyReferenceExpression referenceExpr) {
        pushAssertion(referenceExpr, myPositive, false, context -> PyNoneType.INSTANCE, null);
      }
      return;
    }

    if (PyLiteralType.isNone(rhs)) {
      if (lhs instanceof PyReferenceExpression referenceExpr) {
        pushAssertion(referenceExpr, myPositive, false, context -> PyNoneType.INSTANCE, null);
      }
      return;
    }

    if (lhs instanceof PyReferenceExpression referenceExpr) {
      pushAssertion(referenceExpr, myPositive, false, context -> getLiteralType(rhs, context), null);
    }
  }

  private void processIn(@NotNull PyExpression lhs, @NotNull PyExpression rhs) {
    if (lhs instanceof PyReferenceExpression referenceExpr && rhs instanceof PyTupleExpression tupleExpr) {
      pushAssertion(referenceExpr, myPositive, false, context -> {
        PyExpression[] elements = tupleExpr.getElements();
        List<PyType> types = new ArrayList<>(elements.length);
        for (PyExpression element : elements) {
          PyType type = PyLiteralType.isNone(element) ? PyNoneType.INSTANCE : getLiteralType(element, context);
          if (type == null) {
            return null;
          }
          types.add(type);
        }
        return PyUnionType.union(types);
      }, null);
    }
  }

  private static @Nullable PyType getLiteralType(@NotNull PyExpression element, @NotNull TypeEvalContext context) {
    PyType type = PyLiteralType.getLiteralType(element, context);
    if (type == null) {
      type = context.getType(element);
    }
    return PyTypeUtil.toStream(type).allMatch(subtype -> subtype instanceof PyLiteralType) ? type : null;
  }

  private void setPositive(boolean positive, @NotNull Runnable runnable) {
    boolean oldPositive = myPositive;
    if (!positive) {
      myPositive = !myPositive;
    }
    try {
      runnable.run();
    }
    finally {
      myPositive = oldPositive;
    }
  }

  @ApiStatus.Internal
  public static @Nullable Ref<PyType> createAssertionType(@Nullable PyType initial,
                                                          @Nullable PyType suggested,
                                                          boolean positive,
                                                          @NotNull TypeEvalContext context) {
    if (positive) {
      List<PyType> initialSubtypes = PyTypeUtil.toStream(initial)
        .filter(initialSubtype -> match(suggested, initialSubtype, context))
        .toList();

      StreamEx<PyType> suggestedSubtypes = PyTypeUtil.toStream(suggested)
        .filter(suggestedSubtype -> match(initial, suggestedSubtype, context))
        .filter(suggestedSubtype -> !ContainerUtil.exists(initialSubtypes,
                                                          initialSubtype -> match(initialSubtype, suggestedSubtype, context)));

      List<PyType> types = StreamEx.of(initialSubtypes).append(suggestedSubtypes).toList();
      return Ref.create(types.isEmpty() ? suggested : PyUnionType.union(types));
    }
    else {
      if (initial instanceof PyUnionType unionType) {
        return Ref.create(excludeFromUnion(unionType, suggested, context));
      }
      if (match(suggested, initial, context)) {
        return null;
      }
      Ref<@Nullable PyType> diff = trySubtract(initial, suggested, context);
      return diff != null ? diff : Ref.create(initial);
    }
  }

  private static @Nullable PyType excludeFromUnion(@NotNull PyUnionType unionType,
                                                   @Nullable PyType type,
                                                   @NotNull TypeEvalContext context) {
    final List<PyType> members = new ArrayList<>();
    for (PyType m : unionType.getMembers()) {
      Ref<@Nullable PyType> diff = trySubtract(m, type, context);
      if (diff != null) {
        members.add(diff.get());
      }
      else if (!PyTypeChecker.match(type, m, context)) {
        members.add(m);
      }
    }
    return PyUnionType.union(members);
  }

  private static @Nullable Ref<@Nullable PyType> trySubtract(@Nullable PyType type1,
                                                             @Nullable PyType type2,
                                                             @NotNull TypeEvalContext context) {
    assert !(type1 instanceof PyUnionType);

    if (!(type1 instanceof PyLiteralType) &&
        type1 instanceof PyClassType classType1 &&
        PyStdlibTypeProvider.isCustomEnum(classType1.getPyClass(), context)) {
      if (ContainerUtil.exists(classType1.getPyClass().getAncestorClasses(context),
                               cls -> PyNames.TYPE_ENUM_FLAG.equals(cls.getQualifiedName()))) {
        // Do not expand enum classes that derive from enum.Flag
        return null;
      }
      List<PyLiteralType> enumMembers = PyStdlibTypeProvider.getEnumMembers(classType1.getPyClass(), context).toList();
      List<PyType> filteredEnumMembers = ContainerUtil.filter(enumMembers, m -> !PyTypeChecker.match(type2, m, context));
      PyType type = enumMembers.size() == filteredEnumMembers.size() ? type1 : PyUnionType.union(filteredEnumMembers);
      return Ref.create(type);
    }
    return null;
  }

  private static boolean match(@Nullable PyType expected, @Nullable PyType actual, @NotNull TypeEvalContext context) {
    return !(actual instanceof PyStructuralType) &&
           !PyTypeChecker.isUnknown(actual, context) &&
           PyTypeChecker.match(expected, actual, context);
  }

  private static @Nullable PyType transformTypeFromAssertion(@Nullable PyType type, boolean transformToDefinition, @NotNull TypeEvalContext context,
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
      public Ref<PyType> getType(TypeEvalContext context) {
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
