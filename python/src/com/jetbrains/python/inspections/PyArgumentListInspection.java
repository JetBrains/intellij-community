/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
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
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * Looks at argument lists.
 * @author dcheryasov
 */
public class PyArgumentListInspection extends PyInspection {
  @Nls
  @NotNull
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.incorrect.call.arguments");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly, @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, session);
  }

  public static class Visitor extends PyInspectionVisitor {

    public Visitor(final ProblemsHolder holder, LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitPyArgumentList(final PyArgumentList node) {
      // analyze
      inspectPyArgumentList(node, getHolder(), myTypeEvalContext);
    }

    @Override
    public void visitPyDecoratorList(final PyDecoratorList node) {
      PyDecorator[] decorators = node.getDecorators();
      for (PyDecorator deco : decorators) {
        if (deco.hasArgumentList()) continue;
        final PyCallExpression.PyMarkedCallee markedCallee = deco.resolveCallee(getResolveContext());
        if (markedCallee != null && !markedCallee.isImplicitlyResolved()) {
          final Callable callable = markedCallee.getCallable();
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

  public static void inspectPyArgumentList(PyArgumentList node, ProblemsHolder holder, final TypeEvalContext context, int implicitOffset) {
    if (node.getParent() instanceof PyClass) return; // class Foo(object) is also an arg list
    CallArgumentsMapping result = node.analyzeCall(PyResolveContext.noImplicits().withTypeEvalContext(context), implicitOffset);
    final PyCallExpression.PyMarkedCallee callee = result.getMarkedCallee();
    if (callee != null) {
      final Callable callable = callee.getCallable();
      // Decorate functions may have different parameter lists. We don't match arguments with parameters of decorators yet
      if (callable instanceof PyFunction && PyUtil.hasCustomDecorators((PyFunction)callable)) {
        return;
      }
    }
    highlightIncorrectArguments(holder, result, context);
    highlightMissingArguments(node, holder, result);
    highlightStarArgumentTypeMismatch(node, holder, context);
  }

  public static void inspectPyArgumentList(PyArgumentList node, ProblemsHolder holder, final TypeEvalContext context) {
    inspectPyArgumentList(node, holder, context, 0);
  }

  private static void highlightIncorrectArguments(ProblemsHolder holder, CallArgumentsMapping result, @NotNull TypeEvalContext context) {
    for (Map.Entry<PyExpression, EnumSet<CallArgumentsMapping.ArgFlag>> argEntry : result.getArgumentFlags().entrySet()) {
      EnumSet<CallArgumentsMapping.ArgFlag> flags = argEntry.getValue();
      if (!flags.isEmpty()) { // something's wrong
        PyExpression arg = argEntry.getKey();
        if (flags.contains(CallArgumentsMapping.ArgFlag.IS_DUP)) {
          holder.registerProblem(arg, PyBundle.message("INSP.duplicate.argument"), new PyRemoveArgumentQuickFix());
        }
        if (flags.contains(CallArgumentsMapping.ArgFlag.IS_DUP_KWD)) {
          holder.registerProblem(arg, PyBundle.message("INSP.duplicate.doublestar.arg"), new PyRemoveArgumentQuickFix());
        }
        if (flags.contains(CallArgumentsMapping.ArgFlag.IS_DUP_TUPLE)) {
          holder.registerProblem(arg, PyBundle.message("INSP.duplicate.star.arg"), new PyRemoveArgumentQuickFix());
        }
        if (flags.contains(CallArgumentsMapping.ArgFlag.IS_POS_PAST_KWD)) {
          holder.registerProblem(arg, PyBundle.message("INSP.cannot.appear.past.keyword.arg"), ProblemHighlightType.ERROR, new PyRemoveArgumentQuickFix());
        }
        if (flags.contains(CallArgumentsMapping.ArgFlag.IS_UNMAPPED)) {
          ArrayList<LocalQuickFix> quickFixes = Lists.<LocalQuickFix>newArrayList(new PyRemoveArgumentQuickFix());
          if (arg instanceof PyKeywordArgument) {
            quickFixes.add(new PyRenameArgumentQuickFix());
          }
          holder.registerProblem(arg, PyBundle.message("INSP.unexpected.arg"), quickFixes.toArray(new LocalQuickFix[quickFixes.size()-1]));
        }
        if (flags.contains(CallArgumentsMapping.ArgFlag.IS_TOO_LONG)) {
          final PyCallExpression.PyMarkedCallee markedCallee = result.getMarkedCallee();
          String parameterName = null;
          if (markedCallee != null) {
            final List<PyParameter> parameters = PyUtil.getParameters(markedCallee.getCallable(), context);
            for (int i = parameters.size() - 1; i >= 0; --i) {
              final PyParameter param = parameters.get(i);
              if (param instanceof PyNamedParameter) {
                final List<PyNamedParameter> unmappedParams = result.getUnmappedParams();
                if (!((PyNamedParameter)param).isPositionalContainer() && !((PyNamedParameter)param).isKeywordContainer() &&
                    param.getDefaultValue() == null && !unmappedParams.contains(param)) {
                  parameterName = param.getName();
                  break;
                }
              }
            }
            holder.registerProblem(arg, parameterName != null ? PyBundle.message("INSP.multiple.values.resolve.to.positional.$0", parameterName)
                                                              : PyBundle.message("INSP.more.args.that.pos.params"));
          }
        }
      }
    }
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

  private static void highlightMissingArguments(PyArgumentList node, ProblemsHolder holder, CallArgumentsMapping result) {
    ASTNode our_node = node.getNode();
    if (our_node != null) {
      ASTNode close_paren = our_node.findChildByType(PyTokenTypes.RPAR);
      if (close_paren != null) {
        for (PyNamedParameter param : result.getUnmappedParams()) {
          holder.registerProblem(close_paren.getPsi(), PyBundle.message("INSP.parameter.$0.unfilled", param.getName()));
        }
      }
    }
  }
}
