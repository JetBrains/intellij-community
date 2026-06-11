// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.google.common.collect.Lists;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonUiService;
import com.jetbrains.python.codeInsight.PyPsiIndexUtil;
import com.jetbrains.python.inspections.quickfix.PyRemoveArgumentQuickFix;
import com.jetbrains.python.inspections.quickfix.PyRenameArgumentQuickFix;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyKeywordArgument;
import com.jetbrains.python.psi.PyStarArgument;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyCallableType;
import com.jetbrains.python.psi.types.PyTupleType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class PyArgumentListInspection extends PyInspection {
  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                                 boolean isOnTheFly,
                                                 @NotNull LocalInspectionToolSession session) {
    TypeEvalContext context = PyInspectionVisitor.getContext(session);
    Visitor visitor = new Visitor(holder, context);
    visitor.downgradeHighlightForTypeEngine = context.getUsesExternalTypeEngine();
    return visitor;
  }

  private static class Visitor extends PyInspectionVisitor {

    Visitor(@NotNull ProblemsHolder holder,
            @NotNull TypeEvalContext context) {
      super(holder, context);
    }

    @Override
    protected @NotNull ProblemsHolder getHolder() {
      //noinspection ConstantConditions, see Visitor#Visitor(ProblemsHolder, LocalInspectionToolSession)
      return super.getHolder();
    }

    @Override
    public void visitPyArgumentList(final @NotNull PyArgumentList node) {
      ProblemHighlightType override = downgradeHighlightForTypeEngine ? ProblemHighlightType.INFORMATION : null;
      inspectPyArgumentList(node, getHolder(), getResolveContext(), override);
    }

    @Override
    public void visitPyStarArgument(@NotNull PyStarArgument node) {
      if (node.isKeyword()) return;
      ProblemHighlightType override = downgradeHighlightForTypeEngine ? ProblemHighlightType.INFORMATION : null;
      checkKnownSizeTupleSpreadInCall(node, getHolder(), getResolveContext(), override);
    }

    @Override
    public void visitPyDecorator(@NotNull PyDecorator deco) {
      if (deco.hasArgumentList()) return;
      final PyCallableType callableType = ContainerUtil.getFirstItem(deco.multiResolveCallee(getResolveContext()));
      if (callableType != null) {
        final PyCallable callable = callableType.getCallable();
        if (callable == null) return;
        final int firstParamOffset = callableType.getImplicitOffset();
        final List<PyCallableParameter> params = callableType.getParameters(myTypeEvalContext);
        if (params == null) return;

        final PyCallableParameter allegedFirstParam = ContainerUtil.getOrElse(params, firstParamOffset - 1, null);
        if (allegedFirstParam == null || allegedFirstParam.isKeywordContainer()) {
          // no parameters left to pass function implicitly, or wrong param type
          registerProblem(deco, PyPsiBundle.message("INSP.function.lacks.positional.argument",
                                                    callable.getName())); // TODO: better names for anon lambdas
        }
        else { // possible unfilled params
          for (int i = firstParamOffset; i < params.size(); i++) {
            final PyCallableParameter parameter = params.get(i);
            if (parameter.isKeywordOnlySeparator() || parameter.isPositionOnlySeparator()) {
              continue;
            }
            // param tuples, non-starred or non-default won't do
            if (!parameter.isKeywordContainer() && !parameter.isPositionalContainer() && !parameter.hasDefaultValue()) {
              final String parameterName = parameter.getName();
              registerProblem(deco, PyPsiBundle.message("INSP.parameter.unfilled", parameterName == null ? "(...)" : parameterName));
            }
          }
        }
      }
      // else: this case is handled by arglist visitor
    }
  }

  private static void inspectPyArgumentList(@NotNull PyArgumentList node,
                                            @NotNull ProblemsHolder holder,
                                            @NotNull PyResolveContext resolveContext,
                                            @Nullable ProblemHighlightType highlightOverride) {
    if (node.getParent() instanceof PyClass) return; // `(object)` in `class Foo(object)` is also an arg list
    final PyCallExpression call = node.getCallExpression();
    if (call == null) return;

    final TypeEvalContext context = resolveContext.getTypeEvalContext();
    final List<PyCallExpression.PyArgumentsMapping> mappings = call.multiMapArguments(resolveContext);

    for (PyCallExpression.PyArgumentsMapping mapping : mappings) {
      final PyCallableType callableType = mapping.getCallableType();
      if (callableType != null) {
        final PyCallable callable = callableType.getCallable();
        if (callable instanceof PyFunction function) {
          if (objectMethodCallViaSuper(call, function)) return;
        }
      }
    }

    if (!mappings.isEmpty()) {
      boolean specificMismatchKindReported = false;
      if (ContainerUtil.all(mappings, mapping -> !mapping.getUnmappedArguments().isEmpty())) {
        highlightUnexpectedArguments(node, holder, mappings, context, highlightOverride);
        specificMismatchKindReported = true;
      }
      if (ContainerUtil.all(mappings, mapping -> !mapping.getUnmappedParameters().isEmpty())) {
        highlightUnfilledParameters(node, holder, mappings, context, highlightOverride);
        specificMismatchKindReported = true;
      }
      if (!specificMismatchKindReported && ContainerUtil.all(mappings, mapping -> !mapping.isComplete())) {
        highlightIncorrectArguments(node, holder, mappings, context, highlightOverride);
      }
    }
  }

  private static void checkKnownSizeTupleSpreadInCall(@NotNull PyStarArgument node,
                                                      @NotNull ProblemsHolder holder,
                                                      @NotNull PyResolveContext resolveContext,
                                                      @Nullable ProblemHighlightType highlightOverride) {
    PyExpression expr = node.getExpression();
    if (expr == null) return;

    PyType type = resolveContext.getTypeEvalContext().getType(expr);
    if (!(type instanceof PyTupleType tupleType) || tupleType.isHomogeneous()) return;

    PsiElement parent = node.getParent();
    if (!(parent instanceof PyArgumentList argList)) return;
    if (!(argList.getParent() instanceof PyCallExpression callExpr)) return;

    int nonKeywordStarCount = 0;
    for (PyExpression arg : argList.getArguments()) {
      if (arg instanceof PyStarArgument sa && !sa.isKeyword()) nonKeywordStarCount++;
    }
    if (nonKeywordStarCount != 1) return;

    List<PyCallExpression.PyArgumentsMapping> mappings = callExpr.multiMapArguments(resolveContext);
    if (mappings.size() != 1) return;

    List<PyCallableParameter> variadicParams = mappings.get(0).getParametersMappedToVariadicPositionalArguments();
    if (variadicParams.isEmpty()) return;

    int positionalAfter = 0;
    boolean seenNode = false;
    for (PyExpression arg : argList.getArguments()) {
      if (arg == node) {
        seenNode = true;
        continue;
      }
      if (seenNode && !(arg instanceof PyKeywordArgument) && !(arg instanceof PyStarArgument)) {
        positionalAfter++;
      }
    }

    int filled = tupleType.getElementCount() + positionalAfter;
    if (filled >= variadicParams.size()) return;

    ASTNode argListNode = argList.getNode();
    if (argListNode == null) return;
    ASTNode rparNode = argListNode.findChildByType(PyTokenTypes.RPAR);
    if (rparNode == null) return;
    PsiElement rpar = rparNode.getPsi();

    for (int i = filled; i < variadicParams.size(); i++) {
      PyCallableParameter param = variadicParams.get(i);
      if (param.isPositionalContainer() || param.isKeywordContainer()) break;
      if (!param.hasDefaultValue()) {
        String name = param.getName();
        if (name != null) {
          registerProblem(holder, rpar, PyPsiBundle.message("INSP.parameter.unfilled", name), highlightOverride);
        }
      }
    }
  }

  private static void registerProblem(@NotNull ProblemsHolder holder,
                                      @NotNull PsiElement element,
                                      @NotNull @InspectionMessage String message,
                                      @Nullable ProblemHighlightType highlightOverride,
                                      @NotNull LocalQuickFix @NotNull ... fixes) {
    if (highlightOverride != null) {
      holder.registerProblem(element, message, highlightOverride, fixes);
    }
    else {
      holder.registerProblem(element, message, fixes);
    }
  }

  private static boolean objectMethodCallViaSuper(@NotNull PyCallExpression call, @NotNull PyFunction function) {
    /*
    Class could be designed to be used in cooperative multiple inheritance
    so `super()` could be resolved to some non-object class that is able to receive passed arguments.

    Example:

      class Shape(object):
        def __init__(self, shapename, **kwds):
            self.shapename = shapename
            # in case of ColoredShape the call below will be executed on Colored
            # so warning should not be raised
            super(Shape, self).__init__(**kwds)


      class Colored(object):
          def __init__(self, color, **kwds):
              self.color = color
              super(Colored, self).__init__(**kwds)


      class ColoredShape(Shape, Colored):
          pass
     */

    final PyClass receiverClass = function.getContainingClass();
    if (receiverClass != null && PyUtil.isObjectClass(receiverClass)) {
      final PyExpression receiverExpression = call.getReceiver(null);
      if (receiverExpression instanceof PyCallExpression && PyUtil.isSuperCall((PyCallExpression)receiverExpression)) {
        return true;
      }
    }

    return false;
  }

  private static Set<String> getDuplicateKeywordArguments(@NotNull PyArgumentList node) {
    final Set<String> keywordArgumentNames = new HashSet<>();
    final Set<String> results = new HashSet<>();
    for (PyExpression argument : node.getArguments()) {
      if (argument instanceof PyKeywordArgument) {
        final String keyword = ((PyKeywordArgument)argument).getKeyword();
        if (keywordArgumentNames.contains(keyword)) {
          results.add(keyword);
        }
        keywordArgumentNames.add(keyword);
      }
    }
    return results;
  }

  private static void highlightUnexpectedArguments(@NotNull PyArgumentList node,
                                                   @NotNull ProblemsHolder holder,
                                                   @NotNull List<PyCallExpression.PyArgumentsMapping> mappings,
                                                   @NotNull TypeEvalContext context,
                                                   @Nullable ProblemHighlightType highlightOverride) {
    if (mappings.size() == 1) {
      // if there is only one mapping, we could suggest quick fixes
      final Set<String> duplicateKeywords = getDuplicateKeywordArguments(node);

      final PyCallExpression.PyArgumentsMapping mapping = mappings.get(0);
      if (holder.isOnTheFly() && !mapping.getUnmappedArguments().isEmpty() && mapping.getUnmappedParameters().isEmpty()) {
        final PyCallableType callableType = mapping.getCallableType();
        if (callableType != null) {
          final PyCallable callable = callableType.getCallable();
          final Project project = node.getProject();
          if (callable instanceof PyFunction && !PyPsiIndexUtil.isNotUnderSourceRoot(project, callable.getContainingFile())) {
            final String message = PyPsiBundle.message("INSP.unexpected.arg(s)");
            registerProblem(holder, node, message,
                            highlightOverride != null ? highlightOverride : ProblemHighlightType.INFORMATION,
                            PythonUiService.getInstance().createPyChangeSignatureQuickFixForMismatchedCall(mapping));
          }
        }
      }


      for (PyExpression argument : mapping.getUnmappedArguments()) {
        final List<LocalQuickFix> quickFixes = Lists.newArrayList(new PyRemoveArgumentQuickFix());
        if (argument instanceof PyKeywordArgument) {
          if (duplicateKeywords.contains(((PyKeywordArgument)argument).getKeyword())) {
            continue;
          }
          quickFixes.add(new PyRenameArgumentQuickFix());
        }
        registerProblem(holder, argument,
                        PyPsiBundle.message("INSP.unexpected.arg"),
                        highlightOverride,
                        quickFixes.toArray(new LocalQuickFix[quickFixes.size() - 1]));
      }
    }
    else {
      // all mappings have unmapped arguments so we couldn't determine desired argument list and suggest appropriate quick fixes
      registerCallMismatchProblem(holder, node, node, mappings, context, highlightOverride);
    }
  }

  private static void highlightUnfilledParameters(@NotNull PyArgumentList node,
                                                  @NotNull ProblemsHolder holder,
                                                  @NotNull List<PyCallExpression.PyArgumentsMapping> mappings,
                                                  @NotNull TypeEvalContext context,
                                                  @Nullable ProblemHighlightType highlightOverride) {
    Optional
      .ofNullable(node.getNode())
      .map(astNode -> astNode.findChildByType(PyTokenTypes.RPAR))
      .map(ASTNode::getPsi)
      .ifPresent(
        psi -> {
          if (mappings.size() != 1 ||
              ContainerUtil.exists(mappings.get(0).getUnmappedParameters(), parameter -> parameter.getName() == null)) {
            registerCallMismatchProblem(holder, psi, node, mappings, context, highlightOverride);
          }
          else {
            StreamEx
              .of(mappings.get(0).getUnmappedParameters())
              .map(PyCallableParameter::getName)
              .filter(Objects::nonNull)
              .forEach(name -> registerProblem(holder, psi, PyPsiBundle.message("INSP.parameter.unfilled", name), highlightOverride));
          }
        }
      );
  }

  private static void highlightIncorrectArguments(@NotNull PyArgumentList node,
                                                  @NotNull ProblemsHolder holder,
                                                  @NotNull List<PyCallExpression.PyArgumentsMapping> mappings,
                                                  @NotNull TypeEvalContext context,
                                                  @Nullable ProblemHighlightType highlightOverride) {
    registerCallMismatchProblem(holder, node, node, mappings, context, highlightOverride);
  }

  /**
   * Reports a call that matches none of several candidate signatures, using the same model and messaging as
   * {@link PyTypeCheckerInspectionProblemRegistrar} via {@link PyMismatchTooltips}: a header naming the
   * common callee, an "Argument types" row built from the provided arguments (a keyword argument shows as
   * {@code keyword=type}; an argument that maps to no candidate stands out), and one "Expected one of" row
   * per candidate built from its parameters (an unfilled parameter stands out).
   *
   * @param element the element to highlight (the argument list, or its closing parenthesis for unfilled params)
   * @param node    the argument list whose arguments populate the "Argument types" row
   */
  private static void registerCallMismatchProblem(@NotNull ProblemsHolder holder,
                                                  @NotNull PsiElement element,
                                                  @NotNull PyArgumentList node,
                                                  @NotNull List<PyCallExpression.PyArgumentsMapping> mappings,
                                                  @NotNull TypeEvalContext context,
                                                  @Nullable ProblemHighlightType highlightOverride) {
    final List<PyCallable> callables = ContainerUtil.map(mappings, mapping -> {
      final PyCallableType callableType = mapping.getCallableType();
      return callableType == null ? null : callableType.getCallable();
    });

    final List<PyMismatchTooltips.Slot> argumentSlots = new ArrayList<>();
    for (PyExpression argument : node.getArguments()) {
      final boolean matched = ContainerUtil.exists(mappings, mapping -> !containsIdentity(mapping.getUnmappedArguments(), argument));
      argumentSlots.add(new PyMismatchTooltips.Slot(
        PyMismatchTooltips.actualArgumentText(argument, context.getType(argument), context), matched));
    }

    final List<List<PyMismatchTooltips.Slot>> expectedRows = new ArrayList<>();
    for (PyCallExpression.PyArgumentsMapping mapping : mappings) {
      final List<PyMismatchTooltips.Slot> row = new ArrayList<>();
      final PyCallableType callableType = mapping.getCallableType();
      final List<PyCallableParameter> parameters = callableType == null ? null : callableType.getParameters(context);
      if (parameters != null) {
        for (int i = callableType.getImplicitOffset(); i < parameters.size(); i++) {
          final PyCallableParameter parameter = parameters.get(i);
          if (parameter.isPositionOnlySeparator() || parameter.isKeywordOnlySeparator()) continue;
          final boolean matched = !containsIdentity(mapping.getUnmappedParameters(), parameter);
          row.add(new PyMismatchTooltips.Slot(PyMismatchTooltips.parameterText(parameter, context), matched));
        }
      }
      expectedRows.add(row);
    }

    final PyInspectionMessages.ProblemMessage header = PyMismatchTooltips.header(callables);
    final @InspectionMessage String description = PyMismatchTooltips.description(header, argumentSlots, expectedRows);
    final ProblemHighlightType type = highlightOverride != null ? highlightOverride : ProblemHighlightType.GENERIC_ERROR_OR_WARNING;

    if (holder.isOnTheFly()) {
      holder.problem(element, description).highlight(type)
        .tooltip(PyMismatchTooltips.tooltip(header, argumentSlots, expectedRows)).register();
    }
    else {
      holder.problem(element, description).highlight(type).register();
    }
  }

  private static boolean containsIdentity(@NotNull List<?> list, @NotNull Object element) {
    return ContainerUtil.exists(list, candidate -> candidate == element);
  }
}
