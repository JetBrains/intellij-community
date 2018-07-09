// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.codeInsight.typing.PyProtocolsKt;
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.documentation.PythonDocumentationProvider;
import com.jetbrains.python.inspections.quickfix.PyMakeFunctionReturnTypeQuickFix;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

import static com.jetbrains.python.psi.impl.PyCallExpressionHelper.*;

/**
 * @author vlan
 */
public class PyTypeCheckerInspection extends PyInspection {
  private static final Logger LOG = Logger.getInstance(PyTypeCheckerInspection.class.getName());
  private static final Key<Long> TIME_KEY = Key.create("PyTypeCheckerInspection.StartTime");

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly, @NotNull LocalInspectionToolSession session) {
    if (LOG.isDebugEnabled()) {
      session.putUserData(TIME_KEY, System.nanoTime());
    }
    return new Visitor(holder, session);
  }

  public static class Visitor extends PyInspectionVisitor {
    public Visitor(@Nullable ProblemsHolder holder, @NotNull LocalInspectionToolSession session) {
      super(holder, session);
    }

    // TODO: Visit decorators with arguments
    @Override
    public void visitPyCallExpression(PyCallExpression node) {
      checkCallSite(node);
    }

    @Override
    public void visitPyBinaryExpression(PyBinaryExpression node) {
      checkCallSite(node);
    }

    @Override
    public void visitPySubscriptionExpression(PySubscriptionExpression node) {
      // TODO: Support slice PySliceExpressions
      checkCallSite(node);
    }

    @Override
    public void visitPyForStatement(PyForStatement node) {
      checkIteratedValue(node.getForPart().getSource(), node.isAsync());
    }

    @Override
    public void visitPyReturnStatement(PyReturnStatement node) {
      final ScopeOwner owner = ScopeUtil.getScopeOwner(node);
      if (owner instanceof PyFunction) {
        final PyFunction function = (PyFunction)owner;
        final PyAnnotation annotation = function.getAnnotation();
        final String typeCommentAnnotation = function.getTypeCommentAnnotation();
        if (annotation != null || typeCommentAnnotation != null) {
          final PyExpression returnExpr = node.getExpression();
          final PyType actual = returnExpr != null ? myTypeEvalContext.getType(returnExpr) : PyNoneType.INSTANCE;
          final PyType expected = getExpectedReturnType(function);
          if (!PyTypeChecker.match(expected, actual, myTypeEvalContext)) {
            final String expectedName = PythonDocumentationProvider.getTypeName(expected, myTypeEvalContext);
            final String actualName = PythonDocumentationProvider.getTypeName(actual, myTypeEvalContext);
            PyMakeFunctionReturnTypeQuickFix localQuickFix = new PyMakeFunctionReturnTypeQuickFix(function, actualName, myTypeEvalContext);
            PyMakeFunctionReturnTypeQuickFix globalQuickFix = new PyMakeFunctionReturnTypeQuickFix(function, null, myTypeEvalContext);
            registerProblem(returnExpr != null ? returnExpr : node,
                            String.format("Expected type '%s', got '%s' instead", expectedName, actualName),
                            localQuickFix, globalQuickFix);
          }
        }
      }
    }

    @Nullable
    private PyType getExpectedReturnType(@NotNull PyFunction function) {
      final PyType returnType = myTypeEvalContext.getReturnType(function);

      if (function.isAsync() || function.isGenerator()) {
        return Ref.deref(PyTypingTypeProvider.coroutineOrGeneratorElementType(returnType));
      }

      return returnType;
    }

    @Override
    public void visitPyFunction(PyFunction node) {
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
                              String.format("Expected to return '%s', got no return", expectedName));
            }
          }
        }

        if (PyUtil.isInit(node) && !(getExpectedReturnType(node) instanceof PyNoneType)) {
          registerProblem(annotation != null ? annotation.getValue() : node.getTypeComment(),
                          PyNames.INIT + " should return " + PyNames.NONE);
        }
      }
    }

    @Override
    public void visitPyComprehensionElement(PyComprehensionElement node) {
      super.visitPyComprehensionElement(node);

      for (PyComprehensionForComponent forComponent : node.getForComponents()) {
        checkIteratedValue(forComponent.getIteratedList(), forComponent.isAsync());
      }
    }

    private static class ReturnVisitor extends PyRecursiveElementVisitor {
      private final PyFunction myFunction;
      private boolean myHasReturns = false;

      public ReturnVisitor(PyFunction function) {
        myFunction = function;
      }

      @Override
      public void visitPyReturnStatement(PyReturnStatement node) {
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

          registerProblem(iteratedValue, String.format("Expected 'collections.%s', got '%s' instead", iterableClassName, typeName));
        }
      }
    }

    @Nullable
    private AnalyzeCalleeResults analyzeCallee(@NotNull PyCallSiteExpression callSite, @NotNull PyCallExpression.PyArgumentsMapping mapping) {
      final PyCallExpression.PyMarkedCallee markedCallee = mapping.getMarkedCallee();
      if (markedCallee == null) return null;

      final List<AnalyzeArgumentResult> result = new ArrayList<>();

      final PyExpression receiver = callSite.getReceiver(markedCallee.getElement());
      final Map<PyGenericType, PyType> substitutions = PyTypeChecker.unifyReceiver(receiver, myTypeEvalContext);
      final Map<PyExpression, PyCallableParameter> mappedParameters = mapping.getMappedParameters();

      for (Map.Entry<PyExpression, PyCallableParameter> entry : getRegularMappedParameters(mappedParameters).entrySet()) {
        final PyExpression argument = entry.getKey();
        final PyCallableParameter parameter = entry.getValue();
        final PyType expected = parameter.getArgumentType(myTypeEvalContext);
        final PyType actual = myTypeEvalContext.getType(argument);
        final boolean matched = matchParameterAndArgument(expected, actual, substitutions);
        result.add(new AnalyzeArgumentResult(argument, expected, substituteGenerics(expected, substitutions), actual, matched));
      }
      final PyCallableParameter positionalContainer = getMappedPositionalContainer(mappedParameters);
      if (positionalContainer != null) {
        result.addAll(analyzeContainerMapping(positionalContainer, getArgumentsMappedToPositionalContainer(mappedParameters), substitutions));
      }
      final PyCallableParameter keywordContainer = getMappedKeywordContainer(mappedParameters);
      if (keywordContainer != null) {
        result.addAll(analyzeContainerMapping(keywordContainer, getArgumentsMappedToKeywordContainer(mappedParameters), substitutions));
      }
      return new AnalyzeCalleeResults(markedCallee.getCallableType(), markedCallee.getElement(), result);
    }

    @NotNull
    private List<AnalyzeArgumentResult> analyzeContainerMapping(@NotNull PyCallableParameter container, @NotNull List<PyExpression> arguments,
                                                                @NotNull Map<PyGenericType, PyType> substitutions) {
      final PyType expected = container.getArgumentType(myTypeEvalContext);
      final PyType expectedWithSubstitutions = substituteGenerics(expected, substitutions);
      // For an expected type with generics we have to match all the actual types against it in order to do proper generic unification
      if (PyTypeChecker.hasGenerics(expected, myTypeEvalContext)) {
        final PyType actual = PyUnionType.union(arguments.stream().map(e -> myTypeEvalContext.getType(e)).collect(Collectors.toList()));
        final boolean matched = matchParameterAndArgument(expected, actual, substitutions);
        return arguments.stream()
          .map(argument -> new AnalyzeArgumentResult(argument, expected, expectedWithSubstitutions, actual, matched))
          .collect(Collectors.toList());
      }
      else {
        return arguments.stream()
          .map(argument -> {
            final PyType actual = myTypeEvalContext.getType(argument);
            final boolean matched = matchParameterAndArgument(expected, actual, substitutions);
            return new AnalyzeArgumentResult(argument, expected, expectedWithSubstitutions, actual, matched);
          })
          .collect(Collectors.toList());
      }
    }

    private boolean matchParameterAndArgument(@Nullable PyType parameterType,
                                              @Nullable PyType argumentType,
                                              @NotNull Map<PyGenericType, PyType> substitutions) {
      return PyTypeChecker.match(parameterType, argumentType, myTypeEvalContext, substitutions) &&
             !PyProtocolsKt.matchingProtocolDefinitions(parameterType, argumentType, myTypeEvalContext);
    }

    @Nullable
    private PyType substituteGenerics(@Nullable PyType expectedArgumentType, @NotNull Map<PyGenericType, PyType> substitutions) {
      return PyTypeChecker.hasGenerics(expectedArgumentType, myTypeEvalContext)
             ? PyTypeChecker.substitute(expectedArgumentType, substitutions, myTypeEvalContext)
             : null;
    }

    private static boolean matchedCalleeResultsExist(@NotNull List<AnalyzeCalleeResults> calleesResults) {
      return calleesResults
        .stream()
        .anyMatch(calleeResults -> calleeResults.getResults().stream().allMatch(AnalyzeArgumentResult::isMatched));
    }

    @NotNull
    private static List<PyType> getArgumentTypes(@NotNull List<AnalyzeCalleeResults> calleesResults) {
      return calleesResults
        .stream()
        .map(AnalyzeCalleeResults::getResults)
        .max(Comparator.comparingInt(List::size))
        .orElse(Collections.emptyList())
        .stream()
        .map(AnalyzeArgumentResult::getActualType)
        .collect(Collectors.toList());
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

  @Override
  @Nls
  @NotNull
  public String getDisplayName() {
    return "Type checker";
  }

  static class AnalyzeCalleeResults {

    @NotNull
    private final PyCallableType myCallableType;

    @Nullable
    private final PyCallable myCallable;

    @NotNull
    private final List<AnalyzeArgumentResult> myResults;

    public AnalyzeCalleeResults(@NotNull PyCallableType callableType,
                                @Nullable PyCallable callable,
                                @NotNull List<AnalyzeArgumentResult> results) {
      myCallableType = callableType;
      myCallable = callable;
      myResults = results;
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

    public AnalyzeArgumentResult(@NotNull PyExpression argument,
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
}
