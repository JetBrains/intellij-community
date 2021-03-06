/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.python.inspections;

import com.google.common.collect.Sets;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.util.XmlStringUtil;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.codeInsight.typing.PyProtocolsKt;
import com.jetbrains.python.documentation.PythonDocumentationProvider;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyStructuralType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

class PyTypeCheckerInspectionProblemRegistrar {

  static void registerProblem(@NotNull PyInspectionVisitor visitor,
                              @NotNull PyCallSiteExpression callSite,
                              @NotNull List<PyType> argumentTypes,
                              @NotNull List<PyTypeCheckerInspection.AnalyzeCalleeResults> calleesResults,
                              @NotNull TypeEvalContext context) {
    if (calleesResults.size() == 1) {
      registerSingleCalleeProblem(visitor, calleesResults.get(0), context);
    }
    else if (!calleesResults.isEmpty()) {
      registerMultiCalleeProblem(visitor, callSite, argumentTypes, calleesResults, context);
    }
  }

  private static void registerSingleCalleeProblem(@NotNull PyInspectionVisitor visitor,
                                                  @NotNull PyTypeCheckerInspection.AnalyzeCalleeResults calleeResults,
                                                  @NotNull TypeEvalContext context) {
    for (PyTypeCheckerInspection.AnalyzeArgumentResult argumentResult : calleeResults.getResults()) {
      if (argumentResult.isMatched()) continue;

      visitor.registerProblem(argumentResult.getArgument(), getSingleCalleeProblemMessage(argumentResult, context));
    }
  }

  private static void registerMultiCalleeProblem(@NotNull PyInspectionVisitor visitor,
                                                 @NotNull PyCallSiteExpression callSite,
                                                 @NotNull List<PyType> argumentTypes,
                                                 @NotNull List<PyTypeCheckerInspection.AnalyzeCalleeResults> calleesResults,
                                                 @NotNull TypeEvalContext context) {
    if (callSite instanceof PyBinaryExpression) {
      registerMultiCalleeProblemForBinaryExpression(visitor, (PyBinaryExpression)callSite, argumentTypes, calleesResults, context);
    }
    else {
      visitor.registerProblem(getMultiCalleeElementToHighlight(callSite),
                              getMultiCalleeProblemMessage(argumentTypes, calleesResults, context, isOnTheFly(visitor)));
    }
  }

  @NotNull
  private static @InspectionMessage String getSingleCalleeProblemMessage(@NotNull PyTypeCheckerInspection.AnalyzeArgumentResult argumentResult,
                                                                         @NotNull TypeEvalContext context) {
    final PyType actualType = argumentResult.getActualType();
    final PyType expectedType = argumentResult.getExpectedType();

    assert actualType != null; // see PyTypeCheckerInspection.Visitor.analyzeArgument()
    assert expectedType != null; // see PyTypeCheckerInspection.Visitor.analyzeArgument()

    final String actualTypeName = PythonDocumentationProvider.getTypeName(actualType, context);

    if (expectedType instanceof PyStructuralType) {
      final Set<String> expectedAttributes = ((PyStructuralType)expectedType).getAttributeNames();
      final Set<String> actualAttributes = getAttributes(actualType, context);

      if (actualAttributes != null) {
        final Sets.SetView<String> missingAttributes = Sets.difference(expectedAttributes, actualAttributes);
        String missingAttributeList = StringUtil.join(missingAttributes, s -> String.format("'%s'", s), ", ");
        return PyPsiBundle.message("INSP.type.checker.type.does.not.have.expected.attribute",
                                   actualTypeName, missingAttributes.size(), missingAttributeList);
      }
    }

    @Nullable PyType expectedTypeAfterSubstitution = argumentResult.getExpectedTypeAfterSubstitution();
    String expectedTypeName = PythonDocumentationProvider.getTypeName(expectedType, context);
    String expectedSubstitutedName = expectedTypeAfterSubstitution != null
                                     ? PythonDocumentationProvider.getTypeName(expectedTypeAfterSubstitution, context)
                                     : null;

    if (PyProtocolsKt.matchingProtocolDefinitions(expectedType, actualType, context)) {
      if (expectedSubstitutedName != null) {
        return PyPsiBundle.message("INSP.type.checker.only.concrete.class.can.be.used.where.matched.protocol.expected",
                                   expectedSubstitutedName, expectedTypeName);
      }
      else {
        return PyPsiBundle.message("INSP.type.checker.only.concrete.class.can.be.used.where.protocol.expected", expectedTypeName);
      }
    }

    if (expectedSubstitutedName != null) {
      return PyPsiBundle.message("INSP.type.checker.expected.matched.type.got.type.instead", expectedSubstitutedName, expectedTypeName, actualTypeName);
    }
    else {
      return PyPsiBundle.message("INSP.type.checker.expected.type.got.type.instead", expectedTypeName, actualTypeName);
    }
  }

