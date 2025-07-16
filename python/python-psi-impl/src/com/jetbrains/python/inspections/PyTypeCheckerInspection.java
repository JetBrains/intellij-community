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
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PySubscriptionExpressionImpl;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.util.containers.ContainerUtil.exists;
import static com.jetbrains.python.psi.PyUtil.as;
import static com.jetbrains.python.psi.impl.PyCallExpressionHelper.*;
import static com.jetbrains.python.psi.types.PyNoneTypeKt.isNoneType;

public class PyTypeCheckerInspection extends PyInspection {
  private static final Logger LOG = Logger.getInstance(PyTypeCheckerInspection.class.getName());
  private static final Key<Long> TIME_KEY = Key.create("PyTypeCheckerInspection.StartTime");

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly, @NotNull LocalInspectionToolSession session) {
    if (LOG.isDebugEnabled()) {
      session.putUserData(TIME_KEY, System.nanoTime());
    }
    return new Visitor(holder, PyInspectionVisitor.getContext(session));
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
    public void visitPyReturnStatement(@NotNull PyReturnStatement node) {
      ScopeOwner owner = ScopeUtil.getScopeOwner(node);
      if (owner instanceof PyFunction function) {
        PyAnnotation annotation = function.getAnnotation();
        String typeCommentAnnotation = function.getTypeCommentAnnotation();
        if (annotation != null || typeCommentAnnotation != null) {
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
              .problem(returnExpr != null ? returnExpr : node,   PyPsiBundle.message("INSP.type.checker.expected.type.got.type.instead", expectedName, actualName))
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

      final PyAnnotation annotation = function.getAnnotation();
      final String typeCommentAnnotation = function.getTypeCommentAnnotation();
      if (annotation == null && typeCommentAnnotation == null) return;

      final PyType fullReturnType = myTypeEvalContext.getReturnType(function);
      if (fullReturnType == null) return; // fullReturnType is Any

      final var generatorDesc = GeneratorTypeDescriptor.create(fullReturnType);
      if (generatorDesc == null) {
        // expected type is not Iterable, Iterator, Generator or similar
        final PyType actual = function.getInferredReturnType(myTypeEvalContext);
        String expectedName = PythonDocumentationProvider.getVerboseTypeName(fullReturnType, myTypeEvalContext);
        String actualName = PythonDocumentationProvider.getTypeName(actual, myTypeEvalContext);
        getHolder()
          .problem(node, PyPsiBundle.message("INSP.type.checker.expected.type.got.type.instead", expectedName, actualName))
          .fix(new PyMakeFunctionReturnTypeQuickFix(function, myTypeEvalContext))
          .register();
        return;
      }

      final PyType expectedYieldType = generatorDesc.yieldType();
      final PyType expectedSendType = generatorDesc.sendType();

      final PyType thisYieldType = node.getYieldType(myTypeEvalContext);

      final PyExpression yieldExpr = node.getExpression();

      if (!PyTypeChecker.match(expectedYieldType, thisYieldType, myTypeEvalContext)) {
        String expectedName = PythonDocumentationProvider.getVerboseTypeName(expectedYieldType, myTypeEvalContext);
        String actualName = PythonDocumentationProvider.getTypeName(thisYieldType, myTypeEvalContext);
        getHolder()
          .problem(yieldExpr != null ? yieldExpr : node, PyPsiBundle.message("INSP.type.checker.yield.type.mismatch", expectedName, actualName))
          .fix(new PyMakeFunctionReturnTypeQuickFix(function, myTypeEvalContext))
          .register();
      }

      if (yieldExpr != null && node.isDelegating()) {
        final PyType delegateType = myTypeEvalContext.getType(yieldExpr);
        var delegateDesc = GeneratorTypeDescriptor.create(delegateType);
        if (delegateDesc == null) return;

        if (delegateDesc.isAsync()) {
          String delegateName = PythonDocumentationProvider.getTypeName(delegateType, myTypeEvalContext);
          registerProblem(yieldExpr, PyPsiBundle.message("INSP.type.checker.yield.from.async.generator", delegateName, delegateName));
          return;
        }

        // Reversed because SendType is contravariant
        if (!PyTypeChecker.match(delegateDesc.sendType(), expectedSendType, myTypeEvalContext)) {
          String expectedName = PythonDocumentationProvider.getVerboseTypeName(expectedSendType, myTypeEvalContext);
          String actualName = PythonDocumentationProvider.getTypeName(delegateDesc.sendType(), myTypeEvalContext);
          registerProblem(yieldExpr, PyPsiBundle.message("INSP.type.checker.yield.from.send.type.mismatch", expectedName, actualName));
        }
      }
    }


    public static @Nullable PyType getExpectedReturnStatementType(@NotNull PyFunction function, @NotNull TypeEvalContext typeEvalContext) {
      final PyType returnType = typeEvalContext.getReturnType(function);
      if (function.isGenerator()) {
        final var generatorDesc = GeneratorTypeDescriptor.create(returnType);
        if (generatorDesc != null) {
          return generatorDesc.returnType();
        }
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
      Ref<PyType> classAttrType = getClassAttributeType(node);
      if (classAttrType != null) {
        Ref<PyType> dunderSetValueType = PyDescriptorTypeUtil.getExpectedValueTypeForDunderSet(node, classAttrType.get(), myTypeEvalContext);
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
          !(type instanceof PySelfType)) return true;
      return type instanceof PyCollectionType collectionType &&
             exists(collectionType.getElementTypes(), Visitor::requiresTypeSpecialization);
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

    private @Nullable PyType tryPromotingType(@NotNull PyExpression value, @Nullable PyType expected) {
      final PyType promotedToLiteral = PyLiteralType.Companion.promoteToLiteral(value, expected, myTypeEvalContext, null);
      if (promotedToLiteral != null) return promotedToLiteral;
      return myTypeEvalContext.getType(value);
    }

    @Override
    public void visitPyFunction(@NotNull PyFunction node) {
      final PyAnnotation annotation = node.getAnnotation();
      final String typeCommentAnnotation = node.getTypeCommentAnnotation();
      if (annotation != null || typeCommentAnnotation != null) {
        final PyType expected = getExpectedReturnStatementType(node, myTypeEvalContext);
        final PyType noneType = PyBuiltinCache.getInstance(node).getNoneType();
        final boolean returnsNone = isNoneType(expected);
        final boolean returnsOptional = PyTypeChecker.match(expected, noneType, myTypeEvalContext);

        if (expected != null && !returnsOptional && !PyUtil.isEmptyFunction(node)) {
          final List<PyStatement> returnPoints = node.getReturnPoints(myTypeEvalContext);
          final boolean hasImplicitReturns = exists(returnPoints, it -> !(it instanceof PyReturnStatement));

          if (hasImplicitReturns) {
            final String expectedName = PythonDocumentationProvider.getVerboseTypeName(expected, myTypeEvalContext);
            final String actualName = PythonDocumentationProvider.getTypeName(node.getReturnStatementType(myTypeEvalContext), myTypeEvalContext);
            final PsiElement annotationValue = annotation != null ? annotation.getValue() : node.getTypeComment();
            if (annotationValue != null) {
              getHolder()
                .problem(annotationValue,   PyPsiBundle.message("INSP.type.checker.expected.type.got.type.instead", expectedName, actualName))
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
          boolean shouldBeAsync = node.isAsync() && node.isAsyncAllowed();
          final var generatorDesc = GeneratorTypeDescriptor.create(annotatedType);
          if (generatorDesc != null && generatorDesc.isAsync() != shouldBeAsync) {
            final PyType inferredType = node.getInferredReturnType(myTypeEvalContext);
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

      if (!matchedCalleeResultsExist(calleesResults)) {
        PyTypeCheckerInspectionProblemRegistrar
          .registerProblem(this, callSite, getArgumentTypes(calleesResults), calleesResults, myTypeEvalContext);
      }
    }

    private void checkIteratedValue(@Nullable PyExpression iteratedValue, boolean isAsync) {
      if (iteratedValue != null) {
        final PyType type = myTypeEvalContext.getType(iteratedValue);
        final String iterableClassName = isAsync ? PyNames.ASYNC_ITERABLE : PyNames.ITERABLE;

        if (type != null &&
            !PyTypeChecker.isUnknown(type, myTypeEvalContext) &&
            !PyABCUtil.isSubtype(type, iterableClassName, myTypeEvalContext)) {
          final String typeName = PythonDocumentationProvider.getTypeName(type, myTypeEvalContext);

          String qualifiedName = "collections." + iterableClassName;
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
      final var substitutions = PyTypeChecker.unifyReceiver(receiver, myTypeEvalContext);
      final var mappedParameters = mapping.getMappedParameters();
      final var regularMappedParameters = getRegularMappedParameters(mappedParameters);

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
            analyzeParamSpec(paramSpec, restArguments, substitutions, result, unexpectedArgumentForParamSpecs, unfilledParameterFromParamSpecs);
          }

          break;
        }
        else {
          final boolean matched = matchParameterAndArgument(expected, actual, argument, substitutions);
          result.add(new AnalyzeArgumentResult(argument, expected, substituteGenerics(expected, substitutions), actual, matched));
        }
      }

      PyCallableParameter positionalContainer = getMappedPositionalContainer(mappedParameters);
      List<PyExpression> positionalArguments = getArgumentsMappedToPositionalContainer(mappedParameters);
      PyCallableParameter keywordContainer = getMappedKeywordContainer(mappedParameters);
      List<PyExpression> keywordArguments = getArgumentsMappedToKeywordContainer(mappedParameters);
      List<PyExpression> allArguments = ContainerUtil.concat(positionalArguments, keywordArguments);

      PyParamSpecType paramSpecType = getParamSpecTypeFromContainerParameters(keywordContainer, positionalContainer);
      if (paramSpecType != null) {
        analyzeParamSpec(paramSpecType, allArguments, substitutions, result, unexpectedArgumentForParamSpecs, unfilledParameterFromParamSpecs);
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
      for (var unmappedContainer: mapping.getUnmappedContainerParameters()) {
        PyType containerType = unmappedContainer.getArgumentType(myTypeEvalContext);
        if (unmappedContainer.getName() == null || !(containerType instanceof PyPositionalVariadicType)) continue;
        PyType expandedVararg = PyTypeChecker.substitute(containerType, substitutions, myTypeEvalContext);
        if (!(expandedVararg instanceof PyUnpackedTupleType unpackedTuple) || unpackedTuple.isUnbound()) continue;
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

      var mapping = analyzeArguments(arguments, paramSpecSubst.getParameters(), myTypeEvalContext);
      for (var item: mapping.getMappedParameters().entrySet()) {
        PyExpression argument = item.getKey();
        PyCallableParameter parameter = item.getValue();
        PyType argType = myTypeEvalContext.getType(argument);
        PyType paramType = parameter.getType(myTypeEvalContext);
        boolean matched = matchParameterAndArgument(paramType, argType, argument, substitutions);
        result.add(new AnalyzeArgumentResult(argument, paramType, substituteGenerics(paramType, substitutions), argType, matched));
      }
      if (!mapping.getUnmappedArguments().isEmpty()) {
        for (var argument: mapping.getUnmappedArguments()) {
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

    private @Nullable PyType substituteGenerics(@Nullable PyType expectedArgumentType, @NotNull PyTypeChecker.GenericSubstitutions substitutions) {
      return PyTypeChecker.hasGenerics(expectedArgumentType, myTypeEvalContext)
             ? PyTypeChecker.substitute(expectedArgumentType, substitutions, myTypeEvalContext)
             : null;
    }

    private static boolean matchedCalleeResultsExist(@NotNull List<AnalyzeCalleeResults> calleesResults) {
      return exists(calleesResults, calleeResults ->
        ContainerUtil.all(calleeResults.getResults(), AnalyzeArgumentResult::isMatched) &&
        calleeResults.getUnmatchedArguments().isEmpty() &&
        calleeResults.getUnmatchedParameters().isEmpty() &&
        calleeResults.getUnfilledPositionalVarargs().isEmpty()
      );
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
