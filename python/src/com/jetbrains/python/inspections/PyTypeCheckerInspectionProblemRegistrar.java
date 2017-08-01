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
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.util.XmlStringUtil;
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

      visitor.registerProblem(argumentResult.getArgument(),
                              getSingleCalleeProblemMessage(argumentResult, context),
                              getSingleCalleeHighlightType(argumentResult.getExpectedTypeAfterSubstitution()));
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
                              getMultiCalleeProblemMessage(argumentTypes, calleesResults, context),
                              getMultiCalleeHighlightType(calleesResults));
    }
  }

  @NotNull
  private static String getSingleCalleeProblemMessage(@NotNull PyTypeCheckerInspection.AnalyzeArgumentResult argumentResult,
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
        if (missingAttributes.size() == 1) {
          return String.format("Type '%s' doesn't have expected attribute '%s'", actualTypeName, missingAttributes.iterator().next());
        }
        else {
          return String.format("Type '%s' doesn't have expected attributes %s",
                               actualTypeName,
                               StringUtil.join(missingAttributes, s -> String.format("'%s'", s), ", "));
        }
      }
    }

    final String expectedTypeRepresentation = getSingleCalleeExpectedTypeRepresentation(expectedType,
                                                                                        argumentResult.getExpectedTypeAfterSubstitution(),
                                                                                        context);

    return String.format("Expected type %s, got '%s' instead", expectedTypeRepresentation, actualTypeName);
  }

  @NotNull
  private static ProblemHighlightType getSingleCalleeHighlightType(@Nullable PyType expectedTypeAfterSubstitution) {
    return expectedTypeAfterSubstitution == null ? ProblemHighlightType.GENERIC_ERROR_OR_WARNING : ProblemHighlightType.WEAK_WARNING;
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
      visitor.registerProblem(allCalleesAreRightOperators ? binaryExpression.getLeftExpression() : binaryExpression.getRightExpression(),
                              getMultiCalleeProblemMessage(argumentTypes, preferredOperatorsResults, context),
                              getMultiCalleeHighlightType(preferredOperatorsResults));
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
  private static String getMultiCalleeProblemMessage(@NotNull List<PyType> argumentTypes,
                                                     @NotNull List<PyTypeCheckerInspection.AnalyzeCalleeResults> calleesResults,
                                                     @NotNull TypeEvalContext context) {
    return XmlStringUtil.wrapInHtml("Unexpected type(s):<br>" +
                                    XmlStringUtil.escapeString(getMultiCalleeActualTypesRepresentation(argumentTypes, context)) + "<br>" +
                                    "Possible types:<br>" +
                                    XmlStringUtil.escapeString(getMultiCalleePossibleExpectedTypesRepresentation(calleesResults, context)));
  }

  /**
   * @param calleesResults results of analyzing arguments passed to callees
   * @return {@link ProblemHighlightType#WEAK_WARNING} if all expected types were substituted for all callees,
   * {@link ProblemHighlightType#GENERIC_ERROR_OR_WARNING} otherwise.
   */
  @NotNull
  private static ProblemHighlightType getMultiCalleeHighlightType(@NotNull List<PyTypeCheckerInspection.AnalyzeCalleeResults> calleesResults) {
    final boolean allExpectedTypesWereSubstituted = calleesResults
      .stream()
      .flatMap(calleeResults -> calleeResults.getResults().stream())
      .allMatch(argumentResult -> argumentResult.getExpectedTypeAfterSubstitution() != null);

    return allExpectedTypesWereSubstituted ? ProblemHighlightType.WEAK_WARNING : ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
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
  private static String getSingleCalleeExpectedTypeRepresentation(@NotNull PyType expectedType,
                                                                  @Nullable PyType expectedTypeAfterSubstitution,
                                                                  @NotNull TypeEvalContext context) {
    final String expectedTypeName = PythonDocumentationProvider.getTypeName(expectedType, context);

    return expectedTypeAfterSubstitution == null
           ? String.format("'%s'", expectedTypeName)
           : String.format("'%s' (matched generic type '%s')",
                           PythonDocumentationProvider.getTypeName(expectedTypeAfterSubstitution, context),
                           expectedTypeName);
  }

  @NotNull
  private static String getMultiCalleeActualTypesRepresentation(@NotNull List<PyType> argumentTypes, @NotNull TypeEvalContext context) {
    return argumentTypes
      .stream()
      .map(type -> PythonDocumentationProvider.getTypeName(type, context))
      .collect(Collectors.joining(", ", "(", ")"));
  }

  @NotNull
  private static String getMultiCalleePossibleExpectedTypesRepresentation(@NotNull List<PyTypeCheckerInspection.AnalyzeCalleeResults> calleesResults,
                                                                          @NotNull TypeEvalContext context) {
    return calleesResults
      .stream()
      .map(calleeResult -> getMultiCalleeExpectedTypesRepresentation(calleeResult.getResults(), context))
      .collect(Collectors.joining("<br>"));
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