  private static void registerMultiCalleeProblemForBinaryExpression(@NotNull PyInspectionVisitor visitor,
                                                                    @NotNull PyBinaryExpression binaryExpression,
                                                                    @NotNull List<PyType> argumentTypes,
                                                                    @NotNull List<PyTypeCheckerInspection.AnalyzeCalleeResults> calleesResults,
                                                                    @NotNull TypeEvalContext context) {
    final Predicate<PyTypeCheckerInspection.AnalyzeCalleeResults> isRightOperatorResults =
      calleeResults -> binaryExpression.isRightOperator(calleeResults.getCallable());

    final boolean allCalleesAreRightOperators = calleesResults.stream().allMatch(isRightOperatorResults);

    final List<PyTypeCheckerInspection.AnalyzeCalleeResults> preferredOperatorsResults =
      allCalleesAreRightOperators
      ? calleesResults
      : ContainerUtil.filter(calleesResults, calleeResults -> !isRightOperatorResults.test(calleeResults));

    if (preferredOperatorsResults.size() == 1) {
      registerSingleCalleeProblem(visitor, preferredOperatorsResults.get(0), context);
    }
    else {
      visitor.registerProblem(
        allCalleesAreRightOperators ? binaryExpression.getLeftExpression() : binaryExpression.getRightExpression(),
        getMultiCalleeProblemMessage(argumentTypes, preferredOperatorsResults, context, isOnTheFly(visitor))
      );
    }
  }

  @NotNull
  private static PsiElement getMultiCalleeElementToHighlight(@NotNull PyCallSiteExpression callSite) {
    if (callSite instanceof PyCallExpression) {
      final PyCallExpression call = (PyCallExpression)callSite;
      final PyArgumentList argumentList = call.getArgumentList();

      final PsiElement result = Optional
        .ofNullable(argumentList)
        .map(PyArgumentList::getArguments)
        .filter(arguments -> arguments.length == 1)
        .<PsiElement>map(arguments -> arguments[0])
        .orElse(argumentList);

      return ObjectUtils.notNull(result, callSite);
    }
    else if (callSite instanceof PySubscriptionExpression) {
      return ObjectUtils.notNull(((PySubscriptionExpression)callSite).getIndexExpression(), callSite);
    }
    else {
      return callSite;
    }
  }

  @NotNull
  private static @InspectionMessage String getMultiCalleeProblemMessage(@NotNull List<PyType> argumentTypes,
                                                                        @NotNull List<PyTypeCheckerInspection.AnalyzeCalleeResults> calleesResults,
                                                                        @NotNull TypeEvalContext context,
                                                                        boolean isOnTheFly) {
    final String actualTypesRepresentation = getMultiCalleeActualTypesRepresentation(argumentTypes, context);
    final String expectedTypesRepresentation = getMultiCalleePossibleExpectedTypesRepresentation(calleesResults, context, isOnTheFly);

    if (isOnTheFly) {
      return new HtmlBuilder()
        .append(PyPsiBundle.message("INSP.type.checker.unexpected.types.prefix")).br().appendRaw(actualTypesRepresentation).br()
        .append(PyPsiBundle.message("INSP.type.checker.expected.types.prefix")).br().appendRaw(expectedTypesRepresentation)
        .wrapWith("html").toString();
    }
    else {
      return PyPsiBundle.message("INSP.type.checker.unexpected.types.prefix") + " " + actualTypesRepresentation + " " +
             PyPsiBundle.message("INSP.type.checker.expected.types.prefix") + " " +
             expectedTypesRepresentation;
    }
  }

  private static boolean isOnTheFly(@NotNull PyInspectionVisitor visitor) {
    final ProblemsHolder holder = visitor.getHolder();
    return holder != null && holder.isOnTheFly();
  }

  @Nullable
  private static Set<String> getAttributes(@NotNull PyType type, @NotNull TypeEvalContext context) {
    if (type instanceof PyStructuralType) {
      return ((PyStructuralType)type).getAttributeNames();
    }
    else if (type instanceof PyClassLikeType) {
      return ((PyClassLikeType)type).getMemberNames(true, context);
    }
    return null;
  }

  @NotNull
  private static @NlsSafe String getMultiCalleeActualTypesRepresentation(@NotNull List<PyType> argumentTypes,
                                                                         @NotNull TypeEvalContext context) {
    return argumentTypes
      .stream()
      .map(type -> PythonDocumentationProvider.getTypeName(type, context))
      .collect(Collectors.joining(", ", "(", ")"));
  }

  @NotNull
  private static @NlsSafe String getMultiCalleePossibleExpectedTypesRepresentation(@NotNull List<PyTypeCheckerInspection.AnalyzeCalleeResults> calleesResults,
                                                                                   @NotNull TypeEvalContext context,
                                                                                   boolean isOnTheFly) {
    return calleesResults
      .stream()
      .map(calleeResult -> {
        String expectedTypesRepresentation = getMultiCalleeExpectedTypesRepresentation(calleeResult.getResults(), context);
        return isOnTheFly ? XmlStringUtil.escapeString(expectedTypesRepresentation) : expectedTypesRepresentation;
      })
      .collect(Collectors.joining(isOnTheFly ? "<br>" : " "));
  }

  @NotNull
  private static String getMultiCalleeExpectedTypesRepresentation(@NotNull List<PyTypeCheckerInspection.AnalyzeArgumentResult> calleeResults,
                                                                  @NotNull TypeEvalContext context) {
    return calleeResults
      .stream()
      .map(argumentResult -> ObjectUtils.chooseNotNull(argumentResult.getExpectedTypeAfterSubstitution(), argumentResult.getExpectedType()))
      .map(type -> PythonDocumentationProvider.getTypeName(type, context))
      .collect(Collectors.joining(", ", "(", ")"));
  }
}
