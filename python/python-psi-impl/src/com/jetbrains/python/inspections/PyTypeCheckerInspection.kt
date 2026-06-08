// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.ast.PyAstFunction;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.codeInsight.stdlib.PyStdlibTypeProvider;
import com.jetbrains.python.codeInsight.typing.PyProtocolsKt;
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider.GeneratorTypeDescriptor;
import com.jetbrains.python.documentation.PythonDocumentationProvider;
import com.jetbrains.python.inspections.quickfix.PyMakeFunctionReturnTypeQuickFix;
import com.jetbrains.python.psi.PyAnnotation;
import com.jetbrains.python.psi.PyAnnotationOwner;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyAugAssignmentStatement;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyCallSiteOwner;
import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyComprehensionElement;
import com.jetbrains.python.psi.PyComprehensionForComponent;
import com.jetbrains.python.psi.PyDictLiteralExpression;
import com.jetbrains.python.psi.PyDoubleStarExpression;
import com.jetbrains.python.psi.PyEllipsisLiteralExpression;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyForStatement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyKeywordArgument;
import com.jetbrains.python.psi.PyListLiteralExpression;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameterList;
import com.jetbrains.python.psi.PyParenthesizedExpression;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyReferenceOwner;
import com.jetbrains.python.psi.PyReturnStatement;
import com.jetbrains.python.psi.PySequenceExpression;
import com.jetbrains.python.psi.PyStarArgument;
import com.jetbrains.python.psi.PyStarExpression;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PySubscriptionExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyTupleExpression;
import com.jetbrains.python.psi.PyTypeCommentOwner;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.PyWithItem;
import com.jetbrains.python.psi.PyWithStatement;
import com.jetbrains.python.psi.PyYieldExpression;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyCallExpressionHelper;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.impl.PySubscriptionExpressionImpl;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyABCUtil;
import com.jetbrains.python.psi.types.PyAnyType;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyCallableParameterListType;
import com.jetbrains.python.psi.types.PyCallableType;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyCollectionType;
import com.jetbrains.python.psi.types.PyCollectionTypeImpl;
import com.jetbrains.python.psi.types.PyConcatenateType;
import com.jetbrains.python.psi.types.PyDescriptorTypeUtil;
import com.jetbrains.python.psi.types.PyInstantiableType;
import com.jetbrains.python.psi.types.PyLiteralType;
import com.jetbrains.python.psi.types.PyNeverType;
import com.jetbrains.python.psi.types.PyParamSpecType;
import com.jetbrains.python.psi.types.PyPositionalVariadicType;
import com.jetbrains.python.psi.types.PySelfType;
import com.jetbrains.python.psi.types.PySentinelType;
import com.jetbrains.python.psi.types.PyTupleType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeChecker;
import com.jetbrains.python.psi.types.PyTypeInferenceCspFactory;
import com.jetbrains.python.psi.types.PyTypeParameterMapping;
import com.jetbrains.python.psi.types.PyTypeParameterMapping.Option;
import com.jetbrains.python.psi.types.PyTypeParameterType;
import com.jetbrains.python.psi.types.PyTypeUtil;
import com.jetbrains.python.psi.types.PyTypedDictType;
import com.jetbrains.python.psi.types.PyUnionType;
import com.jetbrains.python.psi.types.PyUnpackedTupleType;
import com.jetbrains.python.psi.types.PyUnpackedTupleTypeImpl;
import com.jetbrains.python.psi.types.PyUnpackedTypedDictType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.pyi.PyiUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.jetbrains.python.psi.PyUtil.as;
import static com.jetbrains.python.psi.impl.PyCallExpressionHelper.mapArguments;
import static com.jetbrains.python.psi.types.PyNoneTypeKt.isNoneType;
import static com.jetbrains.python.psi.types.PyTypeUtilKt.isObject;

