// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.controlflow;

import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Stack;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.codeInsight.stdlib.PyStdlibTypeProvider;
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
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

import static com.jetbrains.python.psi.PyUtil.as;

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
      if (args.length == 2) {
        final PyExpression typeElement = args[1];

        pushAssertion(args[0], myPositive, context ->
          transformTypeFromAssertion(context.getType(typeElement), false, context, typeElement));
      }
    }
    else if (node.isCalleeText(PyNames.CALLABLE_BUILTIN)) {
      final PyExpression[] args = node.getArguments();
      if (args.length == 1) {
        pushAssertion(args[0], myPositive, context -> PyTypingTypeProvider.createTypingCallableType(node));
      }
    }
    else if (node.isCalleeText(PyNames.ISSUBCLASS)) {
      final PyExpression[] args = node.getArguments();
      if (args.length == 2) {
        final PyExpression typeElement = args[1];

        pushAssertion(args[0], myPositive, context ->
          transformTypeFromAssertion(context.getType(typeElement), true, context, typeElement));
      }
    }
  }

  private void visitExpressionInCondition(@NotNull PyExpression node) {
    if (myPositive && (isIfReferenceStatement(node) || isIfReferenceConditionalStatement(node) || isIfNotReferenceStatement(node))) {
      // we could not suggest `None` because it could be a reference to an empty collection
      // so we could push only non-`None` assertions
      pushAssertion(node, !myPositive, context -> PyBuiltinCache.getInstance(node).getNoneType());
    }
  }

  @Override
  public void visitPyReferenceExpression(final @NotNull PyReferenceExpression node) {
    visitExpressionInCondition(node);
    super.visitPyReferenceExpression(node);
  }

  @Override
  public void visitPyAssignmentExpression(@NotNull PyAssignmentExpression node) {
    visitExpressionInCondition(node);
    super.visitPyAssignmentExpression(node);
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
      pushAssertion(rhs, myPositive, context -> PyBuiltinCache.getInstance(rhs).getNoneType());
      return;
    }

    if (PyLiteralType.isNone(rhs)) {
      pushAssertion(lhs, myPositive, context -> PyBuiltinCache.getInstance(lhs).getNoneType());
      return;
    }

    pushAssertion(lhs, myPositive, context -> getLiteralType(rhs, context));
  }

  private void processIn(@NotNull PyExpression lhs, @NotNull PyExpression rhs) {
    if (rhs instanceof PyTupleExpression tupleExpr) {
      pushAssertion(lhs, myPositive, (TypeEvalContext context) -> {
        PyExpression[] elements = tupleExpr.getElements();
        List<PyType> types = new ArrayList<>(elements.length);
        for (PyExpression element : elements) {
          PyType type = PyLiteralType.isNone(element) ? PyBuiltinCache.getInstance(element).getNoneType() : getLiteralType(element, context);
          if (type == null) {
            return null;
          }
          types.add(type);
        }
        return PyUnionType.union(types);
      });
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

  @Override
  public void visitPyCaseClause(@NotNull PyCaseClause node) {
    var pattern = node.getPattern();
    if (pattern == null) return;
    if (node.getParent() instanceof PyMatchStatement matchStatement) {
      pushAssertion(matchStatement.getSubject(), myPositive, context -> context.getType(pattern));
    }
  }

  /**
   * Negative type assertion for when all cases fail
   */
  @Override
  public void visitPyMatchStatement(@NotNull PyMatchStatement matchStatement) {
    assert !myPositive; // for match statement as a whole, only negative assertion can be made
    final PyExpression subject = matchStatement.getSubject();
    if (subject == null) return;
    // allowAnyExpr is here because we need negative edges with Never even when subject is not reference expression
    pushAssertion(subject, true, true, context -> {
      PyType subjectType = context.getType(subject);
      for (PyCaseClause cs : matchStatement.getCaseClauses()) {
        if (cs.getPattern() == null) continue;
        if (cs.getGuardCondition() != null) continue;
        if (cs.getPattern().isIrrefutable()) {
          subjectType = PyNeverType.NEVER;
          break;
        }
        subjectType = Ref.deref(createAssertionType(subjectType, context.getType(cs.getPattern()), false, context));
      }

      return subjectType;
    });
  }

  @ApiStatus.Internal
  public static @Nullable Ref<PyType> createAssertionType(@Nullable PyType initial,
                                                          @Nullable PyType suggested,
                                                          boolean positive,
                                                          @NotNull TypeEvalContext context) {
    if (suggested == null) return null;
    if (positive) {
      List<PyType> initialSubtypes = PyTypeUtil.toStream(initial)
        .filter(initialSubtype -> match(suggested, initialSubtype, context))
        .toList();

      StreamEx<PyType> suggestedSubtypes = PyTypeUtil.toStream(suggested)
        .filter(suggestedSubtype -> match(initial, suggestedSubtype, context))
        .filter(suggestedSubtype -> !ContainerUtil.exists(initialSubtypes,
                                                          initialSubtype -> match(initialSubtype, suggestedSubtype, context)));

      List<PyType> types = StreamEx.of(initialSubtypes).append(suggestedSubtypes).toList();
      return Ref.create(types.isEmpty() ? intersect(initial, suggested) : PyUnionType.union(types));
    }
    else {
      if (initial instanceof PyUnionType unionType) {
        return Ref.create(excludeFromUnion(unionType, suggested, context));
      }
      if (match(suggested, initial, context)) {
        return Ref.create(PyNeverType.NEVER);
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
      else if (!match(type, m, context)) {
        members.add(m);
      }
    }
    if (members.isEmpty()) {
      return PyNeverType.NEVER;
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
      if (filteredEnumMembers.isEmpty()) {
        return Ref.create(PyNeverType.NEVER);
      }
      PyType type = enumMembers.size() == filteredEnumMembers.size() ? type1 : PyUnionType.union(filteredEnumMembers);
      return Ref.create(type);
    }
    return null;
  }
  
  private static @Nullable PyType intersect(@Nullable PyType initial, @Nullable PyType suggested) {
    // TODO: if we had IntersectionType, here it would be created. Also, final classes can be handled here
    if (initial instanceof PyNeverType) {
      return initial;
    }
    // While we don't have IntersectionType, return suggested
    return suggested;
  }

  private static boolean match(@Nullable PyType expected, @Nullable PyType actual, @NotNull TypeEvalContext context) {
    return !(actual instanceof PyStructuralType) &&
           !PyTypeChecker.isUnknown(actual, context) &&
           PyTypeChecker.match(expected, actual, context);
  }

  /**
   * @param transformToDefinition if true the result type will be Type[T], not T itself.
   */
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

      final PyTupleExpression tupleExpression = as(PyPsiUtils.flattenParens(typeElement), PyTupleExpression.class);
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

  private void pushAssertion(@Nullable PyExpression expr, boolean positive, @NotNull Function<TypeEvalContext, PyType> suggestedType) {
    pushAssertion(expr, positive, false, suggestedType);
  }

  private void pushAssertion(@Nullable PyExpression expr, boolean positive, boolean allowAnyExpr, @NotNull Function<TypeEvalContext, PyType> suggestedType) {
    expr = PyPsiUtils.flattenParens(expr);
    if (expr instanceof PyAssignmentExpression walrus) {
      pushAssertion(walrus.getTarget(), positive, allowAnyExpr, suggestedType);
    }
    else if (expr != null) {
      final var target = expr;
      final InstructionTypeCallback typeCallback = new InstructionTypeCallback() {
        @Override
        public Ref<PyType> getType(TypeEvalContext context) {
          return createAssertionType(context.getType(target), suggestedType.apply(context), positive, context);
        }
      };

      if (expr instanceof PyReferenceExpression || expr instanceof PyTargetExpression) {
        myStack.push(new Assertion((PyQualifiedExpression)target, typeCallback));
      }
      else if (allowAnyExpr) {
        myStack.push(new Assertion(null, typeCallback));
      }
    }
  }

  public static @Nullable String getAssertionTargetName(@Nullable PyExpression expression) {
    PyExpression target = PyPsiUtils.flattenParens(expression);
    if (target instanceof PyAssignmentExpression walrus) {
      return getAssertionTargetName(walrus.getTarget());
    }
    if (target instanceof PyReferenceExpression || target instanceof PyTargetExpression) {
      if (!((PyQualifiedExpression)target).isQualified()) {
        return target.getName();
      }
    }
    return null;
  }

  private static boolean isIfReferenceStatement(@NotNull PyExpression node) {
    return PsiTreeUtil.skipParentsOfType(node, PyParenthesizedExpression.class) instanceof PyIfPart;
  }

  private static boolean isIfReferenceConditionalStatement(@NotNull PyExpression node) {
    final PsiElement parent = PsiTreeUtil.skipParentsOfType(node, PyParenthesizedExpression.class); 
    return parent instanceof PyConditionalExpression cond &&
           PsiTreeUtil.isAncestor(cond.getCondition(), node, false);
  }

  private static boolean isIfNotReferenceStatement(@NotNull PyExpression node) {
    final PsiElement parent = PsiTreeUtil.skipParentsOfType(node, PyParenthesizedExpression.class);
    return parent instanceof PyPrefixExpression &&
           ((PyPrefixExpression)parent).getOperator() == PyTokenTypes.NOT_KEYWORD &&
           parent.getParent() instanceof PyIfPart;
  }

  static class Assertion {
    private final @Nullable PyQualifiedExpression element;
    private final @NotNull InstructionTypeCallback myFunction;

    Assertion(@Nullable PyQualifiedExpression element, @NotNull InstructionTypeCallback getType) {
      this.element = element;
      this.myFunction = getType;
    }

    public @Nullable PyQualifiedExpression getElement() {
      return element;
    }

    public @NotNull InstructionTypeCallback getTypeEvalFunction() {
      return myFunction;
    }
  }
}