// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.controlflow;

import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Stack;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.codeInsight.stdlib.PyStdlibTypeProvider;
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.psi.PyAssertStatement;
import com.jetbrains.python.psi.PyAssignmentExpression;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyCaseClause;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyConditionalExpression;
import com.jetbrains.python.psi.PyConditionalStatementPart;
import com.jetbrains.python.psi.PyElementType;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyGeneratorExpression;
import com.jetbrains.python.psi.PyListLiteralExpression;
import com.jetbrains.python.psi.PyMatchStatement;
import com.jetbrains.python.psi.PyParenthesizedExpression;
import com.jetbrains.python.psi.PyPrefixExpression;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.PyRecursiveElementVisitor;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PySequenceExpression;
import com.jetbrains.python.psi.PySetLiteralExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyTupleExpression;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyEvaluator;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyInstantiableType;
import com.jetbrains.python.psi.types.PyLiteralType;
import com.jetbrains.python.psi.types.PyNeverType;
import com.jetbrains.python.psi.types.PyStructuralType;
import com.jetbrains.python.psi.types.PyTupleType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeChecker;
import com.jetbrains.python.psi.types.PyTypeUtil;
import com.jetbrains.python.psi.types.PyUnionType;
import com.jetbrains.python.psi.types.TypeEvalContext;
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

        pushAssertion(args[0], myPositive, context -> {
          if (myPositive || isSafeForNegativeAssertion(typeElement, context)) {
            return transformTypeFromAssertion(context.getType(typeElement), false, context, typeElement);
          }
          return null;
        });
      }
    }
    else if (node.isCalleeText(PyNames.ISSUBCLASS)) {
      final PyExpression[] args = node.getArguments();
      if (args.length == 2) {
        final PyExpression typeElement = args[1];

        pushAssertion(args[0], myPositive, context -> {
          if (myPositive || isSafeForNegativeAssertion(typeElement, context)) {
            return transformTypeFromAssertion(context.getType(typeElement), true, context, typeElement);
          }
          return null;
        });
      }
    }
  }

  private static boolean isSafeForNegativeAssertion(@NotNull PyExpression expression, @NotNull TypeEvalContext context) {
    final List<PyExpression> elements = expandClassInfoExpressions(expression);
    if (elements.isEmpty()) return false;
    for (PyExpression element : elements) {
      if (!isSafeClassInfoReference(element, context)) {
        return false;
      }
    }
    return true;
  }

  @NotNull
  @ApiStatus.Internal
  public static List<PyExpression> expandClassInfoExpressions(@NotNull PyExpression expression) {
    final PyExpression flattened = PyPsiUtils.flattenParens(expression);
    if (flattened == null) return List.of();
    if (flattened instanceof PyTupleExpression tuple) {
      final List<PyExpression> result = new ArrayList<>();
      for (PyExpression element : tuple.getElements()) {
        result.addAll(expandClassInfoExpressions(element));
      }
      return result;
    }
    // Keep in mind that `isinstance` will only accept `A | B` expression if all operands are classinfo, so no parameterized generics
    if (flattened instanceof PyBinaryExpression binary && binary.getOperator() == PyTokenTypes.OR) {
      final PyExpression left = binary.getLeftExpression();
      final PyExpression right = binary.getRightExpression();
      final List<PyExpression> result = new ArrayList<>(expandClassInfoExpressions(left));
      if (right != null) {
        result.addAll(expandClassInfoExpressions(right));
      }
      return result;
    }
    return List.of(flattened);
  }

  private static boolean isSafeClassInfoReference(@NotNull PyExpression expression, @NotNull TypeEvalContext context) {
    if (expression instanceof PyReferenceExpression ref) {
      // Here we check that the reference resolves to a class, not a target in assignment or function parameter.
      // This is done to avoid cases like Py3TypeTest.testIsInstanceNegativeNarrowing
      final List<@Nullable PsiElement> resolvedElements = PyUtil.multiResolveTopPriority(ref, PyResolveContext.defaultContext(context));
      return ContainerUtil.getOnlyItem(resolvedElements) instanceof PyClass;
    }
    return false;
  }

  private void visitExpressionInCondition(@NotNull PyExpression node) {
    if (myPositive && isReferenceInTruthyCondition(node)) {
      // TODO: we can actually check if the class defines __bool__ or __len__, and use it to exclude the type
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
    final boolean positive = myPositive;
    pushAssertion(lhs, myPositive, context -> {
      PyType type = getLiteralType(rhs, context);
      return positive || type instanceof PyLiteralType ? type : null;
    });
  }

  private void processIn(@NotNull PyExpression lhs, @NotNull PyExpression rhs) {
    if (rhs instanceof PyTupleExpression || rhs instanceof PyListLiteralExpression || rhs instanceof PySetLiteralExpression) {
      final boolean positive = myPositive;
      pushAssertion(lhs, myPositive, (TypeEvalContext context) -> {
        final PyExpression[] elements = ((PySequenceExpression)rhs).getElements();
        final List<PyType> types = new ArrayList<>(elements.length);
        final PyClassType noneType = PyBuiltinCache.getInstance(rhs).getNoneType();
        for (PyExpression element : elements) {
          final PyType type = PyLiteralType.isNone(element) ? noneType : getLiteralType(element, context);
          if (type != null && (positive || type == noneType || type instanceof PyLiteralType)) {
            types.add(type);
          }
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
    pushAssertion(subject, true, true, true, context -> {
      List<PyCaseClause> clauses = matchStatement.getCaseClauses();
      if (!clauses.isEmpty()) {
        return clauses.getLast().getSubjectTypeAfter(context);
      }
      return null;
    });
  }

  @ApiStatus.Internal
  public static @Nullable Ref<PyType> createAssertionType(@Nullable PyType initial,
                                                          @Nullable PyType suggested,
                                                          boolean positive,
                                                          boolean forceStrictNarrow,
                                                          @NotNull TypeEvalContext context) {
    if (suggested == null) return null;
    if (positive) {
      // Find all initial type members that are subtypes of the suggested (more specific than the narrowing "suggested" type).
      // Imagine having `list[int] | int` narrowed by `list[Any]`.
      List<PyType> initialSubtypes = PyTypeUtil.toStream(initial)
        .filter(initialSubtype -> match(suggested, initialSubtype, context))
        .toList();

      // Find all suggested subtype members that are subtypes of the initial (more specific than the initial type)
      // AND are not subtypes of those more specific initial types. 
      // This is needed to support generics of `Any`, because `list[Any]` is both a subtype, and a supertype of `list[str]`.
      StreamEx<PyType> suggestedSubtypes = PyTypeUtil.toStream(suggested)
        .filter(suggestedSubtype -> match(initial, suggestedSubtype, context))
        .filter(suggestedSubtype -> !ContainerUtil.exists(initialSubtypes,
                                                          initialSubtype -> match(initialSubtype, suggestedSubtype, context)));

      List<PyType> types = StreamEx.of(initialSubtypes).append(suggestedSubtypes).toList();
      return Ref.create(types.isEmpty() ? intersect(initial, suggested) : PyUnionType.union(types));
    }
    else {
      if (initial instanceof PyUnionType unionType) {
        return Ref.create(excludeFromUnion(unionType, suggested, context, forceStrictNarrow));
      }
      if (match(suggested, initial, context)) {
        return (forceStrictNarrow || isStrictNarrowingAllowed()) ? Ref.create(PyNeverType.NEVER) : null;
      }
      Ref<@Nullable PyType> diff = trySubtract(initial, suggested, context);
      return diff != null ? diff : Ref.create(initial);
    }
  }

  private static @Nullable PyType excludeFromUnion(@NotNull PyUnionType unionType,
                                                   @Nullable PyType type,
                                                   @NotNull TypeEvalContext context,
                                                   boolean forceStrictNarrow) {
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
    if ((forceStrictNarrow || isStrictNarrowingAllowed()) && members.isEmpty()) {
      return PyNeverType.NEVER;
    }
    return PyUnionType.union(members);
  }

  public static boolean isStrictNarrowingAllowed() {
    return Registry.is("python.strict.type.narrow");
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
           !(PyTypeUtil.inheritsAny(actual, context)) &&
           PyTypeChecker.match(expected, actual, context);
  }

  /**
   * @param transformToDefinition if true the result type will be Type[T], not T itself.
   */
  private static @Nullable PyType transformTypeFromAssertion(@Nullable PyType type,
                                                             boolean transformToDefinition,
                                                             @NotNull TypeEvalContext context,
                                                             @Nullable PyExpression typeElement) {
    /*
     * We need to distinguish:
     *   if isinstance(x, (int, str)):
     * And:
     *   if isinstance(x, (1, "")):
     */
    PyExpression typeElementNoParens = PyPsiUtils.flattenParens(typeElement);
    if (type instanceof PyTupleType tupleType) {
      final List<PyType> members = new ArrayList<>();
      final int count = tupleType.getElementCount();

      final PyTupleExpression tupleExpression = as(typeElementNoParens, PyTupleExpression.class);
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
    else if (typeElementNoParens instanceof PyBinaryExpression binary && binary.getOperator() == PyTokenTypes.OR) {
      final Ref<PyType> typeFromTypingProvider = PyTypingTypeProvider.getType(binary, context);
      if (typeFromTypingProvider != null) {
        return transformTypeFromAssertion(typeFromTypingProvider.get(), transformToDefinition, context, null);
      }
    }
    else if (type instanceof PyUnionType) {
      return ((PyUnionType)type).map(member -> transformTypeFromAssertion(member, transformToDefinition, context, null));
    }
    else if (type instanceof PyInstantiableType instantiableType) {
      return transformToDefinition ? instantiableType.toClass() : instantiableType.toInstance();
    }
    return type;
  }

  private void pushAssertion(@Nullable PyExpression expr, boolean positive, @NotNull Function<TypeEvalContext, PyType> suggestedType) {
    pushAssertion(expr, positive, false, isStrictNarrowingAllowed(), suggestedType);
  }

  private void pushAssertion(@Nullable PyExpression expr,
                             boolean positive,
                             boolean allowAnyExpr,
                             boolean forceStrictNarrow,
                             @NotNull Function<TypeEvalContext, PyType> suggestedType) {
    expr = PyPsiUtils.flattenParens(expr);
    if (expr instanceof PySequenceExpression seqExpr) {
      PyExpression[] elements = seqExpr.getElements();
      for (int i = 0; i < elements.length; i++) {
        pushAssertion(elements[i], positive, allowAnyExpr, forceStrictNarrow, getIteratedType(suggestedType, i));
      }
    }
    else if (expr instanceof PyAssignmentExpression walrus) {
      pushAssertion(walrus.getTarget(), positive, allowAnyExpr, forceStrictNarrow, suggestedType);
    }
    else if (expr != null) {
      final var target = expr;
      final InstructionTypeCallback typeCallback = new InstructionTypeCallback() {
        @Override
        public Ref<PyType> getType(TypeEvalContext context) {
          return createAssertionType(context.getType(target), suggestedType.apply(context), positive, forceStrictNarrow, context);
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

  @NotNull
  private static Function<TypeEvalContext, PyType> getIteratedType(@NotNull Function<TypeEvalContext, PyType> sequenceType, int index) {
    return context -> {
      var computedSuggestedType = sequenceType.apply(context);
      if (computedSuggestedType instanceof PyNeverType) return computedSuggestedType;
      if (computedSuggestedType instanceof PyTupleType tupleType) {
        return tupleType.getElementType(index);
      }
      return null;
    };
  }

  private static @Nullable PsiElement skipNotAndParens(@Nullable PsiElement element) {
    if (element == null) return null;
    for (PsiElement e = element.getParent(); e != null; e = e.getParent()) {
      if (!(e instanceof PyParenthesizedExpression) &&
          !(e instanceof PyPrefixExpression prefixExpr && prefixExpr.getOperator() == PyTokenTypes.NOT_KEYWORD)) {
        return e;
      }
    }
    return null;
  }

  private static boolean isReferenceInTruthyCondition(@NotNull PyExpression node) {
    final PsiElement parent = skipNotAndParens(node);
    if (parent instanceof PyConditionalStatementPart) return true;
    if (parent instanceof PyConditionalExpression cond && PsiTreeUtil.isAncestor(cond.getCondition(), node, false)) return true;
    if (parent instanceof PyBinaryExpression binExpr && (binExpr.isOperator(PyNames.AND) || binExpr.isOperator(PyNames.OR))) return true;
    if (parent instanceof PyAssertStatement) return true;
    if (parent instanceof PyGeneratorExpression gen &&
        ContainerUtil.or(gen.getIfComponents(), it -> PsiTreeUtil.isAncestor(it.getTest(), node, false))) {
      return true;
    }
    return false;
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
