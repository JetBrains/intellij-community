// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.codeInsight.typing.PyProtocolsKt;
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider.GeneratorTypeDescriptor;
import com.jetbrains.python.documentation.PythonDocumentationProvider;
import com.jetbrains.python.inspections.quickfix.PyMakeFunctionReturnTypeQuickFix;
import com.jetbrains.python.psi.PyAnnotation;
import com.jetbrains.python.psi.PyAnnotationOwner;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyCallSiteExpression;
import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyComprehensionElement;
import com.jetbrains.python.psi.PyComprehensionForComponent;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyForStatement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameterList;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyReferenceOwner;
import com.jetbrains.python.psi.PyReturnStatement;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PySubscriptionExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyTypeCommentOwner;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.PyWithItem;
import com.jetbrains.python.psi.PyWithStatement;
import com.jetbrains.python.psi.PyYieldExpression;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyCallExpressionHelper;
import com.jetbrains.python.psi.impl.PySubscriptionExpressionImpl;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyABCUtil;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyCallableParameterListType;
import com.jetbrains.python.psi.types.PyCallableType;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyCollectionType;
import com.jetbrains.python.psi.types.PyConcatenateType;
import com.jetbrains.python.psi.types.PyDescriptorTypeUtil;
import com.jetbrains.python.psi.types.PyLiteralType;
import com.jetbrains.python.psi.types.PyNeverType;
import com.jetbrains.python.psi.types.PyParamSpecType;
import com.jetbrains.python.psi.types.PyPositionalVariadicType;
import com.jetbrains.python.psi.types.PySelfType;
import com.jetbrains.python.psi.types.PyTupleType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeChecker;
import com.jetbrains.python.psi.types.PyTypeInferenceCspFactory;
import com.jetbrains.python.psi.types.PyTypeParameterType;
import com.jetbrains.python.psi.types.PyTypedDictType;
import com.jetbrains.python.psi.types.PyUnionType;
import com.jetbrains.python.psi.types.PyUnpackedTupleType;
import com.jetbrains.python.psi.types.PyUnpackedTupleTypeImpl;
import com.jetbrains.python.psi.types.TypeEvalContext;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.jetbrains.python.psi.PyUtil.as;
import static com.jetbrains.python.psi.impl.PyCallExpressionHelper.mapArguments;
import static com.jetbrains.python.psi.types.PyNoneTypeKt.isNoneType;

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
    return new PyReachableElementVisitor(new Visitor(holder, context), context);
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
          if (!PyTypeChecker.match(expected, actual, myTypeEvalContext)) {
            final String expectedName = PythonDocumentationProvider.getVerboseTypeName(expected, myTypeEvalContext);
            final String actualName = PythonDocumentationProvider.getTypeName(actual, myTypeEvalContext);
            getHolder()
              .problem(returnExpr != null ? returnExpr : node,
                       PyPsiBundle.message("INSP.type.checker.expected.type.got.type.instead", expectedName, actualName))
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
          String expectedName = PythonDocumentationProvider.getVerboseTypeName(annotatedReturnType, myTypeEvalContext);
          String actualName = PythonDocumentationProvider.getTypeName(inferredReturnType, myTypeEvalContext);
          getHolder()
            .problem(yieldExpr, PyPsiBundle.message("INSP.type.checker.expected.type.got.type.instead", expectedName, actualName))
            .fix(new PyMakeFunctionReturnTypeQuickFix(function, myTypeEvalContext))
            .register();
        }
        return null;
      }
      return annotatedGeneratorDesc;
    }

    private boolean checkYieldType(@Nullable PyType expectedYieldType, @NotNull PyYieldExpression node, @NotNull PyFunction function) {
      final PyType thisYieldType = node.getYieldType(myTypeEvalContext);
      if (!PyTypeChecker.match(expectedYieldType, thisYieldType, myTypeEvalContext)) {
        final PyExpression yieldExpr = node.getExpression();
        String expectedName = PythonDocumentationProvider.getVerboseTypeName(expectedYieldType, myTypeEvalContext);
        String actualName = PythonDocumentationProvider.getTypeName(thisYieldType, myTypeEvalContext);
        getHolder()
          .problem(yieldExpr != null ? yieldExpr : node,
                   PyPsiBundle.message("INSP.type.checker.yield.type.mismatch", expectedName, actualName))
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
        return null;
      }
      if (function.isAsync()) {
        return Ref.deref(PyTypingTypeProvider.coroutineOrGeneratorElementType(returnType));
      }
      return returnType;
    }

    @Override
    public void visitPyReferenceExpression(@NotNull PyReferenceExpression node) {
      checkClassAttributeAccess(node);
    }

    @Override
    public void visitPyTargetExpression(@NotNull PyTargetExpression node) {
      checkClassAttributeAccess(node);
      // TODO: Check types in class-level assignments
      final ScopeOwner owner = ScopeUtil.getScopeOwner(node);
      if (owner instanceof PyClass) return;
      final PyExpression value = node.findAssignedValue();
      if (value == null) return;

      boolean descriptor = false;
      PyType expected = myTypeEvalContext.getType(node);
      if (node.isQualified()) {
        PyTypeChecker.GenericSubstitutions substitutions = PyTypeChecker.unifyReceiver(node.getQualifier(), myTypeEvalContext);
        expected = PyTypeChecker.substitute(expected, substitutions, myTypeEvalContext);
      }
      Ref<PyType> classAttrType = getClassAttributeType(node);
      if (classAttrType != null) {
        Ref<PyType> dunderSetValueType =
          PyDescriptorTypeUtil.getExpectedValueTypeForDunderSet(node, classAttrType.get(), myTypeEvalContext);
        if (dunderSetValueType != null) {
          expected = dunderSetValueType.get();
          descriptor = true;
        }
      }

      if (expected instanceof PyTypedDictType expectedTypedDictType && PyTypedDictType.isDictExpression(value, myTypeEvalContext)) {
        reportTypedDictProblems(expectedTypedDictType, value);
        return;
      }

      final PyType actual = tryPromotingType(value, expected);
      if (!PyTypeChecker.match(expected, actual, myTypeEvalContext)) {
        String expectedName = PythonDocumentationProvider.getVerboseTypeName(expected, myTypeEvalContext);
        String actualName = PythonDocumentationProvider.getTypeName(actual, myTypeEvalContext);
        registerProblem(value, descriptor ?
                               PyPsiBundle.message("INSP.type.checker.expected.type.from.dunder.set.got.type.instead",
                                                   expectedName, actualName) :
                               PyPsiBundle.message("INSP.type.checker.expected.type.got.type.instead", expectedName, actualName));
      }
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

    private @Nullable Ref<PyType> getClassAttributeType(@NotNull PyTargetExpression attribute) {
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
                        PyPsiBundle.message("INSP.type.checker.expected.type.got.type.instead",
                                            PythonDocumentationProvider.getTypeName(error.getExpectedType(), myTypeEvalContext),
                                            PythonDocumentationProvider.getTypeName(error.getActualType(), myTypeEvalContext)));
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
            final String expectedName = PythonDocumentationProvider.getVerboseTypeName(expected, myTypeEvalContext);
            final String actualName =
              PythonDocumentationProvider.getTypeName(node.getReturnStatementType(myTypeEvalContext), myTypeEvalContext);
            final PsiElement annotationValue = annotation != null ? annotation.getValue() : node.getTypeComment();
            if (annotationValue != null) {
              getHolder()
                .problem(annotationValue, PyPsiBundle.message("INSP.type.checker.expected.type.got.type.instead", expectedName, actualName))
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
            String expectedName = PythonDocumentationProvider.getVerboseTypeName(inferredType, myTypeEvalContext);
            String actualName = PythonDocumentationProvider.getTypeName(annotatedType, myTypeEvalContext);
            final PsiElement annotationValue = annotation != null ? annotation.getValue() : node.getTypeComment();
            if (annotationValue != null) {
              getHolder()
                .problem(annotationValue, PyPsiBundle.message("INSP.type.checker.expected.type.got.type.instead", expectedName, actualName))
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

      final PyExpression defaultValue = node.getDefaultValue();
      if (defaultValue == null) return;

      final PyType expected = myTypeEvalContext.getType(node);
      final PyType actual = tryPromotingType(defaultValue, expected);
      if (Objects.equals(actual, PyBuiltinCache.getInstance(node).getEllipsisType()) && isProtocolMethodParameter(node)) {
        return;
      }
      if (!PyTypeChecker.match(expected, actual, myTypeEvalContext)) {
        final String expectedName = PythonDocumentationProvider.getVerboseTypeName(expected, myTypeEvalContext);
        final String actualName = PythonDocumentationProvider.getTypeName(actual, myTypeEvalContext);
        registerProblem(defaultValue, PyPsiBundle.message("INSP.type.checker.expected.type.got.type.instead", expectedName, actualName));
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

    @Override
    public void visitPyComprehensionElement(@NotNull PyComprehensionElement node) {
      super.visitPyComprehensionElement(node);

      for (PyComprehensionForComponent forComponent : node.getForComponents()) {
        checkIteratedValue(forComponent.getIteratedList(), forComponent.isAsync());
      }
    }

    private void checkCallSite(@NotNull PyCallSiteExpression callSite) {
      final List<AnalyzeCalleeResults> calleesResults = StreamEx
        .of(mapArguments(callSite, getResolveContext()))
        .filter(mapping -> mapping.isComplete())
        .map(mapping -> analyzeCallee(callSite, mapping))
        .nonNull()
        .toList();

      if (!ContainerUtil.exists(calleesResults, calleeResults -> isMatched(calleeResults))) {
        PyTypeCheckerInspectionProblemRegistrar
          .registerProblem(this, callSite, getArgumentTypes(calleesResults), calleesResults, myTypeEvalContext);
      }
    }

    private boolean checkIteratedValue(@Nullable PyExpression iteratedValue, boolean isAsync) {
      if (iteratedValue != null) {
        final PyType type = myTypeEvalContext.getType(iteratedValue);
        final String iterableClassName = isAsync ? PyNames.ASYNC_ITERABLE : PyNames.ITERABLE;

        if (type != null &&
            !PyTypeChecker.isUnknown(type, myTypeEvalContext) &&
            !PyABCUtil.isSubtype(type, iterableClassName, myTypeEvalContext)) {
          final String typeName = PythonDocumentationProvider.getTypeName(type, myTypeEvalContext);

          String qualifiedName = "collections." + iterableClassName;
          registerProblem(iteratedValue, PyPsiBundle.message("INSP.type.checker.expected.type.got.type.instead", qualifiedName, typeName));
          return true;
        }
      }
      return false;
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
          registerProblem(iteratedValue, PyPsiBundle.message("INSP.type.checker.expected.type.got.type.instead", qualifiedName, typeName));
        }
      }
    }

    private @Nullable AnalyzeCalleeResults analyzeCallee(@NotNull PyCallSiteExpression callSite,
                                                         @NotNull PyCallExpression.PyArgumentsMapping mapping) {
      final PyCallableType callableType = mapping.getCallableType();
      if (callableType == null) return null;

      final List<AnalyzeArgumentResult> result = new ArrayList<>();
      final List<UnexpectedArgumentForParamSpec> unexpectedArgumentForParamSpecs = new ArrayList<>();
      final List<UnfilledParameterFromParamSpec> unfilledParameterFromParamSpecs = new ArrayList<>();

      final var receiver = callSite.getReceiver(callableType.getCallable());
      final var substitutions = PyTypeInferenceCspFactory.unifyReceiver(mapping, myTypeEvalContext);

      // When a constructor call resolves to `__init__` method,
      // match the class being constructed against the type of `self` parameter.
      if (PyUtil.isInitMethod(callableType.getCallable()) &&
          receiver != null &&
          myTypeEvalContext.getType(receiver) instanceof PyClassType receiverType &&
          receiverType.isDefinition()) {
        PyCallableParameter selfParameter = ContainerUtil.getFirstItem(mapping.getImplicitParameters());
        if (selfParameter != null) {
          final PyType actual = receiverType.toInstance();
          final PyType expected = selfParameter.getArgumentType(myTypeEvalContext);
          final boolean matched = matchParameterAndArgument(expected, actual, receiver, substitutions);
          result.add(new AnalyzeArgumentResult(receiver, expected, substituteGenerics(expected, substitutions), actual, matched));
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
          final var argumentRightBound = Math.min(firstExpectedTypes.size(), allArguments.size());
          final var firstArguments = allArguments.subList(0, argumentRightBound);
          matchArgumentsAndTypes(firstArguments, firstExpectedTypes, substitutions, result);

          if (argumentRightBound < allArguments.size()) {
            final var paramSpec = concatenateType.getParamSpec();
            final var restArguments = allArguments.subList(argumentRightBound, allArguments.size());
            if (paramSpec != null) {
              analyzeParamSpec(paramSpec, restArguments, substitutions, result, unexpectedArgumentForParamSpecs,
                               unfilledParameterFromParamSpecs);
            }
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


    private void analyzeParamSpec(@NotNull PyParamSpecType paramSpec, @NotNull List<PyExpression> arguments,
                                  @NotNull PyTypeChecker.GenericSubstitutions substitutions,
                                  @NotNull List<AnalyzeArgumentResult> result,
                                  @NotNull List<UnexpectedArgumentForParamSpec> unexpectedArgumentForParamSpecs,
                                  @NotNull List<UnfilledParameterFromParamSpec> unfilledParameterFromParamSpecs) {
      PyCallableParameterListType paramSpecSubst = as(substitutions.getParamSpecs().get(paramSpec), PyCallableParameterListType.class);
      if (paramSpecSubst == null) return;

      var mapping = PyCallExpressionHelper.analyzeArguments(arguments, paramSpecSubst.getParameters(), myTypeEvalContext);
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

      if (parameterType instanceof PyTypedDictType expectedTypedDictType) {
        if (argument != null && PyTypedDictType.isDictExpression(argument, myTypeEvalContext)) {
          reportTypedDictProblems(expectedTypedDictType, argument);
          return true;
        }
      }

      return PyTypeChecker.match(parameterType, argumentType, myTypeEvalContext, substitutions) &&
             !PyProtocolsKt.matchingProtocolDefinitions(parameterType, argumentType, myTypeEvalContext);
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
