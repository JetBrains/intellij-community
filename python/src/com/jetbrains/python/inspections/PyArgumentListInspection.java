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

import com.google.common.collect.Lists;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.xml.util.XmlStringUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.inspections.quickfix.PyRemoveArgumentQuickFix;
import com.jetbrains.python.inspections.quickfix.PyRenameArgumentQuickFix;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyABCUtil;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeChecker;
import com.jetbrains.python.psi.types.TypeEvalContext;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public class PyArgumentListInspection extends PyInspection {
  @Nls
  @NotNull
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.incorrect.call.arguments");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly, @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, session);
  }

  private static class Visitor extends PyInspectionVisitor {

    public Visitor(@NotNull ProblemsHolder holder, @NotNull LocalInspectionToolSession session) {
      super(holder, session);
    }

    @NotNull
    @Override
    protected ProblemsHolder getHolder() {
      //noinspection ConstantConditions, see Visitor#Visitor(ProblemsHolder, LocalInspectionToolSession)
      return super.getHolder();
    }

    @Override
    public void visitPyArgumentList(final PyArgumentList node) {
      inspectPyArgumentList(node, getHolder(), myTypeEvalContext);
    }

    @Override
    public void visitPyDecoratorList(final PyDecoratorList node) {
      PyDecorator[] decorators = node.getDecorators();
      for (PyDecorator deco : decorators) {
        if (deco.hasArgumentList()) continue;
        final PyCallExpression.PyMarkedCallee markedCallee = deco.resolveCallee(getResolveContext());
        if (markedCallee != null && !markedCallee.isImplicitlyResolved()) {
          final PyCallable callable = markedCallee.getCallable();
          int firstParamOffset =  markedCallee.getImplicitOffset();
          final List<PyParameter> params = PyUtil.getParameters(callable, myTypeEvalContext);
          final PyNamedParameter allegedFirstParam = params.size() < firstParamOffset ?
                                                       null : params.get(firstParamOffset-1).getAsNamed();
          if (allegedFirstParam == null || allegedFirstParam.isKeywordContainer()) {
            // no parameters left to pass function implicitly, or wrong param type
            registerProblem(deco, PyBundle.message("INSP.func.$0.lacks.first.arg", callable.getName())); // TODO: better names for anon lambdas
          }
          else { // possible unfilled params
            for (int i = firstParamOffset; i < params.size(); i += 1) {
              final PyParameter parameter = params.get(i);
              if (parameter instanceof PySingleStarParameter) continue;
              final PyNamedParameter par = parameter.getAsNamed();
              // param tuples, non-starred or non-default won't do
              if (par == null || (!par.isKeywordContainer() && !par.isPositionalContainer() &&!par.hasDefaultValue())) {
                String parameterName = par != null ? par.getName() : "(...)";
                registerProblem(deco, PyBundle.message("INSP.parameter.$0.unfilled", parameterName));
              }
            }
          }
        }
        // else: this case is handled by arglist visitor
      }
    }

  }

  public static void inspectPyArgumentList(@NotNull PyArgumentList node,
                                           @NotNull ProblemsHolder holder,
                                           @NotNull TypeEvalContext context,
                                           int implicitOffset) {
    if (node.getParent() instanceof PyClass) return; // `(object)` in `class Foo(object)` is also an arg list
    final PyCallExpression call = node.getCallExpression();
    if (call == null) return;

    final PyResolveContext resolveContext = PyResolveContext.noImplicits().withTypeEvalContext(context);
    final List<PyCallExpression.PyArgumentsMapping> mappings = call.multiMapArguments(resolveContext, implicitOffset);

    for (PyCallExpression.PyArgumentsMapping mapping : mappings) {
      final PyCallExpression.PyMarkedCallee callee = mapping.getMarkedCallee();
      if (callee != null) {
        final PyCallable callable = callee.getCallable();
        if (callable instanceof PyFunction) {
          final PyFunction function = (PyFunction)callable;

          // Decorate functions may have different parameter lists. We don't match arguments with parameters of decorators yet
          if (PyUtil.hasCustomDecorators(function) || decoratedClassInitCall(call.getCallee(), function)) {
            return;
          }
        }
      }
    }

    highlightUnexpectedArguments(node, holder, mappings, context);
    highlightUnfilledParameters(node, holder, mappings, context);
    highlightStarArgumentTypeMismatch(node, holder, context);
  }

  public static void inspectPyArgumentList(@NotNull PyArgumentList node, @NotNull ProblemsHolder holder, @NotNull TypeEvalContext context) {
    inspectPyArgumentList(node, holder, context, 0);
  }

  private static boolean decoratedClassInitCall(@Nullable PyExpression callee, @NotNull PyFunction function) {
    if (callee instanceof PyReferenceExpression && PyUtil.isInit(function)) {
      final PsiPolyVariantReference classReference = ((PyReferenceExpression)callee).getReference();

      return Arrays
        .stream(classReference.multiResolve(false))
        .map(ResolveResult::getElement)
        .anyMatch(element -> element instanceof PyClass && PyUtil.hasCustomDecorators((PyClass)element));
    }

    return false;
  }

  private static void highlightStarArgumentTypeMismatch(PyArgumentList node, ProblemsHolder holder, TypeEvalContext context) {
    for (PyExpression arg : node.getArguments()) {
      if (arg instanceof PyStarArgument) {
        PyExpression content = PyUtil.peelArgument(PsiTreeUtil.findChildOfType(arg, PyExpression.class));
        if (content != null) {
          PyType inside_type = context.getType(content);
          if (inside_type != null && !PyTypeChecker.isUnknown(inside_type)) {
            if (((PyStarArgument)arg).isKeyword()) {
              if (!PyABCUtil.isSubtype(inside_type, PyNames.MAPPING, context)) {
                holder.registerProblem(arg, PyBundle.message("INSP.expected.dict.got.$0", inside_type.getName()));
              }
            }
            else { // * arg
              if (!PyABCUtil.isSubtype(inside_type, PyNames.ITERABLE, context)) {
                holder.registerProblem(arg, PyBundle.message("INSP.expected.iter.got.$0", inside_type.getName()));
              }
            }
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
    if (mappings.isEmpty() || mappings.stream().anyMatch(mapping -> mapping.getUnmappedArguments().isEmpty())) return;

    if (mappings.size() == 1) {
      // if there is only one mapping, we could suggest quick fixes
      final Set<String> duplicateKeywords = getDuplicateKeywordArguments(node);

      for (PyExpression argument : mappings.get(0).getUnmappedArguments()) {
        final List<LocalQuickFix> quickFixes = Lists.newArrayList(new PyRemoveArgumentQuickFix());
        if (argument instanceof PyKeywordArgument) {
          if (duplicateKeywords.contains(((PyKeywordArgument)argument).getKeyword())) {
            continue;
          }
          quickFixes.add(new PyRenameArgumentQuickFix());
        }
        holder.registerProblem(argument,
                               PyBundle.message("INSP.unexpected.arg"),
                               quickFixes.toArray(new LocalQuickFix[quickFixes.size() - 1]));
      }
    }
    else {
      // all mappings have unmapped arguments so we couldn't determine desired argument list and suggest appropriate quick fixes
      holder.registerProblem(node, addPossibleCalleesRepresentationAndWrapInHtml(PyBundle.message("INSP.unexpected.arg(s)"), mappings, context));
    }
  }

  private static void highlightUnfilledParameters(@NotNull PyArgumentList node,
                                                  @NotNull ProblemsHolder holder,
                                                  @NotNull List<PyCallExpression.PyArgumentsMapping> mappings,
                                                  @NotNull TypeEvalContext context) {
    if (mappings.isEmpty() || mappings.stream().anyMatch(mapping -> mapping.getUnmappedParameters().isEmpty())) return;

    Optional
      .ofNullable(node.getNode())
      .map(astNode -> astNode.findChildByType(PyTokenTypes.RPAR))
      .map(ASTNode::getPsi)
      .ifPresent(
        psi -> {
          if (mappings.size() == 1) {
            StreamEx
              .of(mappings.get(0).getUnmappedParameters())
              .map(PyParameter::getName)
              .filter(Objects::nonNull)
              .forEach(name -> holder.registerProblem(psi, PyBundle.message("INSP.parameter.$0.unfilled", name)));
          }
          else {
            holder.registerProblem(psi,
                                   addPossibleCalleesRepresentationAndWrapInHtml(PyBundle.message("INSP.parameter(s).unfilled"), mappings, context));
          }
        }
      );
  }

  @NotNull
  private static String addPossibleCalleesRepresentationAndWrapInHtml(@NotNull String prefix,
                                                                      @NotNull List<PyCallExpression.PyArgumentsMapping> mappings,
                                                                      @NotNull TypeEvalContext context) {
    final String possibleCalleesRepresentation = calculatePossibleCalleesRepresentation(mappings, context);
    return XmlStringUtil.wrapInHtml(prefix + "<br>" + PyBundle.message("INSP.possible.callees") + ":<br>" + possibleCalleesRepresentation);
  }

  @NotNull
  private static String calculatePossibleCalleesRepresentation(@NotNull List<PyCallExpression.PyArgumentsMapping> mappings,
                                                               @NotNull TypeEvalContext context) {
    return StreamEx
      .of(mappings)
      .map(PyCallExpression.PyArgumentsMapping::getMarkedCallee)
      .nonNull()
      .map(markedCallee -> calculatePossibleCalleeRepresentation(markedCallee.getCallable(), context))
      .nonNull()
      .collect(Collectors.joining("<br>"));
  }

  @Nullable
  private static String calculatePossibleCalleeRepresentation(@NotNull PyCallable callable, @NotNull TypeEvalContext context) {
    final String callableNameAndParameters = callable.getName() + callable.getParameterList().getPresentableText(true, context);

    return Optional
      .ofNullable(PyUtil.as(callable, PyFunction.class))
      .map(PyFunction::getContainingClass)
      .map(PyClass::getName)
      .map(className -> className + "." + callableNameAndParameters)
      .orElse(callableNameAndParameters);
  }
}