public class PyTypeCheckerInspection extends PyInspection {
  private static final Logger LOG = Logger.getInstance(PyTypeCheckerInspection.class.getName());
  private static final Key<Long> TIME_KEY = Key.create("PyTypeCheckerInspection.StartTime");

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                                 boolean isOnTheFly,
                                                 @NotNull LocalInspectionToolSession session) {
    if (LOG.isDebugEnabled()) {
      session.putUserData(TIME_KEY, System.nanoTime());
    }
    TypeEvalContext context = PyInspectionVisitor.getContext(session);
    Visitor visitor = new Visitor(holder, context);
    return new PyReachableElementVisitor(visitor, context);
  }

  public static class Visitor extends PyInspectionVisitor {
    public Visitor(@NotNull ProblemsHolder holder, @NotNull TypeEvalContext context) {
      super(holder, context);
    }

    @Override
    protected @NotNull ProblemsHolder getHolder() {
      var holder = super.getHolder();
      assert holder != null;
      return holder;
    }

    // TODO: Visit decorators with arguments
    @Override
    public void visitPyCallExpression(@NotNull PyCallExpression node) {
      checkCallSite(node);
    }

    @Override
    public void visitPyBinaryExpression(@NotNull PyBinaryExpression node) {
      checkCallSite(node);
    }

    @Override
    public void visitPyAugAssignmentStatement(@NotNull PyAugAssignmentStatement node) {
       checkCallSite(node);
       visitPyTargetExpression(node.getAssignmentTarget());
    }

    @Override
    public void visitPySubscriptionExpression(@NotNull PySubscriptionExpression node) {
      PyType operandType = myTypeEvalContext.getType(node.getOperand());
      if (operandType instanceof PyTupleType tupleType && !tupleType.isHomogeneous()) {
        PyExpression indexExpression = node.getIndexExpression();
        for (int index : PySubscriptionExpressionImpl.getIndexExpressionPossibleValues(indexExpression, myTypeEvalContext, Integer.class)) {
          int count = tupleType.getElementCount();
          if (index < -count || index >= count) {
            registerProblem(indexExpression, PyPsiBundle.message("INSP.type.checker.tuple.index.out.of.range"));
          }
        }
      }
      // Type check in TypedDict subscription expressions cannot be properly done because each key should have its own value type,
      // so this case is covered by PyTypedDictInspection
      if (operandType instanceof PyTypedDictType) return;
      // Don't type check __class_getitem__ calls inside type hints. Normally these are not type hinted as a construct 
      // special-cased by type checkers
      if (PyTypingTypeProvider.isInsideTypeHint(node, myTypeEvalContext)) return;
      checkCallSite(node);
    }

    @Override
    public void visitPyForStatement(@NotNull PyForStatement node) {
      checkIteratedValue(node.getForPart().getSource(), node.isAsync());
    }

    @Override
    public void visitPyWithStatement(@NotNull PyWithStatement node) {
      for (PyWithItem withItem : node.getWithItems()) {
        checkContextManagerValue(withItem.getExpression(), node.isAsync());
      }
    }

    @Override
    public void visitPyReturnStatement(@NotNull PyReturnStatement node) {
      ScopeOwner owner = ScopeUtil.getScopeOwner(node);
      if (owner instanceof PyFunction function) {
        if (hasExplicitType(function)) {
          PyType expected = getExpectedReturnStatementType(function, myTypeEvalContext);
          if (expected == null) return;

          // We cannot just match annotated and inferred types, as we cannot promote inferred to Literal
          PyExpression returnExpr = node.getExpression();
          if (expected instanceof PyTypedDictType expectedTypedDictType) {
            if (returnExpr != null && PyTypedDictType.isDictExpression(returnExpr, myTypeEvalContext)) {
              reportTypedDictProblems(expectedTypedDictType, returnExpr);
              return;
            }
          }

          PyType actual = returnExpr != null ? tryPromotingType(returnExpr, expected) : PyBuiltinCache.getInstance(node).getNoneType();
          if (!matchesExpectedType(expected, actual, returnExpr, null)) {
            getHolder()
              .problem(returnExpr != null ? returnExpr : node, typeMismatchMessage(expected, actual))
              .highlight(effectiveHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING))
              .fix(new PyMakeFunctionReturnTypeQuickFix(function, myTypeEvalContext))
              .register();
          }
        }
      }
    }

    @Override
    public void visitPyYieldExpression(@NotNull PyYieldExpression node) {
      ScopeOwner owner = ScopeUtil.getScopeOwner(node);
      if (!(owner instanceof PyFunction function)) return;

      if (node.isDelegating()) {
        visitDelegatingYieldExpression(node, function);
        return;
      }

      final var annotatedGeneratorDesc = getGeneratorDescriptorFromAnnotation(function, node);
      if (annotatedGeneratorDesc == null) return;

      checkYieldType(annotatedGeneratorDesc.yieldType, node, function);
    }

    private void visitDelegatingYieldExpression(@NotNull PyYieldExpression node, @NotNull PyFunction function) {
      assert node.isDelegating();

      final PyExpression yieldExpr = node.getExpression();
      if (yieldExpr == null) return;

      final PyType delegateType = myTypeEvalContext.getType(yieldExpr);
      if (delegateType == null) return;

      var delegateDesc = GeneratorTypeDescriptor.fromGeneratorOrProtocol(delegateType, myTypeEvalContext);
      if (delegateDesc != null && delegateDesc.isAsync) {
        String delegateName = PythonDocumentationProvider.getTypeName(delegateType, myTypeEvalContext);
        registerProblem(yieldExpr, PyPsiBundle.message("INSP.type.checker.yield.from.async.generator", delegateName));
        return;
      }

      if (checkIteratedValue(yieldExpr, false)) return;

      final var annotatedGeneratorDesc = getGeneratorDescriptorFromAnnotation(function, node);
      if (annotatedGeneratorDesc == null) return;

      if (checkYieldType(annotatedGeneratorDesc.yieldType, node, function)) return;

      // Reversed because SendType is contravariant
      final PyType expectedSendType = annotatedGeneratorDesc.sendType;
      if (delegateDesc != null && !PyTypeChecker.match(delegateDesc.sendType, expectedSendType, myTypeEvalContext)) {
        String expectedName = PythonDocumentationProvider.getVerboseTypeName(expectedSendType, myTypeEvalContext);
        String actualName = PythonDocumentationProvider.getTypeName(delegateDesc.sendType, myTypeEvalContext);
        registerProblem(yieldExpr, PyPsiBundle.message("INSP.type.checker.yield.from.send.type.mismatch", expectedName, actualName));
      }
    }

    private @Nullable GeneratorTypeDescriptor getGeneratorDescriptorFromAnnotation(@NotNull PyFunction function,
                                                                                   @NotNull PyYieldExpression yieldExpr) {
      if (!hasExplicitType(function)) return null;

      final PyType annotatedReturnType = myTypeEvalContext.getReturnType(function);
      if (annotatedReturnType == null) return null;

      final var annotatedGeneratorDesc = GeneratorTypeDescriptor.fromGeneratorOrProtocol(annotatedReturnType, myTypeEvalContext);
      if (annotatedGeneratorDesc == null) {
        final PyType inferredReturnType = function.getInferredReturnType(myTypeEvalContext);
        if (!PyTypeChecker.match(annotatedReturnType, inferredReturnType, myTypeEvalContext)) {
          getHolder()
            .problem(yieldExpr, typeMismatchMessage(annotatedReturnType, inferredReturnType))
            .fix(new PyMakeFunctionReturnTypeQuickFix(function, myTypeEvalContext))
            .register();
        }
        return null;
      }
      return annotatedGeneratorDesc;
    }

    private boolean checkYieldType(@Nullable PyType expectedYieldType, @NotNull PyYieldExpression node, @NotNull PyFunction function) {
      final PyType thisYieldType = node.getYieldType(myTypeEvalContext);
      if (!matchesExpectedType(expectedYieldType, thisYieldType, node.getExpression(), null)) {
        final PyExpression yieldExpr = node.getExpression();
        String expectedName = PythonDocumentationProvider.getVerboseTypeName(expectedYieldType, myTypeEvalContext);
        String actualName = PythonDocumentationProvider.getTypeName(thisYieldType, myTypeEvalContext);
        getHolder()
          .problem(yieldExpr != null ? yieldExpr : node,
                   PyPsiBundle.message("INSP.type.checker.yield.type.mismatch", expectedName, actualName))
          .highlight(effectiveHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING))
          .fix(new PyMakeFunctionReturnTypeQuickFix(function, myTypeEvalContext))
          .register();
        return true;
      }
      return false;
    }

    public static @Nullable PyType getExpectedReturnStatementType(@NotNull PyFunction function, @NotNull TypeEvalContext typeEvalContext) {
      final PyType returnType = typeEvalContext.getReturnType(function);
      if (function.isGenerator()) {
        final var generatorDesc = GeneratorTypeDescriptor.fromGeneratorOrProtocol(returnType, typeEvalContext);
        if (generatorDesc != null) {
          return generatorDesc.returnType;
        }
        return PyAnyType.getUnknown();
      }
      if (function.isAsync()) {
        return PyTypeUtil.derefOrUnknown(PyTypingTypeProvider.coroutineOrGeneratorElementType(returnType));
      }
      return returnType;
    }

    @Override
    public void visitPyReferenceExpression(@NotNull PyReferenceExpression node) {
      checkClassAttributeAccess(node);
    }

    @Override
    public void visitPyAssignmentStatement(@NotNull PyAssignmentStatement node) {
      final PyExpression lhs = PyPsiUtils.flattenParens(node.getLeftHandSideExpression());
      final PyExpression rhs = node.getAssignedValue();
      if (!(lhs instanceof PyTupleExpression || lhs instanceof PyListLiteralExpression) || rhs == null) return;
      final var lhsSeq = (PySequenceExpression) lhs;

      // Check that the RHS is iterable
      if (checkUnpackIterableValue(rhs)) return;

      final PyType rhsType = myTypeEvalContext.getType(rhs);
      if (!(rhsType instanceof PyTupleType rhsTupleType) || rhsTupleType.isHomogeneous()) return;

      final PyExpression[] targets = lhsSeq.getElements();
      var lhsStarCount = StreamEx.of(targets).select(PyStarExpression.class).count();

      // The RHS value count. A starred RHS element contributes its operand's length only when that operand is a
      // statically known (heterogeneous) tuple; an unbounded operand (e.g. `*list_value`) makes the count
      // indeterminate, in which case the balance check is skipped.
      final int rhsCount = rhs instanceof PyTupleExpression rhsTupleExpr &&
                           ContainerUtil.exists(rhsTupleExpr.getElements(), PyStarExpression.class::isInstance)
                           ? getUnpackedTupleLength(rhsTupleExpr)
                           : rhsTupleType.getElementCount();
      if (rhsCount >= 0) {
        if (lhsStarCount > 1) {
          registerProblem(lhs, PyPsiBundle.message("INSP.tuple.assignment.balance.only.one.starred.expression.allowed.in.assignment"));
          return;
        }
        if (lhsStarCount == 0 && targets.length != rhsCount) {
          final String key = targets.length < rhsCount
                             ? "INSP.tuple.assignment.balance.too.many.values.to.unpack"
                             : "INSP.tuple.assignment.balance.need.more.values.to.unpack";
          registerProblem(rhs, PyPsiBundle.message(key, targets.length, rhsCount),
                          effectiveHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
          return;
        }
        if (lhsStarCount == 1 && targets.length - 1 > rhsCount) {
          registerProblem(rhs, PyPsiBundle.message("INSP.tuple.assignment.balance.need.more.values.to.unpack",
                                                   targets.length - 1, rhsCount),
                          effectiveHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
          return;
        }
      }

      // Per-element type mismatch check for annotated targets with a non-tuple RHS (`x, y = expr` / `[x] = expr`).
      // findAssignedValue() yields a synthetic subscription for these, so the mismatch is reported on the RHS itself.
      // A tuple RHS (`x, y = 1, 2` / `[x] = 1, 2`) maps to real value elements and is handled by
      // visitPyTargetExpression via findAssignedValue().
      if (lhsStarCount == 0 && !(rhs instanceof PyTupleExpression)) {
        for (PyExpression target : targets) {
          if (!(target instanceof PyTargetExpression targetExpr)) continue;
          if (!targetOrResolvedHasExplicitType(targetExpr)) continue;
          final PyType annotatedType = myTypeEvalContext.getType(targetExpr);
          final PyType unpackedType = PyTypeChecker.getTargetTypeFromTupleAssignment(targetExpr, lhsSeq, rhsTupleType);
          if (unpackedType == null || PyTypeChecker.match(annotatedType, unpackedType, myTypeEvalContext)) continue;
          final PyType displayType = PyLiteralType.upcastLiteralToClass(unpackedType);
          registerProblem(rhs,
                          PyPsiBundle.message("INSP.type.checker.expected.type.got.type.instead",
                                              PythonDocumentationProvider.getVerboseTypeName(annotatedType, myTypeEvalContext),
                                              PythonDocumentationProvider.getTypeName(displayType, myTypeEvalContext)),
                          effectiveHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
          // stop after the first error, because otherwise we might start reporting different type errors on the same element
          return;
        }
      }
    }

    @Override
    public void visitPyStarExpression(@NotNull PyStarExpression node) {
      final PsiElement parent = node.getParent();
      if (parent instanceof PySequenceExpression sequenceExpr) {
        // Skip star expressions that are assignment targets: `a, *b = ...`
        final var possibleLhs = PsiTreeUtil.skipParentsOfType(sequenceExpr, PyParenthesizedExpression.class);
        if (possibleLhs instanceof PyAssignmentStatement assignment &&
            PyPsiUtils.flattenParens(assignment.getLeftHandSideExpression()) == sequenceExpr) {
          // Check type annotation compatibility for annotated star targets like `x: int; (*x,) = [1, 2, 3]`
          final PyExpression innerExpr = node.getExpression();
          if (innerExpr instanceof PyTargetExpression targetExpr && targetOrResolvedHasExplicitType(targetExpr)) {
            final PyExpression rhs = assignment.getAssignedValue();
            if (rhs != null) {
              final PyType rhsType = myTypeEvalContext.getType(rhs);
              if (rhsType instanceof PyCollectionType collectionType) {
                final PyType elementType = PyLiteralType.upcastLiteralToClass(collectionType.getIteratedItemType());
                final PyClass listClass = PyBuiltinCache.getInstance(node).getClass("list");
                if (listClass != null) {
                  final PyType actualType = new PyCollectionTypeImpl(listClass, false, Collections.singletonList(elementType));
                  final PyType annotatedType = myTypeEvalContext.getType(targetExpr);
                  if (annotatedType != null && !PyTypeChecker.isUnknown(annotatedType, myTypeEvalContext) &&
                      !PyTypeChecker.match(annotatedType, actualType, myTypeEvalContext)) {
                    registerProblem(rhs,
                                    PyPsiBundle.message("INSP.type.checker.expected.type.got.type.instead",
                                                        PythonDocumentationProvider.getVerboseTypeName(annotatedType, myTypeEvalContext),
                                                        PythonDocumentationProvider.getTypeName(actualType, myTypeEvalContext)),
                                    effectiveHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
                  }
                }
              }
            }
          }
          return;
        }
        // Check iterability of starred operand in sequence literals `[*a], {*a}, (*a,)`
        // Skip type unpack expressions in type hints: [*tuple[T]], (*Ts,), etc.
        // TODO: consider Annotated[T, [*1]]
        if (PyTypingTypeProvider.isInsideTypeHint(node, myTypeEvalContext)) return;
        checkUnpackIterableValue(node.getExpression());
      }
    }

    @Override
    public void visitPyDoubleStarExpression(@NotNull PyDoubleStarExpression node) {
      // Check that **expr in dict literals ({**a}) has a mapping type
      if (node.getParent() instanceof PyDictLiteralExpression) {
        checkUnpackMappingValue(node.getExpression());
      }
    }

    @Override
    public void visitPyStarArgument(@NotNull PyStarArgument node) {
      if (node.isKeyword()) {
        checkUnpackMappingValue(node.getExpression());
      }
      else {
        checkUnpackIterableValue(node.getExpression());
      }
    }

    @Override
    public void visitPyTargetExpression(@NotNull PyTargetExpression node) {
      checkClassAttributeAccess(node);
      final PyExpression assignedValue = node.findAssignedValue();
      if (assignedValue == null) return;

      final ScopeOwner scopeOwner = ScopeUtil.getScopeOwner(node);
      if (scopeOwner instanceof PyClass cls && PyStdlibTypeProvider.isCustomEnum(cls, myTypeEvalContext)) {
        final PyStdlibTypeProvider.EnumAttributeInfo info = PyStdlibTypeProvider.getEnumAttributeInfo(cls, node, myTypeEvalContext);
        if (info == null || info.attributeKind() != PyStdlibTypeProvider.EnumAttributeKind.MEMBER) return;

        PyType expected = PyStdlibTypeProvider.getEnumValueType(cls, myTypeEvalContext);
        PyType actual = info.assignedValueType();
        if (!PyTypeChecker.match(expected, actual, myTypeEvalContext)) {
          registerProblem(assignedValue, typeMismatchMessage(expected, actual));
        }
        return;
      }

      PyType expected = myTypeEvalContext.getType(node);

      if (scopeOwner instanceof PyClass) {
        if (!targetOrResolvedHasExplicitType(node)) return;
      }

      if (node.isQualified()) {
        PyTypeChecker.GenericSubstitutions substitutions = PyTypeChecker.unifyReceiver(node.getQualifier(), myTypeEvalContext);
        expected = PyTypeChecker.substitute(expected, substitutions, myTypeEvalContext);
      }

      boolean isDescriptor = false;

      Ref<PyType> classAttrType = getClassAttributeType(node);
      if (classAttrType != null) {
        Ref<PyType> dunderSetValueType =
          PyDescriptorTypeUtil.getExpectedValueTypeForDunderSet(node, classAttrType.get(), myTypeEvalContext);
        if (dunderSetValueType != null) {
          expected = dunderSetValueType.get();
          isDescriptor = true;
        }
      }

      if (expected instanceof PyTypedDictType expectedTypedDictType && PyTypedDictType.isDictExpression(assignedValue, myTypeEvalContext)) {
        reportTypedDictProblems(expectedTypedDictType, assignedValue);
        return;
      }

      PyType actual = tryPromotingType(assignedValue, expected);

      if (expected instanceof PySentinelType) {
        if (isObject(actual)) return;
      }

      if (!matchesExpectedType(expected, actual, assignedValue, null)) {
        boolean isAugAssignment = node.getParent() instanceof PyAugAssignmentStatement;
        String message =
          isDescriptor
          ? typeMismatchMessage(
            expected,
            actual,
            "INSP.type.checker.expected.type.from.dunder.set.got.type.instead"
          )
          : isAugAssignment
            ? typeMismatchMessage(
            expected,
            actual,
            "INSP.type.checker.expected.type.from.aug.assignment.got.type.instead"
          )
            : typeMismatchMessage(expected, actual);
        registerProblem(assignedValue,
                        message,
                        effectiveHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
      }
    }

    private @NotNull @Nls String typeMismatchMessage(@Nullable PyType expected,
                                                     @Nullable PyType actual) {
      return typeMismatchMessage(expected, actual, "INSP.type.checker.expected.type.got.type.instead");
    }

    private @NotNull @Nls String typeMismatchMessage(@Nullable PyType expected,
                                                     @Nullable PyType actual,
                                                     @NotNull @PropertyKey(resourceBundle = PyPsiBundle.BUNDLE) String messageKey) {
      String expectedName = PythonDocumentationProvider.getVerboseTypeName(expected, myTypeEvalContext);
      String actualName = PythonDocumentationProvider.getTypeName(actual, myTypeEvalContext);
      return PyPsiBundle.message(messageKey, expectedName, actualName);
    }

    private boolean matchesExpectedType(@Nullable PyType expected,
                                        @Nullable PyType actual,
                                        @Nullable PyExpression expression,
                                        @Nullable PyTypeChecker.GenericSubstitutions substitutions) {

      boolean matches = substitutions == null
                        ? PyTypeChecker.match(expected, actual, myTypeEvalContext)
                        : PyTypeChecker.match(expected, actual, myTypeEvalContext, substitutions);
      if (matches) return true;
      return isCovariantMatchTempFix(expected, actual, expression, substitutions);
    }

    /**
     * The failing subtype check could be due to respecting variance.
     * However, the underlying reason is that the `actual` type was not correctly inferred.
     * As a temporary solution, we mimic a covariant subtype check. (TODO PY-89564)
     */
    private boolean isCovariantMatchTempFix(PyType expected, PyType actual, PyExpression expExpr,
                                            PyTypeChecker.GenericSubstitutions substitutions
    ) {
      var expectedSubst = substitutions == null ? expected : PyTypeChecker.substitute(expected, substitutions, myTypeEvalContext);
      var actualSubst = substitutions == null ? actual : PyTypeChecker.substitute(actual, substitutions, myTypeEvalContext);
      if (expectedSubst instanceof PyCollectionType expCT && actualSubst instanceof PyCollectionType actCT) {
        var expClassType = expCT.getPyClass().getType(myTypeEvalContext);
        var actClassType = actCT.getPyClass().getType(myTypeEvalContext);
        var isCreational = expExpr instanceof PySequenceExpression
                           || expExpr instanceof PyCallExpression ce && !(ce.getCallee() instanceof PySubscriptionExpression)
                           || expExpr instanceof PyParenthesizedExpression pe && pe.getContainedExpression() instanceof PyTupleExpression;
        var paramMapping = PyTypeParameterMapping.mapByShape(expCT.getElementTypes(), actCT.getElementTypes(), Option.USE_DEFAULTS);
        if (isCreational
            && paramMapping != null
            && PyTypeChecker.match(expClassType, actClassType, myTypeEvalContext)
        ) {
          boolean allElementsMatch = true;
          for (int i = 0; i < paramMapping.getMappedTypes().size(); i++) {
            var couple = paramMapping.getMappedTypes().get(i);
            var expET = couple.first;
            var actET = couple.second;
            if (actET instanceof PyUnpackedTupleType utt && utt.isUnbound()) {
              actET = utt.getElementTypes().getFirst();
            }
            if (!PyTypeChecker.match(expET, actET, myTypeEvalContext) && !isCovariantMatchTempFix(expET, actET, expExpr, substitutions)) {
              allElementsMatch = false;
              break;
            }
          }
          if (allElementsMatch) {
            return true;
          }
        }
      }
      return false;
    }

    // Using generic classes (parameterized or not) to access attributes will result in type check failure.
    private <T extends PyQualifiedExpression & PyReferenceOwner> void checkClassAttributeAccess(@NotNull T expression) {
      PyExpression qualifier = expression.getQualifier();
      if (qualifier != null) {
        PyType qualifierType = myTypeEvalContext.getType(qualifier);
        if (qualifierType instanceof PyClassType classType && classType.isDefinition()) {
          PsiElement resolved = expression.getReference(PyResolveContext.defaultContext(myTypeEvalContext)).resolve();
          if (resolved instanceof PyTargetExpression target && PyUtil.isClassAttribute(target)) {
            PyType targetType = myTypeEvalContext.getType(target);
            if (requiresTypeSpecialization(targetType)) {
              ASTNode nameElement = expression.getNameElement();
              registerProblem(nameElement == null ? null : nameElement.getPsi(),
                              PyPsiBundle.message("INSP.type.checker.access.to.generic.instance.variables.via.class.is.ambiguous"));
            }
          }
        }
      }
    }

    private static boolean requiresTypeSpecialization(@Nullable PyType type) {
      if (type instanceof PyTypeParameterType typeParameterType &&
          typeParameterType.getDefaultType() == null &&
          !(type instanceof PySelfType)) {
        return true;
      }
      return type instanceof PyCollectionType collectionType &&
             ContainerUtil.exists(collectionType.getElementTypes(), Visitor::requiresTypeSpecialization);
    }

    private <T extends PyQualifiedExpression & PyReferenceOwner> @Nullable Ref<PyType> getClassAttributeType(@NotNull T attribute) {
      if (!attribute.isQualified()) return null;
      PsiElement definition = attribute.getReference(PyResolveContext.defaultContext(myTypeEvalContext)).resolve();
      if (!(definition instanceof PyTargetExpression attrDefinition && PyUtil.isAttribute(attrDefinition))) return null;
      return Ref.create(myTypeEvalContext.getType(attrDefinition));
    }

    private void reportTypedDictProblems(@NotNull PyTypedDictType expectedType, @NotNull PyExpression expression) {
      @NotNull PyTypedDictType.TypeCheckingResult result = new PyTypedDictType.TypeCheckingResult();
      PyTypedDictType.checkExpression(expectedType, expression, myTypeEvalContext, result);
      result.getValueTypeErrors().forEach(error -> {
        registerProblem(error.getActualExpression(),
                        typeMismatchMessage(error.getExpectedType(), error.getActualType()),
                        effectiveHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
      });
      result.getExtraKeys().forEach(error -> {
        registerProblem(Objects.requireNonNullElse(error.getActualExpression(), expression),
                        PyPsiBundle.message("INSP.type.checker.typed.dict.extra.key", error.getKey(), error.getExpectedTypedDictName()));
      });
      result.getMissingKeys().forEach(error -> {
        registerProblem(error.getActualExpression() != null ? error.getActualExpression() : expression,
                        PyPsiBundle.message("INSP.type.checker.typed.dict.missing.keys", error.getExpectedTypedDictName(),
                                            error.getMissingKeys().size(),
                                            StringUtil.join(error.getMissingKeys(), s -> String.format("'%s'", s), ", ")));
      });
    }

    private void reportUnpackedTypedDictProblems(@NotNull PyUnpackedTypedDictType expectedType,
                                                 @NotNull PyExpression expression) {
      if (expression instanceof PyStarArgument starArgument) {
        expression = PsiTreeUtil.findChildOfType(starArgument, PyExpression.class);
      }
      if (expression == null) return;
      PyType argumentType = myTypeEvalContext.getType(expression);
      PyTypedDictType typedDictType = expectedType.getTypedDictType();
      if (PyTypedDictType.isDictExpression(expression, myTypeEvalContext)) {
        reportTypedDictProblems(typedDictType, expression);
        return;
      }
      if (!PyTypeChecker.match(typedDictType, argumentType, myTypeEvalContext)) {
        registerProblem(expression,
                        PyPsiBundle.message("INSP.type.checker.expected.type.got.type.instead",
                                            PythonDocumentationProvider.getTypeName(typedDictType, myTypeEvalContext),
                                            PythonDocumentationProvider.getTypeName(argumentType, myTypeEvalContext)));
      }
    }

    private @Nullable PyType tryPromotingType(@NotNull PyExpression expr, @Nullable PyType expected) {
      final PyType promotedToLiteral = PyLiteralType.Companion.promoteToLiteral(expr, expected, myTypeEvalContext, null);
      if (promotedToLiteral != null) return promotedToLiteral;
      return myTypeEvalContext.getType(expr);
    }

    @Override
    public void visitPyFunction(@NotNull PyFunction node) {
      if (hasExplicitType(node)) {
        final PyAnnotation annotation = node.getAnnotation();
        final PyType expected = getExpectedReturnStatementType(node, myTypeEvalContext);
        final PyType noneType = PyBuiltinCache.getInstance(node).getNoneType();
        final boolean returnsNone = isNoneType(expected);
        final boolean returnsOptional = PyTypeChecker.match(expected, noneType, myTypeEvalContext);

        if (expected != null && !returnsOptional && !PyUtil.isEmptyFunction(node)) {
          final List<PyStatement> returnPoints = node.getReturnPoints(myTypeEvalContext);
          final boolean hasImplicitReturns = ContainerUtil.exists(returnPoints, it -> !(it instanceof PyReturnStatement));

          if (hasImplicitReturns) {
            final PyType actual = node.getReturnStatementType(myTypeEvalContext);
            final PsiElement annotationValue = annotation != null ? annotation.getValue() : node.getTypeComment();
            if (annotationValue != null) {
              getHolder()
                .problem(annotationValue, typeMismatchMessage(expected, actual))
                .highlight(effectiveHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING))
                .fix(new PyMakeFunctionReturnTypeQuickFix(node, myTypeEvalContext))
                .register();
            }
          }
        }

        final PyType annotatedType = myTypeEvalContext.getReturnType(node);

        if (PyUtil.isInitMethod(node) && !(returnsNone || annotatedType instanceof PyNeverType)) {
          registerProblem(annotation != null ? annotation.getValue() : node.getTypeComment(),
                          PyPsiBundle.message("INSP.type.checker.init.should.return.none"));
        }

        if (node.isGenerator()) {
          final var generatorDesc = GeneratorTypeDescriptor.fromGeneratorOrProtocol(annotatedType, myTypeEvalContext);
          final boolean shouldBeAsync = node.isAsync() && node.isAsyncAllowed();
          final boolean wrongSyncAsync = generatorDesc != null && generatorDesc.isAsync != shouldBeAsync;

          final PyType inferredType = node.getInferredReturnType(myTypeEvalContext);
          if (wrongSyncAsync || (generatorDesc == null && !PyTypeChecker.match(annotatedType, inferredType, myTypeEvalContext))) {
            final PsiElement annotationValue = annotation != null ? annotation.getValue() : node.getTypeComment();
            if (annotationValue != null) {
              getHolder()
                .problem(annotationValue, typeMismatchMessage(inferredType, annotatedType))
                .fix(new PyMakeFunctionReturnTypeQuickFix(node, myTypeEvalContext))
                .register();
            }
          }
        }
      }
    }

    @Override
    public void visitPyNamedParameter(@NotNull PyNamedParameter node) {
      if (!hasExplicitType(node)) return;

      final PyExpression defaultValue = PyPsiUtils.flattenParens(node.getDefaultValue());
      if (defaultValue == null) return;

      if (defaultValue instanceof PyEllipsisLiteralExpression && (isProtocolMethodParameter(node) || isOverloadSignature(node))) {
        return;
      }

      // we use `PyTypingTypeProvider.getType` of the annotation directly, instead of `node.getType`,
      //  because otherwise `PyTypingTypeProvider` will inject the type of `None`
      final var expectedRef = PyTypingTypeProvider.getType(node.getAnnotation().getValue(), myTypeEvalContext);
      if (expectedRef == null) return;
      final var expected = expectedRef.get();
      final var actual = tryPromotingType(defaultValue, expected);

      if (actual instanceof PySentinelType) return;

      if (!matchesExpectedType(expected, actual, defaultValue, null)) {
        registerProblem(defaultValue, typeMismatchMessage(expected, actual),
                        effectiveHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
      }
    }

    private boolean isProtocolMethodParameter(@NotNull PyNamedParameter node) {
      PsiElement parent = node.getContext();
      if (parent instanceof PyParameterList parameterList) {
        PyCallable containingCallable = parameterList.getContainingCallable();
        if (containingCallable instanceof PyFunction function) {
          PyClass containingClass = function.getContainingClass();
          if (containingClass == null) {
            return false;
          }
          PyType classType = myTypeEvalContext.getType(containingClass);
          if (classType instanceof PyClassLikeType classLikeType && PyProtocolsKt.isProtocol(classLikeType, myTypeEvalContext)) {
            return true;
          }
        }
      }
      return false;
    }

    private boolean isOverloadSignature(@NotNull PyNamedParameter node) {
      PsiElement parent = node.getParent();
      if (parent instanceof PyParameterList parameterList) {
        PyCallable containingCallable = parameterList.getContainingCallable();
        if (containingCallable instanceof PyFunction function) {
          return PyiUtil.isOverload(function, myTypeEvalContext);
        }
      }
      return false;
    }

    @Override
    public void visitPyComprehensionElement(@NotNull PyComprehensionElement node) {
      super.visitPyComprehensionElement(node);

      for (PyComprehensionForComponent forComponent : node.getForComponents()) {
        checkIteratedValue(forComponent.getIteratedList(), forComponent.isAsync());
      }
    }

    private void checkCallSite(@NotNull PyCallSiteOwner callSite) {
      final List<AnalyzeCalleeResults> calleesResults = StreamEx
        .of(mapArguments(callSite, getResolveContext()))
        .filter(mapping -> mapping.isComplete())
        .map(mapping -> analyzeCallee(callSite, mapping))
        .nonNull()
        .toList();

      if (!ContainerUtil.exists(calleesResults, calleeResults -> isMatched(calleeResults))) {
        PyTypeCheckerInspectionProblemRegistrar
          .registerProblem(this, callSite, getArgumentTypes(calleesResults), calleesResults, myTypeEvalContext,
                           effectiveHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
      }
    }

    private boolean checkIteratedValue(@Nullable PyExpression iteratedValue, boolean isAsync) {
      return checkIteratedValue(iteratedValue, iteratedValue, isAsync);
    }

    private boolean checkIteratedValue(@Nullable PyExpression iteratedValue, @Nullable PsiElement highlightElement, boolean isAsync) {
      if (iteratedValue == null || highlightElement == null) return false;
      final PyType type = myTypeEvalContext.getType(iteratedValue);
      final String iterableClassName = isAsync ? PyNames.ASYNC_ITERABLE : PyNames.ITERABLE;

      if (type != null &&
          !PyTypeChecker.isUnknown(type, myTypeEvalContext) &&
          !PyABCUtil.isSubtype(type, iterableClassName, myTypeEvalContext)) {
        final String typeName = PythonDocumentationProvider.getTypeName(type, myTypeEvalContext);

        String qualifiedName = "collections." + iterableClassName;
        registerProblem(highlightElement, PyPsiBundle.message("INSP.type.checker.expected.type.got.type.instead", qualifiedName, typeName),
                        effectiveHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
        return true;
      }
      return false;
    }

    private boolean checkUnpackIterableValue(@Nullable PyExpression iteratedValue) {
      if (iteratedValue == null) return false;
      if (iteratedValue instanceof PyStarExpression starExpression) iteratedValue = starExpression.getExpression();
      // A generic-class subscription like `*A[int]` is always iterable at runtime:
      // `types.GenericAlias.__iter__` yields the subscript args.
      if (iteratedValue instanceof PySubscriptionExpression subscription) {
        final var operand = subscription.getOperand();
        if (myTypeEvalContext.getType(operand) instanceof PyClassLikeType classLikeType && classLikeType.isDefinition()) {
          return false;
        }
      }
      final PyType type = myTypeEvalContext.getType(iteratedValue);
      if (type != null &&
          !PyTypeChecker.isUnknown(type, myTypeEvalContext) &&
          !PyABCUtil.isSubtype(type, PyNames.ITERABLE, myTypeEvalContext)) {
        final String typeName = PythonDocumentationProvider.getTypeName(type, myTypeEvalContext);
        registerProblem(iteratedValue, PyPsiBundle.message("INSP.type.checker.unpack.expected.iterable", typeName),
                        effectiveHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
        return true;
      }
      return false;
    }

    private void checkUnpackMappingValue(@Nullable PyExpression mappingValue) {
      if (mappingValue == null) return;
      if (mappingValue instanceof PyDoubleStarExpression starExpression) mappingValue = starExpression.getExpression();
      final PyType type = myTypeEvalContext.getType(mappingValue);
      if (type != null &&
          !PyTypeChecker.isUnknown(type, myTypeEvalContext) &&
          // TODO: it's not Mapping, but a more wider type
          !PyABCUtil.isSubtype(type, PyNames.MAPPING, myTypeEvalContext)) {
        final String typeName = PythonDocumentationProvider.getTypeName(type, myTypeEvalContext);
        registerProblem(mappingValue, PyPsiBundle.message("INSP.type.checker.unpack.expected.mapping", typeName),
                        effectiveHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
      }
    }

    private void checkContextManagerValue(@Nullable PyExpression iteratedValue, boolean isAsync) {
      if (iteratedValue != null) {
        final PyType type = myTypeEvalContext.getType(iteratedValue);
        final String contextManagerClassName = isAsync ? PyNames.ABSTRACT_ASYNC_CONTEXT_MANAGER : PyNames.ABSTRACT_CONTEXT_MANAGER;

        if (type != null &&
            !PyTypeChecker.isUnknown(type, myTypeEvalContext) &&
            !PyABCUtil.isSubtype(type, contextManagerClassName, myTypeEvalContext)) {
          final String typeName = PythonDocumentationProvider.getTypeName(type, myTypeEvalContext);

          String qualifiedName = "contextlib." + contextManagerClassName;
          registerProblem(iteratedValue, PyPsiBundle.message("INSP.type.checker.expected.type.got.type.instead", qualifiedName, typeName),
                          effectiveHighlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
        }
      }
    }

    private @Nullable AnalyzeCalleeResults analyzeCallee(@NotNull PyCallSiteOwner callSite,
                                                         @NotNull PyCallExpression.PyArgumentsMapping mapping) {
      final PyCallableType callableType = mapping.getCallableType();
      if (callableType == null) return null;

      final List<AnalyzeArgumentResult> result = new ArrayList<>();
      final List<UnexpectedArgumentForParamSpec> unexpectedArgumentForParamSpecs = new ArrayList<>();
      final List<UnfilledParameterFromParamSpec> unfilledParameterFromParamSpecs = new ArrayList<>();

      final var receiver = callSite.getReceiver(callableType.getCallable());
      final var substitutions = PyTypeInferenceCspFactory.unifyReceiver(mapping, myTypeEvalContext);

      PyCallableParameter selfParameter = ContainerUtil.getFirstItem(mapping.getImplicitParameters());
      if (receiver != null && selfParameter != null) {
        PyType actual = myTypeEvalContext.getType(receiver);
        // TODO (PY-89400): Support validation for `receiver` of a union type
        // When `receiver` has a union type, we must find the specific member of the union bound to `callableType`.
        // See `Py3TypeCheckerInspectionTest.testAnnotatedSelfAgainstUnionReceiver`.
        if (!(actual instanceof PyUnionType)) {
          if (actual instanceof PyInstantiableType<?> instantiableType) {
            if (isConstructorCall(callSite) && PyUtil.isInitMethod(callableType.getCallable())) {
              actual = instantiableType.toInstance();
            }
            if (callableType.getModifier() == PyAstFunction.Modifier.CLASSMETHOD) {
              actual = instantiableType.toClass();
            }
          }

          PyType expected = selfParameter.getArgumentType(myTypeEvalContext);
          // Skip the check when `expected` is a metaclass-scoped `PySelfType`:
          // - explicit `typing.Self` usage on a metaclass is disallowed by the typing specification;
          // - for an unannotated `self`/`cls` (for which the inferred type is also `PySelfType`),
          //   the bound-receiver resolution already guarantees that the receiver is an instance of the metaclass;
          // - matching a class receiver against a metaclass-scoped `PySelfType` currently fails
          //   (see `Py3TypeCheckerInspectionTest.testSelfOnMetaclass`).
          boolean isSelfOnMetaclass = false;
          if (expected instanceof PySelfType expectedSelfType) {
            PyClassType typeType = PyBuiltinCache.getInstance(callSite).getTypeType();
            isSelfOnMetaclass = typeType != null &&
                                expectedSelfType.getScopeClassType().getAncestorTypes(myTypeEvalContext).contains(typeType.toClass());
          }
          if (!isSelfOnMetaclass) {
            if (!matchParameterAndArgument(expected, actual, receiver, substitutions)) {
              result.add(new AnalyzeArgumentResult(receiver, expected, substituteGenerics(expected, substitutions), actual, false));
            }
          }
        }
      }

      final var mappedParameters = mapping.getMappedParameters();
      final var regularMappedParameters = PyCallExpressionHelper.getRegularMappedParameters(mappedParameters);

      for (Map.Entry<PyExpression, PyCallableParameter> entry : regularMappedParameters.entrySet()) {
        final PyExpression argument = entry.getKey();
        final PyCallableParameter parameter = entry.getValue();
        final PyType expected = parameter.getArgumentType(myTypeEvalContext);
        final PyType promotedToLiteral = PyLiteralType.Companion.promoteToLiteral(argument, expected, myTypeEvalContext, substitutions);
        final var actual = promotedToLiteral != null ? promotedToLiteral : myTypeEvalContext.getType(argument);

        if (expected instanceof PyParamSpecType) {
          final var allArguments = callSite.getArguments(callableType.getCallable());
          analyzeParamSpec((PyParamSpecType)expected, allArguments, substitutions, result, unexpectedArgumentForParamSpecs,
                           unfilledParameterFromParamSpecs);
          break;
        }
        else if (expected instanceof PyConcatenateType concatenateType) {
          final var allArguments = callSite.getArguments(callableType.getCallable());
          if (allArguments.isEmpty()) break;

          final var firstExpectedTypes = concatenateType.getFirstTypes();
          int nonStarCount = 0;
          for (PyExpression arg : allArguments) {
            if (arg instanceof PyStarArgument) break;
            nonStarCount++;
          }
          final var argumentRightBound = Math.min(firstExpectedTypes.size(), nonStarCount);
          final var firstArguments = allArguments.subList(0, argumentRightBound);
          matchArgumentsAndTypes(firstArguments, firstExpectedTypes, substitutions, result);

          final var paramSpec = concatenateType.getParamSpec();
          final var restArguments = allArguments.subList(argumentRightBound, allArguments.size());
          if (paramSpec != null) {
            if (argumentRightBound < firstExpectedTypes.size()) {
              // Not enough positional arguments to satisfy the Concatenate prefix, e.g., int, str in Concatenate[int, str, P]
              PyCallableParameterListType paramSpecSubst = getParamSpecSubstitution(paramSpec, substitutions);
              if (paramSpecSubst == null) {
                for (PyExpression arg : restArguments) {
                  if (arg instanceof PyStarArgument) {
                    unexpectedArgumentForParamSpecs.add(new UnexpectedArgumentForParamSpec(arg, paramSpec));
                    break;
                  }
                }
              }
            }
            analyzeParamSpec(paramSpec, restArguments, substitutions, result, unexpectedArgumentForParamSpecs,
                             unfilledParameterFromParamSpecs);
          }

          break;
        }
        else {
          final boolean matched = matchParameterAndArgument(expected, actual, argument, substitutions);
          result.add(new AnalyzeArgumentResult(argument, expected, substituteGenerics(expected, substitutions), actual, matched));
        }
      }

      PyCallableParameter positionalContainer = PyCallExpressionHelper.getMappedPositionalContainer(mappedParameters);
      List<PyExpression> positionalArguments = PyCallExpressionHelper.getArgumentsMappedToPositionalContainer(mappedParameters);
      PyCallableParameter keywordContainer = PyCallExpressionHelper.getMappedKeywordContainer(mappedParameters);
      List<PyExpression> keywordArguments = PyCallExpressionHelper.getArgumentsMappedToKeywordContainer(mappedParameters);
      List<PyExpression> allArguments = ContainerUtil.concat(positionalArguments, keywordArguments);

      PyParamSpecType paramSpecType = getParamSpecTypeFromContainerParameters(keywordContainer, positionalContainer);
      if (paramSpecType != null) {
        // Keyword arguments for positional parameters preceding *args: P.args
        // might shadow the values in ParamSpec, causing runtime errors. Report them when P is unsubstituted.
        PyCallableParameterListType paramSpecSubst = getParamSpecSubstitution(paramSpecType, substitutions);
        if (paramSpecSubst == null) {
          for (var entry : regularMappedParameters.entrySet()) {
            if (entry.getKey() instanceof PyKeywordArgument) {
              unexpectedArgumentForParamSpecs.add(new UnexpectedArgumentForParamSpec(entry.getKey(), paramSpecType));
            }
          }
        }
        analyzeParamSpec(paramSpecType, allArguments, substitutions, result, unexpectedArgumentForParamSpecs,
                         unfilledParameterFromParamSpecs);
      }
      else {
        if (positionalContainer != null) {
          result.addAll(analyzeContainerMapping(positionalContainer, positionalArguments, substitutions));
        }
        if (keywordContainer != null) {
          result.addAll(analyzeContainerMapping(keywordContainer, keywordArguments, substitutions));
        }
      }

      List<UnfilledPositionalVararg> unfilledPositionalVarargs = new ArrayList<>();
      for (var unmappedContainer : mapping.getUnmappedContainerParameters()) {
        PyType containerType = unmappedContainer.getArgumentType(myTypeEvalContext);
        if (unmappedContainer.getName() == null || !(containerType instanceof PyPositionalVariadicType)) continue;
        PyType expandedVararg = PyTypeChecker.substitute(containerType, substitutions, myTypeEvalContext);
        if (!(expandedVararg instanceof PyUnpackedTupleType unpackedTuple) || unpackedTuple.isUnbound()) continue;
        if (unpackedTuple.getElementTypes().isEmpty()) continue;
        if (ContainerUtil.all(unpackedTuple.getElementTypes(), e -> e instanceof PyPositionalVariadicType)) continue;
        unfilledPositionalVarargs.add(
          new UnfilledPositionalVararg(unmappedContainer.getName(),
                                       PythonDocumentationProvider.getTypeName(expandedVararg, myTypeEvalContext)));
      }

      return new AnalyzeCalleeResults(callableType, callableType.getCallable(), result,
                                      unexpectedArgumentForParamSpecs,
                                      unfilledParameterFromParamSpecs,
                                      unfilledPositionalVarargs);
    }

    private boolean isConstructorCall(@NotNull PyCallSiteOwner callSite) {
      if (callSite instanceof PyCallExpression callExpression) {
        PyExpression callee = callExpression.getCallee();
        if (callee != null && myTypeEvalContext.getType(callee) instanceof PyClassType calleeType && calleeType.isDefinition()) {
          return true;
        }
      }
      return false;
    }

    private void analyzeParamSpec(@NotNull PyParamSpecType paramSpec, @NotNull List<PyExpression> arguments,
                                  @NotNull PyTypeChecker.GenericSubstitutions substitutions,
                                  @NotNull List<AnalyzeArgumentResult> result,
                                  @NotNull List<UnexpectedArgumentForParamSpec> unexpectedArgumentForParamSpecs,
                                  @NotNull List<UnfilledParameterFromParamSpec> unfilledParameterFromParamSpecs) {
      PyCallableParameterListType paramSpecSubst = getParamSpecSubstitution(paramSpec, substitutions);
      if (paramSpecSubst == null) {
        analyzeUnsubstitutedParamSpec(paramSpec, arguments, unexpectedArgumentForParamSpecs);
        return;
      }

      var mapping = PyCallExpressionHelper.analyzeArguments(arguments, paramSpecSubst, myTypeEvalContext);
      for (var item : mapping.getMappedParameters().entrySet()) {
        PyExpression argument = item.getKey();
        PyCallableParameter parameter = item.getValue();
        PyType argType = myTypeEvalContext.getType(argument);
        PyType paramType = parameter.getType(myTypeEvalContext);
        boolean matched = matchParameterAndArgument(paramType, argType, argument, substitutions);
        result.add(new AnalyzeArgumentResult(argument, paramType, substituteGenerics(paramType, substitutions), argType, matched));
      }
      if (!mapping.getUnmappedArguments().isEmpty()) {
        for (var argument : mapping.getUnmappedArguments()) {
          unexpectedArgumentForParamSpecs.add(new UnexpectedArgumentForParamSpec(argument, paramSpec));
        }
      }
      var unmappedParameters = mapping.getUnmappedParameters();
      if (!unmappedParameters.isEmpty()) {
        unfilledParameterFromParamSpecs.add(new UnfilledParameterFromParamSpec(unmappedParameters.get(0), paramSpec));
      }
    }

    private void analyzeUnsubstitutedParamSpec(@NotNull PyParamSpecType paramSpec,
                                               @NotNull List<PyExpression> arguments,
                                               @NotNull List<UnexpectedArgumentForParamSpec> unexpectedArgs) {
      for (PyExpression argument : arguments) {
        if (argument instanceof PyStarArgument starArg) {
          PyExpression innerExpr = starArg.getExpression();
          if (innerExpr != null && isParamSpecContainerForwarding(innerExpr, paramSpec, !starArg.isKeyword())) {
            continue;
          }
        }
        unexpectedArgs.add(new UnexpectedArgumentForParamSpec(argument, paramSpec));
      }
    }

    private static @Nullable PyCallableParameterListType getParamSpecSubstitution(@NotNull PyParamSpecType paramSpecType,
                                                                                  @NotNull PyTypeChecker.GenericSubstitutions substitutions) {
      return as(substitutions.getParamSpecs().get(paramSpecType), PyCallableParameterListType.class);
    }

    private boolean isParamSpecContainerForwarding(@NotNull PyExpression expr,
                                                   @NotNull PyParamSpecType paramSpec,
                                                   boolean expectPositional) {
      PyType type = myTypeEvalContext.getType(expr);
      if (!(type instanceof PyParamSpecType exprParamSpec) || !exprParamSpec.equals(paramSpec)) {
        return false;
      }
      if (expr instanceof PyReferenceExpression refExpr) {
        PsiElement resolved = refExpr.getReference().resolve();
        if (resolved instanceof PyNamedParameter param) {
          return expectPositional ? param.isPositionalContainer() : param.isKeywordContainer();
        }
      }
      return true;
    }

    private void matchArgumentsAndTypes(@NotNull List<PyExpression> arguments, @NotNull List<PyType> types,
                                        @NotNull PyTypeChecker.GenericSubstitutions substitutions,
                                        @NotNull List<AnalyzeArgumentResult> result) {
      final var size = Math.min(arguments.size(), types.size());
      for (int i = 0; i < size; ++i) {
        final var expected = types.get(i);
        final var argument = arguments.get(i);
        final var actual = myTypeEvalContext.getType(argument);
        final var matched = matchParameterAndArgument(expected, actual, argument, substitutions);
        result.add(new AnalyzeArgumentResult(argument, expected, substituteGenerics(expected, substitutions), actual, matched));
      }
    }

    private @NotNull List<AnalyzeArgumentResult> analyzeContainerMapping(@NotNull PyCallableParameter container,
                                                                         @NotNull List<PyExpression> arguments,
                                                                         @NotNull PyTypeChecker.GenericSubstitutions substitutions) {
      final PyType expected = container.getArgumentType(myTypeEvalContext);

      if (container.isPositionalContainer() && expected instanceof PyPositionalVariadicType) {
        PyUnpackedTupleType argumentTypes = PyUnpackedTupleTypeImpl.create(ContainerUtil.map(arguments, myTypeEvalContext::getType));
        boolean matched = matchParameterAndArgument(expected, argumentTypes, null, substitutions);
        return ContainerUtil.map(arguments, argument -> {
          PyType expectedWithSubstitutions = substituteGenerics(expected, substitutions);
          // TODO Don't duplicate the message of each argument, highlight an entire series of them
          return new AnalyzeArgumentResult(argument, expected, expectedWithSubstitutions, argumentTypes, matched);
        });
      }

      // For an expected type with generics we have to match all the actual types against it in order to do proper generic unification
      if (PyTypeChecker.hasGenerics(expected, myTypeEvalContext)) {
        // First collect type parameter substitutions by matching the expected type with the union, if it's a keyword container
        // otherwise, match as usual arguments, passed to a function
        if (container.isKeywordContainer()) {
          PyType actualJoin = PyUnionType.union(ContainerUtil.map(arguments, myTypeEvalContext::getType));
          matchParameterAndArgument(expected, actualJoin, null, substitutions);
        }
        return ContainerUtil.map(arguments, argument -> {
          // Then match each argument type against the expected type after these substitutions.
          PyType actual = myTypeEvalContext.getType(argument);
          boolean matched = matchParameterAndArgument(expected, actual, argument, substitutions);
          return new AnalyzeArgumentResult(argument, expected, substituteGenerics(expected, substitutions), actual, matched);
        });
      }
      else {
        return ContainerUtil.map(
          arguments,
          argument -> {
            final PyType promotedToLiteral = PyLiteralType.Companion.promoteToLiteral(argument, expected, myTypeEvalContext, substitutions);
            final var actual = promotedToLiteral != null ? promotedToLiteral : myTypeEvalContext.getType(argument);
            boolean matched = matchParameterAndArgument(expected, actual, argument, substitutions);
            PyType expectedWithSubstitutions = substituteGenerics(expected, substitutions);
            return new AnalyzeArgumentResult(argument, expected, expectedWithSubstitutions, actual, matched);
          }
        );
      }
    }

    private @Nullable PyParamSpecType getParamSpecTypeFromContainerParameters(@Nullable PyCallableParameter positionalContainer,
                                                                              @Nullable PyCallableParameter keywordContainer) {
      if (positionalContainer == null && keywordContainer == null) return null;
      PyCallableParameter container = Objects.requireNonNullElse(positionalContainer, keywordContainer);
      return as(container.getType(myTypeEvalContext), PyParamSpecType.class);
    }

    private boolean matchParameterAndArgument(@Nullable PyType parameterType,
                                              @Nullable PyType argumentType,
                                              @Nullable PyExpression argument,
                                              @NotNull PyTypeChecker.GenericSubstitutions substitutions) {
      argument = PyUtil.peelArgument(argument);

      if (argument != null) {
        if (PyTypedDictType.isDictExpression(argument, myTypeEvalContext) &&
            parameterType instanceof PyTypedDictType expectedTypedDictType) {
          reportTypedDictProblems(expectedTypedDictType, argument);
          return true;
        }
        else if (parameterType instanceof PyUnpackedTypedDictType unpackedTypedDictType) {
          reportUnpackedTypedDictProblems(unpackedTypedDictType, argument);
          return true;
        }
      }

      return matchesExpectedType(parameterType, argumentType, argument, substitutions)
             && !PyProtocolsKt.matchingProtocolDefinitions(parameterType, argumentType, myTypeEvalContext);
    }

    private @Nullable PyType substituteGenerics(@Nullable PyType expectedArgumentType,
                                                @NotNull PyTypeChecker.GenericSubstitutions substitutions) {
      return PyTypeChecker.hasGenerics(expectedArgumentType, myTypeEvalContext)
             ? PyTypeChecker.substitute(expectedArgumentType, substitutions, myTypeEvalContext)
             : null;
    }

    private static boolean isMatched(@NotNull AnalyzeCalleeResults calleeResults) {
      return ContainerUtil.all(calleeResults.getResults(), AnalyzeArgumentResult::isMatched) &&
             calleeResults.getUnmatchedArguments().isEmpty() &&
             calleeResults.getUnmatchedParameters().isEmpty() &&
             calleeResults.getUnfilledPositionalVarargs().isEmpty();
    }

    private static @NotNull List<PyType> getArgumentTypes(@NotNull List<AnalyzeCalleeResults> calleesResults) {
      return ContainerUtil.map(
        calleesResults
          .stream()
          .map(AnalyzeCalleeResults::getResults)
          .max(Comparator.comparingInt(List::size))
          .orElse(Collections.emptyList()),
        AnalyzeArgumentResult::getActualType
      );
    }

    private static boolean hasExplicitType(@NotNull PsiElement node) {
      if (node instanceof PyAnnotationOwner owner && owner.getAnnotation() != null) return true;
      if (node instanceof PyTypeCommentOwner owner && owner.getTypeCommentAnnotation() != null) return true;
      return false;
    }

    private boolean targetOrResolvedHasExplicitType(@NotNull PyTargetExpression target) {
      PsiElement current = target;
      while (current instanceof PyTargetExpression currentTarget) {
        if (hasExplicitType(currentTarget)) return true;
        final PsiElement resolved = currentTarget.getReference(PyResolveContext.defaultContext(myTypeEvalContext)).resolve();
        if (resolved == current) break;
        current = resolved;
      }
      return false;
    }

    /**
     * Number of values produced by a tuple expression used as the right-hand side of an unpacking assignment.
     * A starred element contributes the length of its operand only when the operand is a statically known
     * (heterogeneous, bounded) tuple; if any starred operand has an indeterminate length, returns {@code -1}.
     */
    private int getUnpackedTupleLength(@NotNull PyTupleExpression rhsTuple) {
      int count = 0;
      for (PyExpression element : rhsTuple.getElements()) {
        if (element instanceof PyStarExpression starExpression) {
          final PyExpression operand = starExpression.getExpression();
          if (operand == null) return -1;
          if (!(myTypeEvalContext.getType(operand) instanceof PyTupleType operandTupleType) || operandTupleType.isHomogeneous()) {
            return -1;
          }
          count += operandTupleType.getElementCount();
        }
        else {
          count++;
        }
      }
      return count;
    }
  }

  @Override
  public void inspectionFinished(@NotNull LocalInspectionToolSession session, @NotNull ProblemsHolder problemsHolder) {
    if (LOG.isDebugEnabled()) {
      final Long startTime = session.getUserData(TIME_KEY);
      if (startTime != null) {
        LOG.debug(String.format("[%d] elapsed time: %d ms\n",
                                Thread.currentThread().getId(),
                                (System.nanoTime() - startTime) / 1000000));
      }
    }
  }

  static class AnalyzeCalleeResults {

    private final @NotNull PyCallableType myCallableType;

    private final @Nullable PyCallable myCallable;

    private final @NotNull List<AnalyzeArgumentResult> myResults;

    private final @NotNull List<UnexpectedArgumentForParamSpec> myUnexpectedArgumentForParamSpecs;

    private final @NotNull List<UnfilledParameterFromParamSpec> myUnfilledParameterFromParamSpecs;

    private final @NotNull List<UnfilledPositionalVararg> myUnfilledPositionalVarargs;

    AnalyzeCalleeResults(@NotNull PyCallableType callableType,
                         @Nullable PyCallable callable,
                         @NotNull List<AnalyzeArgumentResult> results,
                         @NotNull List<UnexpectedArgumentForParamSpec> unexpectedArgumentForParamSpecs,
                         @NotNull List<UnfilledParameterFromParamSpec> unfilledParameterFromParamSpecs,
                         @NotNull List<UnfilledPositionalVararg> unfilledPositionalVarargs) {
      myCallableType = callableType;
      myCallable = callable;
      myResults = results;
      myUnexpectedArgumentForParamSpecs = unexpectedArgumentForParamSpecs;
      myUnfilledParameterFromParamSpecs = unfilledParameterFromParamSpecs;
      myUnfilledPositionalVarargs = unfilledPositionalVarargs;
    }

    public @NotNull PyCallableType getCallableType() {
      return myCallableType;
    }

    public @Nullable PyCallable getCallable() {
      return myCallable;
    }

    public @NotNull List<AnalyzeArgumentResult> getResults() {
      return myResults;
    }

    @NotNull
    List<UnexpectedArgumentForParamSpec> getUnmatchedArguments() {
      return myUnexpectedArgumentForParamSpecs;
    }

    @NotNull
    List<UnfilledParameterFromParamSpec> getUnmatchedParameters() {
      return myUnfilledParameterFromParamSpecs;
    }

    @NotNull
    List<UnfilledPositionalVararg> getUnfilledPositionalVarargs() {
      return myUnfilledPositionalVarargs;
    }
  }

  static class AnalyzeArgumentResult {

    private final @NotNull PyExpression myArgument;

    private final @Nullable PyType myExpectedType;

    private final @Nullable PyType myExpectedTypeAfterSubstitution;

    private final @Nullable PyType myActualType;

    private final boolean myIsMatched;

    AnalyzeArgumentResult(@NotNull PyExpression argument,
                          @Nullable PyType expectedType,
                          @Nullable PyType expectedTypeAfterSubstitution,
                          @Nullable PyType actualType,
                          boolean isMatched) {
      myArgument = argument;
      myExpectedType = expectedType;
      myExpectedTypeAfterSubstitution = expectedTypeAfterSubstitution;
      myActualType = actualType;
      myIsMatched = isMatched;
    }

    public @NotNull PyExpression getArgument() {
      return myArgument;
    }

    public @Nullable PyType getExpectedType() {
      return myExpectedType;
    }

    public @Nullable PyType getExpectedTypeAfterSubstitution() {
      return myExpectedTypeAfterSubstitution;
    }

    public @Nullable PyType getActualType() {
      return myActualType;
    }

    public boolean isMatched() {
      return myIsMatched;
    }
  }

  static class UnfilledParameterFromParamSpec {
    private final PyCallableParameter myParameter;
    private final PyParamSpecType myParamSpecType;

    UnfilledParameterFromParamSpec(@NotNull PyCallableParameter parameter, @NotNull PyParamSpecType paramSpecType) {
      myParameter = parameter;
      myParamSpecType = paramSpecType;
    }

    public @NotNull PyCallableParameter getParameter() {
      return myParameter;
    }

    @NotNull
    PyParamSpecType getParamSpecType() {
      return myParamSpecType;
    }
  }

  static class UnexpectedArgumentForParamSpec {
    private final PyExpression myArgument;
    private final PyParamSpecType myParamSpecType;

    UnexpectedArgumentForParamSpec(@NotNull PyExpression argument, @NotNull PyParamSpecType paramSpecType) {
      myArgument = argument;
      myParamSpecType = paramSpecType;
    }

    @NotNull
    PyExpression getArgument() {
      return myArgument;
    }

    @NotNull
    PyParamSpecType getParamSpecType() {
      return myParamSpecType;
    }
  }

  record UnfilledPositionalVararg(@NotNull String varargName, @NotNull String expectedTypes) {
  }
}
