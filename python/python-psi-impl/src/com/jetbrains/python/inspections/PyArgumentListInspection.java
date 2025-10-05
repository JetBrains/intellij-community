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
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.util.XmlStringUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonUiService;
import com.jetbrains.python.codeInsight.PyPsiIndexUtil;
import com.jetbrains.python.inspections.quickfix.PyRemoveArgumentQuickFix;
import com.jetbrains.python.inspections.quickfix.PyRenameArgumentQuickFix;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.ParamHelper;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public final class PyArgumentListInspection extends PyInspection {

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly, @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, PyInspectionVisitor.getContext(session));
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
      inspectPyArgumentList(node, getHolder(), getResolveContext());
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
          registerProblem(deco, PyPsiBundle.message("INSP.function.lacks.positional.argument", callable.getName())); // TODO: better names for anon lambdas
        }
        else { // possible unfilled params
          for (int i = firstParamOffset; i < params.size(); i++) {
            final PyCallableParameter parameter = params.get(i);
            if (parameter.getParameter() instanceof PySingleStarParameter || parameter.getParameter() instanceof PySlashParameter) {
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
                                            @NotNull PyResolveContext resolveContext) {
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

          // Decorate functions may have different parameter lists. We don't match arguments with parameters of decorators yet
          if (PyKnownDecoratorUtil.hasUnknownOrChangingSignatureDecorator(function, context) ||
              decoratedClassInitCall(call.getCallee(), function, resolveContext)) {
            return;
          }

          if (objectMethodCallViaSuper(call, function)) return;
        }
      }
    }

    if (!mappings.isEmpty()) {
      boolean specificMismatchKindReported = false;
      if (ContainerUtil.all(mappings, mapping -> !mapping.getUnmappedArguments().isEmpty())) {
        highlightUnexpectedArguments(node, holder, mappings, context);
        specificMismatchKindReported = true;
      }
      if (ContainerUtil.all(mappings, mapping -> !mapping.getUnmappedParameters().isEmpty())) {
        highlightUnfilledParameters(node, holder, mappings, context);
        specificMismatchKindReported = true;
      }
      if (!specificMismatchKindReported && ContainerUtil.all(mappings, mapping -> !mapping.isComplete())) {
        highlightIncorrectArguments(node, holder, mappings, context);
      }
    }
    highlightStarArgumentTypeMismatch(node, holder, context);
  }

  private static boolean decoratedClassInitCall(@Nullable PyExpression callee,
                                                @NotNull PyFunction function,
                                                @NotNull PyResolveContext resolveContext) {
    if (callee instanceof PyReferenceExpression && PyUtil.isInitMethod(function)) {
      final PsiPolyVariantReference classReference = ((PyReferenceExpression)callee).getReference(resolveContext);

      return Arrays
        .stream(classReference.multiResolve(false))
        .map(ResolveResult::getElement)
        .anyMatch(
          element ->
            element instanceof PyClass &&
            PyKnownDecoratorUtil.hasUnknownOrChangingReturnTypeDecorator((PyClass)element, resolveContext.getTypeEvalContext())
        );
    }

    return false;
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

  private static void highlightStarArgumentTypeMismatch(PyArgumentList node, ProblemsHolder holder, TypeEvalContext context) {
    for (PyExpression arg : node.getArguments()) {
      if (!(arg instanceof PyStarArgument starArgument)) {
        continue;
      }
      PyExpression content = PyUtil.peelArgument(PsiTreeUtil.findChildOfType(arg, PyExpression.class));
      if (content == null) {
        continue;
      }
      PyType argType = context.getType(content);
      if (argType != null && !PyTypeChecker.isUnknown(argType, context)) {
        if (starArgument.isKeyword()) {
          if (!PyABCUtil.isSubtype(argType, PyNames.MAPPING, context)) {
            // TODO: check that the key type is compatible with `str`
            holder.registerProblem(arg, PyPsiBundle.message("INSP.expected.dict.got.type", argType.getName()));
          }
        }
        else {
          // *
          if (!PyABCUtil.isSubtype(argType, PyNames.ITERABLE, context)) {
            holder.registerProblem(arg, PyPsiBundle.message("INSP.expected.iterable.got.type", argType.getName()));
          }
        }
      }
    }
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
                                                   @NotNull TypeEvalContext context) {
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
            holder.registerProblem(node, message, ProblemHighlightType.INFORMATION, PythonUiService.getInstance().createPyChangeSignatureQuickFixForMismatchedCall(mapping));
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
        holder.registerProblem(argument,
                               PyPsiBundle.message("INSP.unexpected.arg"),
                               quickFixes.toArray(new LocalQuickFix[quickFixes.size() - 1]));
      }
    }
    else {
      // all mappings have unmapped arguments so we couldn't determine desired argument list and suggest appropriate quick fixes
      holder.registerProblem(
        node,
        addPossibleCalleesRepresentation(PyPsiBundle.message("INSP.unexpected.arg(s)"), mappings, context, holder.isOnTheFly())
      );
    }
  }

  private static void highlightUnfilledParameters(@NotNull PyArgumentList node,
                                                  @NotNull ProblemsHolder holder,
                                                  @NotNull List<PyCallExpression.PyArgumentsMapping> mappings,
                                                  @NotNull TypeEvalContext context) {
    Optional
      .ofNullable(node.getNode())
      .map(astNode -> astNode.findChildByType(PyTokenTypes.RPAR))
      .map(ASTNode::getPsi)
      .ifPresent(
        psi -> {
          if (mappings.size() != 1 ||
              ContainerUtil.exists(mappings.get(0).getUnmappedParameters(), parameter -> parameter.getName() == null)) {
            holder.registerProblem(
              psi,
              addPossibleCalleesRepresentation(PyPsiBundle.message("INSP.parameter(s).unfilled"), mappings, context, holder.isOnTheFly())
            );
          }
          else {
            StreamEx
              .of(mappings.get(0).getUnmappedParameters())
              .map(PyCallableParameter::getName)
              .filter(Objects::nonNull)
              .forEach(name -> holder.registerProblem(psi, PyPsiBundle.message("INSP.parameter.unfilled", name)));
          }
        }
      );
  }

  private static void highlightIncorrectArguments(@NotNull PyArgumentList node,
                                                  @NotNull ProblemsHolder holder,
                                                  @NotNull List<PyCallExpression.PyArgumentsMapping> mappings,
                                                  @NotNull TypeEvalContext context) {
    holder.registerProblem(
      node,
      addPossibleCalleesRepresentation(PyPsiBundle.message("INSP.incorrect.arguments"), mappings, context, holder.isOnTheFly())
    );
  }

  private static @NlsSafe @NotNull String addPossibleCalleesRepresentation(@NotNull @InspectionMessage String prefix,
                                                                           @NotNull List<PyCallExpression.PyArgumentsMapping> mappings,
                                                                           @NotNull TypeEvalContext context,
                                                                           boolean isOnTheFly) {
    final @NlsSafe String separator = isOnTheFly ? "<br>" : " ";
    final @NlsSafe String possibleCalleesRepresentation = calculatePossibleCalleesRepresentation(mappings, context, isOnTheFly);

    if (isOnTheFly) {
      return XmlStringUtil.wrapInHtml(
        prefix + separator +
        PyPsiBundle.message("INSP.possible.callees") + ":" + separator +
        possibleCalleesRepresentation
      );
    }
    else {
      return prefix + "." + separator +
             PyPsiBundle.message("INSP.possible.callees") + ":" + separator +
             possibleCalleesRepresentation;
    }
  }


  private static @NotNull @NlsSafe String calculatePossibleCalleesRepresentation(@NotNull List<PyCallExpression.PyArgumentsMapping> mappings,
                                                                                 @NotNull TypeEvalContext context,
                                                                                 boolean isOnTheFly) {
    return StreamEx
      .of(mappings)
      .map(PyCallExpression.PyArgumentsMapping::getCallableType)
      .nonNull()
      .map(callableType -> XmlStringUtil.escapeString(calculatePossibleCalleeRepresentation(callableType, context)))
      .nonNull()
      .collect(Collectors.joining(isOnTheFly ? "<br>" : " "));
  }

  private static @Nullable @NlsSafe String calculatePossibleCalleeRepresentation(@NotNull PyCallableType callableType,
                                                                                 @NotNull TypeEvalContext context) {
    final String name = callableType.getCallable() != null ? callableType.getCallable().getName() : "";
    final List<PyCallableParameter> callableParameters = callableType.getParameters(context);
    if (callableParameters == null) return null;

    final String parameters = ParamHelper.getPresentableText(callableParameters, true, context);
    final String callableNameAndParameters = name + parameters;

    return Optional
      .ofNullable(PyUtil.as(callableType.getCallable(), PyFunction.class))
      .map(PyFunction::getContainingClass)
      .map(PyClass::getName)
      .map(className -> PyNames.INIT.equals(name) ? className + parameters : className + "." + callableNameAndParameters)
      .orElse(callableNameAndParameters);
  }
}
