// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.codeInsight.typing.PyProtocolsKt;
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.documentation.PythonDocumentationProvider;
import com.jetbrains.python.inspections.quickfix.PyMakeFunctionReturnTypeQuickFix;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.jetbrains.python.psi.PyUtil.as;
import static com.jetbrains.python.psi.impl.PyCallExpressionHelper.*;

public class PyTypeCheckerInspection extends PyInspection {
  private static final Logger LOG = Logger.getInstance(PyTypeCheckerInspection.class.getName());
  private static final Key<Long> TIME_KEY = Key.create("PyTypeCheckerInspection.StartTime");

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly, @NotNull LocalInspectionToolSession session) {
    if (LOG.isDebugEnabled()) {
      session.putUserData(TIME_KEY, System.nanoTime());
    }
    return new Visitor(holder, PyInspectionVisitor.getContext(session));
  }

  public static class Visitor extends PyInspectionVisitor {
    public Visitor(@Nullable ProblemsHolder holder, @NotNull TypeEvalContext context) {
      super(holder, context);
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
      // TODO: Support slice PySliceExpressions
      // Type check in TypedDict subscription expressions cannot be properly done because each key should have its own value type,
      // so this case is covered by PyTypedDictInspection
      if (myTypeEvalContext.getType(node.getOperand()) instanceof PyTypedDictType) return;
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
          PyExpression returnExpr = node.getExpression();
          PyType expected = getExpectedReturnType(function);
          PyType actual = returnExpr != null ? tryPromotingType(returnExpr, expected) : PyNoneType.INSTANCE;

          if (expected != null && actual instanceof PyTypedDictType) {
            if (reportTypedDictProblems(expected, (PyTypedDictType)actual, returnExpr)) return;
          }

          if (!PyTypeChecker.match(expected, actual, myTypeEvalContext)) {
            String expectedName = PythonDocumentationProvider.getVerboseTypeName(expected, myTypeEvalContext);
            String actualName = PythonDocumentationProvider.getTypeName(actual, myTypeEvalContext);
            var localQuickFix = new PyMakeFunctionReturnTypeQuickFix(function, returnExpr, actual, myTypeEvalContext);
            var globalQuickFix = new PyMakeFunctionReturnTypeQuickFix(function, returnExpr, null, myTypeEvalContext);
            registerProblem(returnExpr != null ? returnExpr : node,
                            PyPsiBundle.message("INSP.type.checker.expected.type.got.type.instead", expectedName, actualName),
                            localQuickFix, globalQuickFix);
          }
        }
      }
    }

    @Nullable
    private PyType getExpectedReturnType(@NotNull PyFunction function) {
      return getExpectedReturnType(function, myTypeEvalContext);
    }

    @Nullable
    public static PyType getExpectedReturnType(@NotNull PyFunction function, @NotNull TypeEvalContext typeEvalContext) {
      final PyType returnType = typeEvalContext.getReturnType(function);

      if (function.isAsync() || function.isGenerator()) {
        return Ref.deref(PyTypingTypeProvider.coroutineOrGeneratorElementType(returnType));
      }

      return returnType;
    }

    @Nullable
    public static PyType getActualReturnType(@NotNull PyFunction function, @Nullable PyExpression returnExpr,
                                             @NotNull TypeEvalContext context) {
      PyType returnTypeExpected = getExpectedReturnType(function, context);
      return returnExpr != null ? tryPromotingType(returnExpr, returnTypeExpected, context) : PyNoneType.INSTANCE;
    }

    @Override
    public void visitPyTargetExpression(@NotNull PyTargetExpression node) {
      // TODO: Check types in class-level assignments
      final ScopeOwner owner = ScopeUtil.getScopeOwner(node);
      if (owner instanceof PyClass) return;
      final PyExpression value = node.findAssignedValue();
      if (value == null) return;
      final PyType expected = myTypeEvalContext.getType(node);
      final PyType actual = tryPromotingType(value, expected);

      if (expected != null && actual instanceof PyTypedDictType) {
        if (reportTypedDictProblems(expected, (PyTypedDictType)actual, value)) return;
      }

      if (!PyTypeChecker.match(expected, actual, myTypeEvalContext)) {
        String expectedName = PythonDocumentationProvider.getVerboseTypeName(expected, myTypeEvalContext);
        String actualName = PythonDocumentationProvider.getTypeName(actual, myTypeEvalContext);
        registerProblem(value, PyPsiBundle.message("INSP.type.checker.expected.type.got.type.instead", expectedName, actualName));
      }
    }

    private boolean reportTypedDictProblems(@NotNull PyType expected, @NotNull PyTypedDictType actual, @NotNull PyExpression value) {
      final PyExpression valueWithoutKeyword = value instanceof PyKeywordArgument ? ((PyKeywordArgument)value).getValueExpression() : value;
      final PyTypedDictType.TypeCheckingResult result =
        PyTypedDictType.Companion.checkTypes(expected, actual, myTypeEvalContext, valueWithoutKeyword);
      if (result == null) return false;
      if (!result.getMatch()) {
        if (result.getValueTypeErrors().isEmpty() &&
            result.getExtraKeys().isEmpty() &&
            result.getMissingKeys().isEmpty()) {
          registerProblem(valueWithoutKeyword, PyPsiBundle.message("INSP.type.checker.expected.type.got.type.instead",
                                                                   PythonDocumentationProvider.getTypeName(expected, myTypeEvalContext),
                                                                   PythonDocumentationProvider.getTypeName(actual, myTypeEvalContext)));
        }

        if (!result.getValueTypeErrors().isEmpty()) {
          result.getValueTypeErrors().forEach(error -> {
            registerProblem(error.getActualExpression(), PyPsiBundle.message("INSP.type.checker.expected.type.got.type.instead",
                                                                             PythonDocumentationProvider.getTypeName(
                                                                               error.getExpectedType(), myTypeEvalContext),
                                                                             PythonDocumentationProvider.getTypeName(error.getActualType(),
                                                                                                                     myTypeEvalContext)));
          });
          if (!actual.isInferred()) {
            return true;
          }
        }
        if (!result.getExtraKeys().isEmpty()) {
          result.getExtraKeys().forEach(error -> {
            registerProblem(Objects.requireNonNullElse(error.getActualExpression(), valueWithoutKeyword),
                            PyPsiBundle.message("INSP.type.checker.typed.dict.extra.key", error.getKey(),
                                                error.getExpectedTypedDictName()));
          });
        }
        if (!result.getMissingKeys().isEmpty()) {
          result.getMissingKeys().forEach(error -> {
            final List<String> missingKeys = error.getMissingKeys();
            registerProblem(error.getActualExpression() != null ? error.getActualExpression() : valueWithoutKeyword,
                            PyPsiBundle.message("INSP.type.checker.typed.dict.missing.keys", error.getExpectedTypedDictName(),
                                                missingKeys.size(),
                                                StringUtil.join(missingKeys, s -> String.format("'%s'", s), ", ")));
          });
        }
      }
      return true;
    }

    @Nullable
    private PyType tryPromotingType(@NotNull PyExpression value, @Nullable PyType expected) {
      return tryPromotingType(value, expected, myTypeEvalContext);
    }

    @Nullable
    public static PyType tryPromotingType(@NotNull PyExpression value, @Nullable PyType expected, @NotNull TypeEvalContext context) {
      final PyType promotedToLiteral = PyLiteralType.Companion.promoteToLiteral(value, expected, context, null);
      if (promotedToLiteral != null) return promotedToLiteral;
      return context.getType(value);
    }

    @Override
    public void visitPyFunction(@NotNull PyFunction node) {
      final PyAnnotation annotation = node.getAnnotation();
      final String typeCommentAnnotation = node.getTypeCommentAnnotation();
      if (annotation != null || typeCommentAnnotation != null) {
        if (!PyUtil.isEmptyFunction(node)) {
          final ReturnVisitor visitor = new ReturnVisitor(node);
          node.getStatementList().accept(visitor);
          if (!visitor.myHasReturns) {
            final PyType expected = getExpectedReturnType(node);
            final String expectedName = PythonDocumentationProvider.getTypeName(expected, myTypeEvalContext);
            if (expected != null && !(expected instanceof PyNoneType)) {
              registerProblem(annotation != null ? annotation.getValue() : node.getTypeComment(),
                              PyPsiBundle.message("INSP.type.checker.expected.to.return.type.got.no.return", expectedName));
            }
          }
        }

        if (PyUtil.isInitMethod(node) && !(getExpectedReturnType(node) instanceof PyNoneType
                                           || PyTypingTypeProvider.isNoReturn(node, myTypeEvalContext))) {
          registerProblem(annotation != null ? annotation.getValue() : node.getTypeComment(),
                          PyPsiBundle.message("INSP.type.checker.init.should.return.none"));
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

    private static class ReturnVisitor extends PyRecursiveElementVisitor {
      private final PyFunction myFunction;
      private boolean myHasReturns = false;

      ReturnVisitor(PyFunction function) {
        myFunction = function;
      }

      @Override
      public void visitPyYieldExpression(@NotNull PyYieldExpression node) {
        if (ScopeUtil.getScopeOwner(node) == myFunction) {
          myHasReturns = true;
        }
      }

      @Override
      public void visitPyReturnStatement(@NotNull PyReturnStatement node) {
        if (ScopeUtil.getScopeOwner(node) == myFunction) {
          myHasReturns = true;
        }
      }
    }

    private void checkCallSite(@NotNull PyCallSiteExpression callSite) {
      final List<AnalyzeCalleeResults> calleesResults = StreamEx
        .of(mapArguments(callSite, getResolveContext()))
        .filter(mapping -> mapping.getUnmappedArguments().isEmpty() && mapping.getUnmappedParameters().isEmpty())
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

    @Nullable
    private AnalyzeCalleeResults analyzeCallee(@NotNull PyCallSiteExpression callSite,
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
        if (unmappedContainer.getName() == null || !(containerType instanceof PyVariadicType)) continue;
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
      paramSpec = Objects.requireNonNullElse(substitutions.getParamSpecs().get(paramSpec), paramSpec);
      List<PyCallableParameter> parameters = paramSpec.getParameters();
      if (parameters == null) return;

      var mapping = analyzeArguments(arguments, parameters, myTypeEvalContext);
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

    @NotNull
    private List<AnalyzeArgumentResult> analyzeContainerMapping(@NotNull PyCallableParameter container,
                                                                @NotNull List<PyExpression> arguments,
                                                                @NotNull PyTypeChecker.GenericSubstitutions substitutions) {
      final PyType expected = container.getArgumentType(myTypeEvalContext);

      if (container.isPositionalContainer() && expected instanceof PyVariadicType) {
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
        // First collect type parameter substitutions by matching the expected type with the union.
        PyType actualJoin = PyUnionType.union(ContainerUtil.map(arguments, myTypeEvalContext::getType));
        matchParameterAndArgument(expected, actualJoin, null, substitutions);
        PyType expectedWithSubstitutions = substituteGenerics(expected, substitutions);
        return ContainerUtil.map(arguments, argument -> {
          // Then match each argument type against the expected type after these substitutions.
          PyType actual = myTypeEvalContext.getType(argument);
          boolean matched = matchParameterAndArgument(expected, actual, argument, substitutions);
          return new AnalyzeArgumentResult(argument, expected, expectedWithSubstitutions, actual, matched);
        });
      }
      else {
        return ContainerUtil.map(
          arguments,
          argument -> {
            PyType actual = myTypeEvalContext.getType(argument);
            boolean matched = matchParameterAndArgument(expected, actual, argument, substitutions);
            PyType expectedWithSubstitutions = substituteGenerics(expected, substitutions);
            return new AnalyzeArgumentResult(argument, expected, expectedWithSubstitutions, actual, matched);
          }
        );
      }
    }

    @Nullable
    private PyParamSpecType getParamSpecTypeFromContainerParameters(@Nullable PyCallableParameter positionalContainer,
                                                                    @Nullable PyCallableParameter keywordContainer) {
      if (positionalContainer == null && keywordContainer == null) return null;
      PyCallableParameter container = Objects.requireNonNullElse(positionalContainer, keywordContainer);
      return as(container.getType(myTypeEvalContext), PyParamSpecType.class);
    }

    private boolean matchParameterAndArgument(@Nullable PyType parameterType,
                                              @Nullable PyType argumentType,
                                              @Nullable PyExpression argument,
                                              @NotNull PyTypeChecker.GenericSubstitutions substitutions) {
      if (parameterType != null && argumentType instanceof PyTypedDictType && argument != null) {
        if (reportTypedDictProblems(parameterType, (PyTypedDictType)argumentType, argument)) return true;
      }

      return PyTypeChecker.match(parameterType, argumentType, myTypeEvalContext, substitutions) &&
             !PyProtocolsKt.matchingProtocolDefinitions(parameterType, argumentType, myTypeEvalContext);
    }

    @Nullable
    private PyType substituteGenerics(@Nullable PyType expectedArgumentType, @NotNull PyTypeChecker.GenericSubstitutions substitutions) {
      return PyTypeChecker.hasGenerics(expectedArgumentType, myTypeEvalContext)
             ? PyTypeChecker.substitute(expectedArgumentType, substitutions, myTypeEvalContext)
             : null;
    }

    private static boolean matchedCalleeResultsExist(@NotNull List<AnalyzeCalleeResults> calleesResults) {
      return ContainerUtil.exists(calleesResults, calleeResults ->
        ContainerUtil.all(calleeResults.getResults(), AnalyzeArgumentResult::isMatched) &&
        calleeResults.getUnmatchedArguments().isEmpty() &&
        calleeResults.getUnmatchedParameters().isEmpty() &&
        calleeResults.getUnfilledPositionalVarargs().isEmpty()
      );
    }

    @NotNull
    private static List<PyType> getArgumentTypes(@NotNull List<AnalyzeCalleeResults> calleesResults) {
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

    @NotNull
    private final PyCallableType myCallableType;

    @Nullable
    private final PyCallable myCallable;

    @NotNull
    private final List<AnalyzeArgumentResult> myResults;

    @NotNull
    private final List<UnexpectedArgumentForParamSpec> myUnexpectedArgumentForParamSpecs;

    @NotNull
    private final List<UnfilledParameterFromParamSpec> myUnfilledParameterFromParamSpecs;
    
    @NotNull
    private final List<UnfilledPositionalVararg> myUnfilledPositionalVarargs;

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

    @NotNull
    public PyCallableType getCallableType() {
      return myCallableType;
    }

    @Nullable
    public PyCallable getCallable() {
      return myCallable;
    }

    @NotNull
    public List<AnalyzeArgumentResult> getResults() {
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

    @NotNull
    private final PyExpression myArgument;

    @Nullable
    private final PyType myExpectedType;

    @Nullable
    private final PyType myExpectedTypeAfterSubstitution;

    @Nullable
    private final PyType myActualType;

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

    @NotNull
    public PyExpression getArgument() {
      return myArgument;
    }

    @Nullable
    public PyType getExpectedType() {
      return myExpectedType;
    }

    @Nullable
    public PyType getExpectedTypeAfterSubstitution() {
      return myExpectedTypeAfterSubstitution;
    }

    @Nullable
    public PyType getActualType() {
      return myActualType;
    }

    public boolean isMatched() {
      return myIsMatched;
    }
  }

  static class UnfilledParameterFromParamSpec {
    final private PyCallableParameter myParameter;
    final private PyParamSpecType myParamSpecType;

    UnfilledParameterFromParamSpec(@NotNull PyCallableParameter parameter, @NotNull PyParamSpecType paramSpecType) {
      myParameter = parameter;
      myParamSpecType = paramSpecType;
    }

    @NotNull
    public PyCallableParameter getParameter() {
      return myParameter;
    }

    @NotNull
    PyParamSpecType getParamSpecType() {
      return myParamSpecType;
    }
  }

  static class UnexpectedArgumentForParamSpec {
    final private PyExpression myArgument;
    final private PyParamSpecType myParamSpecType;

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
